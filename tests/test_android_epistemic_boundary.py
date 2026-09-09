from pathlib import Path


def test_android_jni_never_writes_assistant_output_to_factual_learn_turn_path():
    source = Path("android/app/src/main/cpp/offia_memory_jni.cpp").read_text("utf-8")

    # The public JNI signature remains compatible with Kotlin, but assistant
    # output must not be serialized into memoria_mobile_learn_turn_json().
    assert "jstring /*assistant*/" in source
    assert "assistant_request" not in source
    assert "assistant_response" not in source
    assert "assistant_id" not in source

    # The factual path still constructs and submits exactly the trusted user
    # observation, then packs only that returned memory id.
    assert "const std::string user_text = from_jstring(env, user);" in source
    assert "const std::string user_request =" in source
    assert "json_escape(user_text)" in source
    assert "call_learn(runtime, user_request, &user_response)" in source
    assert "const std::string user_id = first_stored_memory_id(user_response);" in source
    assert "json_escape(user_id)" in source
