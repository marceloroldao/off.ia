from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, Sequence


@dataclass(frozen=True, slots=True)
class CognitivePacketEnvelope:
    """Transport-only view of a Memoria.ia CognitivePacket.

    OFF.IA must not interpret or mutate cognitive semantics. It forwards the
    serialized packet produced by Memoria.ia to the language boundary and keeps
    the schema version visible for diagnostics/compatibility checks.
    """

    payload_json: str
    schema_version: int = 1

    def __post_init__(self) -> None:
        if not self.payload_json.strip():
            raise ValueError("payload_json must be non-empty")
        if self.schema_version < 1:
            raise ValueError("schema_version must be >= 1")


@dataclass(frozen=True, slots=True)
class ModelClaim:
    """Structured model claim transport.

    This is not a factual memory row. OFF.IA may forward claims to Memoria.ia's
    ResponseValidator, where they remain LLM_GENERATED unless a separate trusted
    Learning Gate decision creates new authoritative evidence.
    """

    subject: str
    predicate: str
    object: str
    confidence: float = 1.0

    def __post_init__(self) -> None:
        if not self.subject.strip():
            raise ValueError("subject must be non-empty")
        if not self.predicate.strip():
            raise ValueError("predicate must be non-empty")
        if not self.object.strip():
            raise ValueError("object must be non-empty")
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("confidence must be in [0, 1]")


@dataclass(frozen=True, slots=True)
class ResolvedContext:
    items: tuple[str, ...]
    memory_ids: tuple[str, ...] = ()
    hit: bool = False
    unresolved: bool = False
    cognitive_packet: CognitivePacketEnvelope | None = None

    def language_context(self) -> tuple[str, ...]:
        """Return the context that may cross the language-model boundary.

        A structured CognitivePacket takes precedence over legacy text items.
        OFF.IA treats it as opaque serialized data; legacy text remains the
        compatibility path for adapters that have not adopted the freeze
        candidate contract yet.
        """
        if self.cognitive_packet is not None:
            return (self.cognitive_packet.payload_json,)
        return self.items


class MemoriaBoundary(Protocol):
    """OFF.IA boundary; implementation must delegate semantics to Memoria.ia.

    Freeze-candidate rules:
    - Memoria.ia owns cognitive/temporal semantics and packet compilation.
    - OFF.IA may transport a serialized CognitivePacket but must not reinterpret it.
    - user/sensor input may enter the trusted memory path;
    - assistant/model output must not be written back as factual memory here;
    - structured model claims may only enter the ResponseValidator path and remain
      non-authoritative LLM_GENERATED evidence until an explicit trusted decision.

    New adapters should implement ``learn_user`` explicitly. ``learn`` remains a
    legacy compatibility surface only.
    """

    def resolve(self, message: str) -> ResolvedContext: ...
    def learn_user(self, text: str) -> Sequence[str]: ...
    def learn(self, text: str) -> Sequence[str]: ...
    def validate_model_response(
        self,
        *,
        response_id: str,
        response_text: str,
        claims: Sequence[ModelClaim],
    ) -> Sequence[str]: ...
    def flush(self) -> None: ...
