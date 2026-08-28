from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Sequence
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


class LlamaCppError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class LlamaUsage:
    input_tokens: int | None = None
    output_tokens: int | None = None
    estimated_cost_usd: float | None = 0.0


@dataclass(frozen=True, slots=True)
class LlamaResponse:
    text: str
    provider: str
    model: str
    usage: LlamaUsage


class LlamaCppAdapter:
    """Minimal adapter for a llama.cpp OpenAI-compatible local server.

    The response surface mirrors the fields used by Memoria.ia's provider-neutral
    LLM boundary without importing or duplicating Memoria.ia internals. The
    endpoint is loopback-only by default.
    """

    provider_name = "local"

    def __init__(self, base_url: str = "http://127.0.0.1:8080", *, model: str = "local-gguf", timeout_s: float = 120.0, allow_non_loopback: bool = False):
        self.base_url = base_url.rstrip("/")
        self.model_name = model
        self.timeout_s = timeout_s
        host = (urlparse(self.base_url).hostname or "").lower()
        if not allow_non_loopback and host not in {"127.0.0.1", "localhost", "::1"}:
            raise ValueError("llama.cpp endpoint must be loopback unless explicitly overridden")

    @staticmethod
    def _messages(message: str, context: Sequence[str]) -> list[dict[str, str]]:
        messages: list[dict[str, str]] = []
        if context:
            selected = "\n".join(str(item) for item in context)
            messages.append({"role": "system", "content": "Use the following Memoria.ia-selected context when relevant. Do not invent facts absent from it.\n\n" + selected})
        messages.append({"role": "user", "content": message})
        return messages

    def generate(self, *, message: str, context: Sequence[str]) -> LlamaResponse:
        body = {"model": self.model_name, "messages": self._messages(message, context), "stream": False}
        request = Request(self.base_url + "/v1/chat/completions", data=json.dumps(body).encode("utf-8"), headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self.timeout_s) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
            raise LlamaCppError(f"local llama.cpp inference failed: {exc}") from exc
        try:
            text = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise LlamaCppError("llama.cpp returned an invalid chat-completions payload") from exc
        usage = payload.get("usage") or {}
        return LlamaResponse(text=str(text), provider=self.provider_name, model=str(payload.get("model") or self.model_name), usage=LlamaUsage(input_tokens=usage.get("prompt_tokens"), output_tokens=usage.get("completion_tokens"), estimated_cost_usd=0.0))
