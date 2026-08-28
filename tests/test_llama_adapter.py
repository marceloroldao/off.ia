import pytest

from offia.adapters.llama import LlamaCppAdapter


def test_loopback_default():
    adapter = LlamaCppAdapter()
    assert adapter.provider_name == "local"


def test_non_loopback_rejected():
    with pytest.raises(ValueError):
        LlamaCppAdapter("http://192.0.2.10:8080")


def test_context_materialization():
    messages = LlamaCppAdapter._messages("question", ["fact A", "fact B"])
    assert messages[0]["role"] == "system"
    assert "fact A" in messages[0]["content"]
    assert messages[-1] == {"role": "user", "content": "question"}
