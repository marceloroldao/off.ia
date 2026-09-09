from dataclasses import dataclass, field

import pytest

from offia.adapters.memoria import CognitivePacketEnvelope, ResolvedContext
from offia.runtime import OfflineRuntime


class FakeMemoria:
    def __init__(self):
        self.resolve_calls = 0
        self.learn_calls = []
        self.learn_user_calls = []
        self.flush_calls = 0

    def resolve(self, message):
        self.resolve_calls += 1
        return ResolvedContext(("main supply is 24 V",), ("mem-1",), hit=True)

    def learn_user(self, text):
        self.learn_user_calls.append(text)
        return ("mem-user",)

    def learn(self, text):
        self.learn_calls.append(text)
        return ("mem-legacy",)

    def flush(self):
        self.flush_calls += 1


class PacketMemoria(FakeMemoria):
    def resolve(self, message):
        self.resolve_calls += 1
        return ResolvedContext(
            ("legacy text that must not cross the model boundary",),
            ("mem-1",),
            hit=True,
            cognitive_packet=CognitivePacketEnvelope(
                '{"schema_version":1,"question":"What voltage is the main supply?","facts":[{"subject":"main supply","attribute":"voltage","value":"24 V"}]}'
            ),
        )


class LegacyMemoria:
    def __init__(self):
        self.learn_calls = []
        self.flush_calls = 0

    def resolve(self, message):
        return ResolvedContext((), (), hit=False)

    def learn(self, text):
        self.learn_calls.append(text)
        return ("legacy-user",)

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

    def __init__(self, text="24 V"):
        self.last_context = None
        self.text = text

    def generate(self, *, message, context):
        self.last_context = tuple(context)
        return Response(self.text)


def test_memory_context_flows_to_language_and_only_user_input_enters_trusted_write_path():
    memoria = FakeMemoria()
    language = FakeLanguage("24 V")
    result = OfflineRuntime(memoria, language).chat(
        "What voltage is the main supply?"
    )

    assert result.text == "24 V"
    assert language.last_context == ("main supply is 24 V",)
    assert result.metrics.mode == "memoria"
    assert result.metrics.memory_hit is True
    assert result.metrics.retrieved_memory_ids == ("mem-1",)
    assert result.metrics.learned_memory_ids == ("mem-user",)
    assert result.metrics.input_tokens == 11
    assert result.metrics.output_tokens == 3
    assert result.metrics.provider == "local"

    assert memoria.learn_user_calls == ["What voltage is the main supply?"]
    assert memoria.learn_calls == []
    assert "24 V" not in memoria.learn_user_calls
    assert memoria.flush_calls == 1


def test_cognitive_packet_takes_precedence_over_legacy_text_without_offia_interpretation():
    memoria = PacketMemoria()
    language = FakeLanguage("24 V")
    result = OfflineRuntime(memoria, language).chat("What voltage is the main supply?")

    assert len(language.last_context) == 1
    assert language.last_context[0].startswith('{"schema_version":1')
    assert "legacy text that must not cross the model boundary" not in language.last_context[0]
    assert result.context == language.last_context
    assert result.metrics.retrieved_context_chars == len("legacy text that must not cross the model boundary")
    assert result.metrics.context_sent_chars == len(language.last_context[0])
    assert memoria.learn_user_calls == ["What voltage is the main supply?"]
    assert all("24 V" not in item for item in memoria.learn_user_calls)


def test_cognitive_packet_envelope_fails_closed_on_invalid_transport_metadata():
    with pytest.raises(ValueError, match="payload_json must be non-empty"):
        CognitivePacketEnvelope("   ")
    with pytest.raises(ValueError, match="schema_version must be >= 1"):
        CognitivePacketEnvelope("{}", schema_version=0)


def test_legacy_memoria_fallback_receives_user_text_only_never_assistant_output():
    memoria = LegacyMemoria()
    language = FakeLanguage("MODEL INVENTED VALUE")
    result = OfflineRuntime(memoria, language).chat("User-confirmed input")

    assert result.text == "MODEL INVENTED VALUE"
    assert memoria.learn_calls == ["User-confirmed input"]
    assert all("MODEL INVENTED VALUE" not in item for item in memoria.learn_calls)
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
    assert memoria.learn_user_calls == []
    assert memoria.learn_calls == []
    assert memoria.flush_calls == 0
    assert language.last_context == history
    assert result.context == history
    assert result.metrics.mode == "baseline"
    assert result.metrics.memory_hit is False
    assert result.metrics.memory_miss is False
    assert result.metrics.retrieved_context_chars == 0
    assert result.metrics.learned_memory_ids == ()
