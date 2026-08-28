from dataclasses import dataclass, field

from offia.adapters.memoria import ResolvedContext
from offia.runtime import OfflineRuntime


class FakeMemoria:
    def __init__(self):
        self.resolve_calls = 0
        self.learn_calls = []
        self.flush_calls = 0

    def resolve(self, message):
        self.resolve_calls += 1
        return ResolvedContext(("main supply is 24 V",), ("mem-1",), hit=True)

    def learn(self, text):
        self.learn_calls.append(text)
        return ("mem-new",)

    def flush(self):
        self.flush_calls += 1


@dataclass
class Usage:
    input_tokens: int = 11
    output_tokens: int = 3
    estimated_cost_usd: float = 0.0


@dataclass
class Response:
    text: str
    provider: str = "local"
    model: str = "fake-local"
    usage: Usage = field(default_factory=Usage)


class FakeLanguage:
    provider_name = "local"
    model_name = "fake-local"

    def __init__(self):
        self.last_context = None

    def generate(self, *, message, context):
        self.last_context = tuple(context)
        return Response("24 V")


def test_memory_context_flows_to_language_adapter_and_turn_is_learned():
    memoria = FakeMemoria()
    language = FakeLanguage()
    result = OfflineRuntime(memoria, language).chat(
        "What voltage is the main supply?"
    )

    assert result.text == "24 V"
    assert language.last_context == ("main supply is 24 V",)
    assert result.metrics.mode == "memoria"
    assert result.metrics.memory_hit is True
    assert result.metrics.retrieved_memory_ids == ("mem-1",)
    assert result.metrics.learned_memory_ids == ("mem-new",)
    assert result.metrics.input_tokens == 11
    assert result.metrics.output_tokens == 3
    assert result.metrics.provider == "local"

    assert memoria.learn_calls == [
        "USER:\nWhat voltage is the main supply?\n\nASSISTANT:\n24 V"
    ]
    assert memoria.flush_calls == 1


def test_baseline_bypasses_memoria_and_forwards_full_context():
    memoria = FakeMemoria()
    language = FakeLanguage()
    history = ("older turn", "another older turn")
    result = OfflineRuntime(memoria, language).chat(
        "What voltage is the main supply?",
        mode="baseline",
        baseline_context=history,
    )

    assert memoria.resolve_calls == 0
    assert memoria.learn_calls == []
    assert memoria.flush_calls == 0
    assert language.last_context == history
    assert result.context == history
    assert result.metrics.mode == "baseline"
    assert result.metrics.memory_hit is False
    assert result.metrics.memory_miss is False
    assert result.metrics.retrieved_context_chars == 0
    assert result.metrics.learned_memory_ids == ()
