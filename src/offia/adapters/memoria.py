from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, Sequence


@dataclass(frozen=True, slots=True)
class ResolvedContext:
    items: tuple[str, ...]
    memory_ids: tuple[str, ...] = ()
    hit: bool = False
    unresolved: bool = False


class MemoriaBoundary(Protocol):
    """OFF.IA boundary; implementation must delegate semantics to Memoria.ia.

    Freeze-candidate rule: user/sensor input may enter the trusted memory path,
    but assistant/model output must not be written back as factual memory by this
    boundary. New adapters should implement ``learn_user`` explicitly. ``learn``
    remains documented as a legacy compatibility surface only.
    """

    def resolve(self, message: str) -> ResolvedContext: ...
    def learn_user(self, text: str) -> Sequence[str]: ...
    def learn(self, text: str) -> Sequence[str]: ...
    def flush(self) -> None: ...
