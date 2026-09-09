from __future__ import annotations

from dataclasses import dataclass
from time import perf_counter
from typing import Literal, Protocol, Sequence

from .adapters.memoria import LearningDecisionRequest, MemoriaBoundary, ResolvedContext

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
    learned_memory_ids: tuple[str, ...]
    validated_model_evidence_ids: tuple[str, ...]
    retrieved_context_chars: int
    context_sent_chars: int
    input_tokens: int | None
    output_tokens: int | None
    memory_latency_ms: float
    memory_write_latency_ms: float
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

    Memoria mode accepts context only from Memoria.ia. When Memoria.ia supplies
    a serialized CognitivePacket, OFF.IA forwards that opaque packet to the
    language adapter instead of the legacy text items. OFF.IA does not parse or
    reinterpret the packet.

    The trusted write path records the user's input only. Assistant/model output
    is deliberately not written back as factual memory here. Structured claims,
    when explicitly supplied by a language adapter, may only be sent to
    Memoria.ia's ResponseValidator path. Free-form response text without claims
    creates no model evidence automatically.

    Learning is a separate explicit operation. OFF.IA can forward a trusted
    USER_CONFIRMED or SENSOR_OBSERVED decision to Memoria.ia's Learning Gate, but
    it never upgrades a model claim by itself.

    New Memoria adapters should implement ``learn_user``. During migration, the
    legacy ``learn`` method may be used only as a compatibility fallback and is
    called with the user message alone.

    Baseline mode intentionally bypasses both Memoria retrieval and learning so
    benchmark runs are not contaminated by state changes.
    """

    def __init__(self, memoria: MemoriaBoundary, language: LanguageAdapter):
        self.memoria = memoria
        self.language = language

    def _learn_user(self, message: str):
        learn_user = getattr(self.memoria, "learn_user", None)
        if callable(learn_user):
            return learn_user(message)
        legacy_learn = getattr(self.memoria, "learn", None)
        if callable(legacy_learn):
            return legacy_learn(message)
        raise TypeError("Memoria adapter must implement learn_user() or legacy learn()")

    def _validate_model_response(self, *, response_id: str | None, response) -> tuple[str, ...]:
        claims = tuple(getattr(response, "claims", ()) or ())
        if not claims:
            return ()
        if response_id is None or not response_id.strip():
            raise ValueError("response_id is required when structured model claims are present")
        validator = getattr(self.memoria, "validate_model_response", None)
        if not callable(validator):
            raise TypeError("Memoria adapter must implement validate_model_response() for structured claims")
        evidence_ids = validator(
            response_id=response_id,
            response_text=response.text,
            claims=claims,
        )
        return tuple(str(item) for item in evidence_ids)

    def apply_learning_decision(self, request: LearningDecisionRequest) -> str | None:
        """Forward one explicit trusted decision to Memoria.ia's Learning Gate."""
        apply_decision = getattr(self.memoria, "apply_learning_decision", None)
        if not callable(apply_decision):
            raise TypeError("Memoria adapter must implement apply_learning_decision()")
        promoted_evidence_id = apply_decision(request)
        self.memoria.flush()
        return None if promoted_evidence_id is None else str(promoted_evidence_id)

    def chat(
        self,
        message: str,
        *,
        mode: ChatMode = "memoria",
        baseline_context: Sequence[str] = (),
        response_id: str | None = None,
    ) -> TurnResult:
        if mode not in {"baseline", "memoria"}:
            raise ValueError("mode must be 'baseline' or 'memoria'")

        total_start = perf_counter()
        memory_ms = 0.0
        memory_write_ms = 0.0
        learned_memory_ids: tuple[str, ...] = ()
        validated_model_evidence_ids: tuple[str, ...] = ()

        if mode == "memoria":
            memory_start = perf_counter()
            resolved: ResolvedContext = self.memoria.resolve(message)
            memory_ms = (perf_counter() - memory_start) * 1000.0
            context = resolved.language_context()
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

        if mode == "memoria":
            validated_model_evidence_ids = self._validate_model_response(
                response_id=response_id,
                response=response,
            )
            write_start = perf_counter()
            learned = self._learn_user(message)
            self.memoria.flush()
            memory_write_ms = (perf_counter() - write_start) * 1000.0
            learned_memory_ids = tuple(str(item) for item in learned)

        total_ms = (perf_counter() - total_start) * 1000.0
        usage = getattr(response, "usage", None)

        return TurnResult(
            text=response.text,
            context=tuple(context),
            metrics=TurnMetrics(
                mode=mode,
                memory_hit=hit,
                memory_miss=(mode == "memoria" and not hit),
                retrieved_memory_ids=memory_ids,
                learned_memory_ids=learned_memory_ids,
                validated_model_evidence_ids=validated_model_evidence_ids,
                retrieved_context_chars=retrieved_chars,
                context_sent_chars=len("\n".join(context)),
                input_tokens=getattr(usage, "input_tokens", None),
                output_tokens=getattr(usage, "output_tokens", None),
                memory_latency_ms=memory_ms,
                memory_write_latency_ms=memory_write_ms,
                llm_latency_ms=llm_ms,
                total_latency_ms=total_ms,
                model=response.model,
                provider=getattr(response, "provider", self.language.provider_name),
            ),
        )
