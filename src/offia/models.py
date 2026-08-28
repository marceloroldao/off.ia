from __future__ import annotations

from dataclasses import dataclass, asdict
from enum import StrEnum
from hashlib import sha256
import json
from pathlib import Path
from typing import Iterable


class ModelStatus(StrEnum):
    MODEL_AVAILABLE = "MODEL_AVAILABLE"
    MODEL_MISSING = "MODEL_MISSING"
    MODEL_INVALID = "MODEL_INVALID"
    MODEL_LOADING = "MODEL_LOADING"
    MODEL_READY = "MODEL_READY"
    MODEL_ERROR = "MODEL_ERROR"


@dataclass(frozen=True, slots=True)
class LocalModel:
    model_id: str
    display_name: str
    file_path: str
    architecture: str | None = None
    quantization: str | None = None
    context_size: int | None = None
    file_size: int | None = None
    sha256: str | None = None
    status: ModelStatus = ModelStatus.MODEL_MISSING

    def to_dict(self) -> dict:
        data = asdict(self)
        data["status"] = self.status.value
        return data


def file_sha256(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(chunk_size), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_model(model: LocalModel, *, registry_dir: Path) -> LocalModel:
    path = Path(model.file_path)
    if not path.is_absolute():
        path = (registry_dir / path).resolve()
    if not path.exists() or not path.is_file():
        return LocalModel(**{**model.to_dict(), "status": ModelStatus.MODEL_MISSING})
    if path.suffix.lower() != ".gguf":
        return LocalModel(**{**model.to_dict(), "status": ModelStatus.MODEL_INVALID})
    size = path.stat().st_size
    if size <= 0:
        return LocalModel(**{**model.to_dict(), "file_size": size, "status": ModelStatus.MODEL_INVALID})
    if model.file_size is not None and model.file_size != size:
        return LocalModel(**{**model.to_dict(), "status": ModelStatus.MODEL_INVALID})
    if model.sha256 is not None and file_sha256(path) != model.sha256.lower():
        return LocalModel(**{**model.to_dict(), "status": ModelStatus.MODEL_INVALID})
    return LocalModel(**{**model.to_dict(), "file_size": size, "status": ModelStatus.MODEL_AVAILABLE})


class ModelRegistry:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def load(self) -> tuple[LocalModel, ...]:
        if not self.path.exists():
            return ()
        payload = json.loads(self.path.read_text("utf-8"))
        rows: Iterable[dict] = payload.get("models", ())
        models = []
        for row in rows:
            row = dict(row)
            row["status"] = ModelStatus(row.get("status", ModelStatus.MODEL_MISSING))
            models.append(LocalModel(**row))
        return tuple(models)

    def inspect_all(self) -> tuple[LocalModel, ...]:
        return tuple(inspect_model(model, registry_dir=self.path.parent) for model in self.load())
