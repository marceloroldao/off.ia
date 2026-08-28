from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class BDRCapability:
    available: bool
    backend: str
    reason: str | None = None


def detect_bdr_v11_memoria_backend() -> BDRCapability:
    """Detect the experimental Memoria.ia BDR v1.1 native adapter if installed.

    This intentionally does not implement storage in OFF.IA. Persistence remains
    a Resolutive-DB/Memoria.ia responsibility.
    """
    try:
        from memoria_resolutiva.bdr_store import native_bdr_available  # type: ignore
    except (ImportError, ModuleNotFoundError) as exc:
        return BDRCapability(False, "bdr-v1.1", f"Memoria.ia BDR adapter unavailable: {exc}")
    if not native_bdr_available():
        return BDRCapability(False, "bdr-v1.1", "Memoria.ia native BDR extension is not available")
    return BDRCapability(True, "bdr-v1.1")
