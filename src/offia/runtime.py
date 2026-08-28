from __future__ import annotations

from dataclasses import dataclass
from time import perf_counter
from typing import Literal, Protocol, Sequence

from .adapters.memoria import MemoriaBoundary, ResolvedContext

ChatMode = Literal["baseline", "memoria"]

class LanguageAdapter(Protocol):
    provider_name: str
    model_name: str
    def generate(self, *, message: str, context: Sequence[str]): ...

@dataclass(frozen=True, slots=True)
class TurnMetrics:
    mode: ChatMode
    memory_hit: bool
    memory_miss: bool
    retrieved_memory_ids: tuple[str, ...]
    retrieved_context_chars: int
    context_sent_chars: int
    input_tokens: int | None
    output_tokens: int | None
    memory_latency_ms: float
    llm_latency_ms: float
    total_latency_ms: float
    model: str
    provider: str = "local"

@dataclass(frozen=True, slots=True)
class TurnResult:
    text: str
    context: tuple[str, ...]
    metrics: TurnMetrics

class OfflineRuntime:
    """Thin OFF.IA orchestration boundary.

    Memoria mode accepts context only from Memoria.ia. Baseline mode forwards
    caller-provided history/context directly to the same language adapter for
    controlled comparison.
    """
    def __init__(self, memoria: MemoriaBoundary, language: LanguageAdapter):
        self.memoria = memoria
        self.language = language

    def chat(self, message: str, *, mode: ChatMode = "memoria", baseline_context: Sequence[str] = ()) -> TurnResult:
        if mode not in {"baseline", "memoria"}:
            raise ValueError("mode must be 'baseline' or 'memoria'")
        total_start = perf_counter()
        memory_ms = 0.0
        if mode == "memoria":
            memory_start = perf_counter()
            resolved: ResolvedContext = self.memoria.resolve(message)
            memory_ms = (perf_counter() - memory_start) * 1000.0
            context = resolved.items
            memory_ids = resolved.memory_ids
            hit = resolved.hit
            retrieved_chars = sum(len(x) for x in resolved.items)
        else:
            context = tuple(str(item) for item in baseline_context)
            memory_ids = ()
            hit = False
            retrieved_chars = 0
        llm_start = perf_counter()
        response = self.language.generate(message=message, context=context)
        llm_ms = (perf_counter() - llm_start) * 1000.0
        total_ms = (perf_counter() - total_start) * 1000.0
        usage = getattr(response, "usage", None)
        return TurnResult(text=response.text, context=tuple(context), metrics=TurnMetrics(mode=mode, memory_hit=hit, memory_miss=(mode == "memoria" and not hit), retrieved_memory_ids=memory_ids, retrieved_context_chars=retrieved_chars, context_sent_chars=len("\n".join(context)), input_tokens=getattr(usage, "input_tokens", None), output_tokens=getattr(usage, "output_tokens", None), memory_latency_ms=memory_ms, llm_latency_ms=llm_ms, total_latency_ms=total_ms, model=response.model, provider=getattr(response, "provider", self.language.provider_name)))
