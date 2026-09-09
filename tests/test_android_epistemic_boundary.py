from pathlib import Path


def test_android_jni_never_writes_assistant_output_to_factual_learn_turn_path():
    source = Path("android/app/src/main/cpp/offia_memory_jni.cpp").read_text("utf-8")

    # The public JNI signature remains compatible with Kotlin, but assistant
    # output must not be serialized into memoria_mobile_learn_turn_json().
    assert "jstring /*assistant*/" in source
    assert "assistant_request" not in source
    assert "assistant_response" not in source
    assert "assistant_id" not in source
    assert '\"role\":\"assistant\"' not in source

    # The factual path still stores the trusted user observation and returns
    # exactly the user memory id to the existing Kotlin surface.
    assert '\"role\":\"user\"' in source
    assert 'json_escape(user_id) + "\\\"]}"' in source
