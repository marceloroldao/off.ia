from pathlib import Path
import json

from offia.models import LocalModel, ModelRegistry, ModelStatus, inspect_model


def test_missing_model(tmp_path: Path):
    model = LocalModel("x", "X", "missing.gguf")
    assert inspect_model(model, registry_dir=tmp_path).status == ModelStatus.MODEL_MISSING


def test_non_gguf_is_invalid(tmp_path: Path):
    path = tmp_path / "bad.bin"
    path.write_bytes(b"x")
    model = LocalModel("x", "X", str(path))
    assert inspect_model(model, registry_dir=tmp_path).status == ModelStatus.MODEL_INVALID


def test_registry_loads(tmp_path: Path):
    path = tmp_path / "registry.json"
    path.write_text(json.dumps({"models": [{"model_id":"x","display_name":"X","file_path":"x.gguf"}]}), "utf-8")
    assert ModelRegistry(path).load()[0].model_id == "x"
