from pathlib import Path

root = Path(__file__).resolve().parents[1] / "vendor" / "llama.cpp"
cpp = root / "examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
kt = root / "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"

cpp_text = cpp.read_text(encoding="utf-8")

needle = 'static common_sampler                   * g_sampler;\n'
insert = '''static common_sampler                   * g_sampler;\n\nstatic std::string g_last_load_error;\n\nstatic void offia_android_log_callback(ggml_log_level level, const char * text, void * user_data) {\n    aichat_android_log_callback(level, text, user_data);\n    if (text != nullptr) {\n        g_last_load_error.append(text);\n        if (g_last_load_error.size() > 8192) {\n            g_last_load_error.erase(0, g_last_load_error.size() - 8192);\n        }\n    }\n}\n'''
if needle not in cpp_text:
    raise SystemExit("ai_chat.cpp anchor 1 not found")
cpp_text = cpp_text.replace(needle, insert, 1)

cpp_text = cpp_text.replace(
    'llama_log_set(aichat_android_log_callback, nullptr);',
    'llama_log_set(offia_android_log_callback, nullptr);',
    1,
)

needle = '''    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);\n    LOGd("%s: Loading model from: \\n%s\\n", __func__, model_path);\n\n    auto *model = llama_model_load_from_file(model_path, model_params);\n'''
replacement = '''    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);\n    LOGd("%s: Loading model from: \\n%s\\n", __func__, model_path);\n\n    g_last_load_error.clear();\n    auto *model = llama_model_load_from_file(model_path, model_params);\n'''
if needle not in cpp_text:
    raise SystemExit("ai_chat.cpp anchor 2 not found")
cpp_text = cpp_text.replace(needle, replacement, 1)

marker = '''    g_model = model;\n    return 0;\n}\n\nstatic llama_context *init_context'''
addition = '''    g_model = model;\n    return 0;\n}\n\nextern "C"\nJNIEXPORT jstring JNICALL\nJava_com_arm_aichat_internal_InferenceEngineImpl_lastLoadError(JNIEnv *env, jobject) {\n    return env->NewStringUTF(g_last_load_error.c_str());\n}\n\nstatic llama_context *init_context'''
if marker not in cpp_text:
    raise SystemExit("ai_chat.cpp anchor 3 not found")
cpp_text = cpp_text.replace(marker, addition, 1)
cpp.write_text(cpp_text, encoding="utf-8")

kt_text = kt.read_text(encoding="utf-8")
needle = '''    @FastNative\n    private external fun load(modelPath: String): Int\n\n    @FastNative\n    private external fun prepare(): Int\n'''
replacement = '''    @FastNative\n    private external fun load(modelPath: String): Int\n\n    @FastNative\n    private external fun lastLoadError(): String\n\n    @FastNative\n    private external fun prepare(): Int\n'''
if needle not in kt_text:
    raise SystemExit("InferenceEngineImpl.kt anchor 1 not found")
kt_text = kt_text.replace(needle, replacement, 1)

needle = '''                load(pathToModel).let {\n                    // TODO-han.yin: find a better way to pass other error codes\n                    if (it != 0) throw UnsupportedArchitectureException()\n                }\n'''
replacement = '''                load(pathToModel).let { code ->\n                    if (code != 0) {\n                        val nativeError = lastLoadError().trim().takeLast(4000)\n                        if (nativeError.isNotBlank()) {\n                            throw IOException("llama.cpp load failed ($code): $nativeError")\n                        }\n                        throw UnsupportedArchitectureException()\n                    }\n                }\n'''
if needle not in kt_text:
    raise SystemExit("InferenceEngineImpl.kt anchor 2 not found")
kt_text = kt_text.replace(needle, replacement, 1)
kt.write_text(kt_text, encoding="utf-8")

print("Patched llama.android loader diagnostics")
