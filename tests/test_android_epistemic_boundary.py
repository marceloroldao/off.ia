from pathlib import Path


def test_android_jni_never_writes_assistant_output_to_factual_learn_turn_path():
    source = Path("android/app/src/main/cpp/offia_memory_jni.cpp").read_text("utf-8")

    # The public JNI signature remains compatible with Kotlin, but assistant
    # output must not be serialized into memoria_mobile_learn_turn_json().
    assert "jstring /*assistant*/" in source
    assert "assistant_request" not in source
    assert "assistant_response" not in source
    assert "assistant_id" not in source

    # The factual path constructs and submits only the trusted user observation.
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

    # Cognitive validation and Learning Gate calls are independent from the
    # factual user learn helper. No model response is fed through call_learn().
    validate_body = source.split("Java_ia_off_NativeMemoryGateway_nativeValidateResponse", 1)[1].split(
        "Java_ia_off_NativeMemoryGateway_nativeDecideLearning", 1
    )[0]
    decide_body = source.split("Java_ia_off_NativeMemoryGateway_nativeDecideLearning", 1)[1].split(
        "Java_ia_off_NativeMemoryGateway_nativeLearn", 1
    )[0]
    assert "call_learn(" not in validate_body
    assert "call_learn(" not in decide_body


def test_android_kotlin_learning_gate_allows_only_trusted_validators():
    source = Path("android/app/src/main/java/ia/off/NativeMemoryGateway.kt").read_text("utf-8")
    assert 'validatorSource in setOf("USER_CONFIRMED", "SENSOR_OBSERVED")' in source
    assert 'put("accepted", accepted)' in source
    assert 'put("candidate_memory_id", candidateMemoryId)' in source
    assert 'check(!json.optBoolean("promoted", true))' in source
