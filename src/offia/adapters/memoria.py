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
    """OFF.IA boundary; implementation must delegate semantics to Memoria.ia."""

    def resolve(self, message: str) -> ResolvedContext: ...
    def learn(self, text: str) -> Sequence[str]: ...
    def flush(self) -> None: ...
