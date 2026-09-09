from pathlib import Path


def test_android_jni_never_writes_assistant_output_to_factual_learn_turn_path():
    source = Path("android/app/src/main/cpp/offia_memory_jni.cpp").read_text("utf-8")

    assert "jstring /*assistant*/" in source
    assert "assistant_request" not in source
    assert "assistant_response" not in source
    assert "assistant_id" not in source
    assert "const std::string user_text = from_jstring(env, user);" in source
    assert "const std::string user_request =" in source
    assert "json_escape(user_text)" in source
    assert "call_learn(runtime, user_request, &user_response)" in source
    assert "const std::string user_id = first_stored_memory_id(user_response);" in source


def test_android_jni_routes_cognitive_operations_to_separate_memoria_symbols():
    source = Path("android/app/src/main/cpp/offia_memory_jni.cpp").read_text("utf-8")

    assert "memoria_mobile_compile_context_json" in source
    assert "memoria_mobile_validate_response_json" in source
    assert "memoria_mobile_decide_learning_json" in source

    validate_body = source.split("Java_ia_off_NativeMemoryGateway_nativeValidateResponse", 1)[1].split(
        "Java_ia_off_NativeMemoryGateway_nativeDecideLearning", 1
    )[0]
    decide_body = source.split("Java_ia_off_NativeMemoryGateway_nativeDecideLearning", 1)[1].split(
        "Java_ia_off_NativeMemoryGateway_nativeLearn", 1
    )[0]
    assert "call_learn(" not in validate_body
    assert "call_learn(" not in decide_body


def test_android_gateway_uses_context_compiler_for_normal_resolve_cycle():
    source = Path("android/app/src/main/java/ia/off/NativeMemoryGateway.kt").read_text("utf-8")
    resolve_body = source.split("override suspend fun resolve(", 1)[1].split(
        "override suspend fun compileContext", 1
    )[0]

    assert "nativeCompileContext(requireHandle(), request.toString())" in resolve_body
    assert "nativeResolve(requireHandle(), request.toString())" not in resolve_body
    assert 'listOf(json.toString())' in resolve_body
    assert 'optJSONArray("memory_ids")' in resolve_body
    assert 'optJSONObject("activation")' in resolve_body


def test_android_gateway_quarantines_model_output_after_user_only_learn():
    source = Path("android/app/src/main/java/ia/off/NativeMemoryGateway.kt").read_text("utf-8")
    learn_body = source.split("override suspend fun learnTurn", 1)[1].split(
        "override suspend fun learnExternalKnowledge", 1
    )[0]

    assert "nativeLearn(requireHandle(), userText, assistantText)" in learn_body
    assert "validateModelResponse(" in learn_body
    assert "val responseId = userMemoryIds.first()" in learn_body
    assert "UUID.randomUUID().toString()" not in learn_body
    assert "EpistemicAuditBridge.record(" in learn_body
    assert "candidateMemoryId = validation.candidateMemoryId" in learn_body
    assert "validationStatus = validation.consistencyStatus" in learn_body
    assert "decideLearning(" not in learn_body


def test_android_prompt_preserves_structured_cognitive_packet():
    source = Path("android/app/src/main/java/ia/off/MemoryGateway.kt").read_text("utf-8")
    assert 'COGNITIVE_PACKET_SCHEMA = "\\\"packet_schema\\\":\\\"memoria.cognitive.packet.v1\\\""' in source
    assert "if (cognitivePacket != null) return materializeCognitivePrompt(userText, cognitivePacket)" in source
    assert "MAX_COGNITIVE_PACKET_CHARS = 6000" in source


def test_android_kotlin_learning_gate_allows_only_trusted_validators():
    source = Path("android/app/src/main/java/ia/off/NativeMemoryGateway.kt").read_text("utf-8")
    assert 'validatorSource in setOf("USER_CONFIRMED", "SENSOR_OBSERVED")' in source
    assert 'put("accepted", accepted)' in source
    assert 'put("candidate_memory_id", candidateMemoryId)' in source
    assert 'check(!json.optBoolean("promoted", true))' in source


def test_android_chat_store_persists_epistemic_candidate_and_learning_metadata():
    models = Path("android/app/src/main/java/ia/off/ChatModels.kt").read_text("utf-8")
    store = Path("android/app/src/main/java/ia/off/ChatStore.kt").read_text("utf-8")

    assert "private const val SCHEMA_VERSION = 5" in store
    assert "setOf(2, 3, 4, SCHEMA_VERSION)" in store

    for kotlin_field in (
        "responseId",
        "candidateMemoryId",
        "validationStatus",
        "learningDecisionId",
        "learningAccepted",
        "promotedMemoryId",
    ):
        assert f"val {kotlin_field}:" in models

    for json_key in (
        "response_id",
        "candidate_memory_id",
        "validation_status",
        "learning_decision_id",
        "learning_accepted",
        "promoted_memory_id",
    ):
        assert f'put("{json_key}"' in store
        assert f'optString("{json_key}")' in store or f'has("{json_key}")' in store


def test_android_candidate_identity_is_restart_reconstructable_from_factual_turn():
    gateway = Path("android/app/src/main/java/ia/off/NativeMemoryGateway.kt").read_text("utf-8")
    bridge = Path("android/app/src/main/java/ia/off/EpistemicAuditBridge.kt").read_text("utf-8")
    models = Path("android/app/src/main/java/ia/off/ChatModels.kt").read_text("utf-8")

    assert "val responseId = userMemoryIds.first()" in gateway
    assert "mobile:42 -> response:mobile:42" in gateway
    assert "candidate identity can be reconstructed" in bridge
    assert "val effectiveResponseId: String?" in models
    assert "get() = responseId ?: learnedMemoryIds.firstOrNull()" in models
    assert "val effectiveCandidateMemoryId: String?" in models
    assert 'get() = candidateMemoryId ?: effectiveResponseId?.let { "response:$it" }' in models


def test_android_explicit_learning_actions_never_run_from_normal_generation():
    main = Path("android/app/src/main/java/ia/off/MainActivity.kt").read_text("utf-8")
    generation = main.split("val learned = memory.learnTurn", 1)[1].split("saveWorkspace()", 1)[0]
    decision = main.split("fun applyLearningDecision", 1)[1].split("fun runCuriosity", 1)[0]

    assert "decideLearning(" not in generation
    assert "memory.decideLearning(" in decision
    assert 'validatorSource = "USER_CONFIRMED"' in decision
    assert 'validatorId = "offia-ui-user"' in decision
    assert "memory.flush()" in decision
    assert "learningDecisionId = result.decisionId" in decision
    assert "learningAccepted = result.accepted" in decision


def test_android_learning_ui_requires_explicit_confirmation_or_rejection():
    card = Path("android/app/src/main/java/ia/off/MessageCard.kt").read_text("utf-8")

    assert "onLearningDecision: ((String, Boolean) -> Unit)? = null" in card
    assert 'Text("Confirmar como memória")' in card
    assert 'Text("Rejeitar aprendizado")' in card
    assert 'title = { Text(if (accepted) "Confirmar como memória?" else "Rejeitar aprendizado?") }' in card
    assert "onLearningDecision?.invoke(message.id, accepted)" in card
    assert "message.memory?.learningDecisionId == null" in card


def test_android_regeneration_clears_stale_epistemic_candidate_and_decision():
    main = Path("android/app/src/main/java/ia/off/MainActivity.kt").read_text("utf-8")
    regen = main.split("fun regenerateResponse", 1)[1].split("fun applyLearningDecision", 1)[0]

    assert "learnedMemoryIds = emptyList()" in regen
    assert "responseId = null" in regen
    assert "candidateMemoryId = null" in regen
    assert "validationStatus = null" in regen
    assert "learningDecisionId = null" in regen
    assert "learningAccepted = null" in regen
    assert "promotedMemoryId = null" in regen
