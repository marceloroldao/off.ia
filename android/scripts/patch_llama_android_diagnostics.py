from pathlib import Path

root = Path(__file__).resolve().parents[1] / "vendor" / "llama.cpp"
cpp = root / "examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
kt = root / "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
interface = root / "examples/llama.android/lib/src/main/java/com/arm/aichat/InferenceEngine.kt"

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
addition = '''    g_model = model;\n    return 0;\n}\n\nextern "C"\nJNIEXPORT jstring JNICALL\nJava_com_arm_aichat_internal_InferenceEngineImpl_lastLoadError(JNIEnv *env, jobject) {\n    return env->NewStringUTF(g_last_load_error.c_str());\n}\n\nextern "C"\nJNIEXPORT jstring JNICALL\nJava_com_arm_aichat_internal_InferenceEngineImpl_modelMetadataNative(JNIEnv *env, jobject) {\n    char architecture[128] = {0};\n    if (g_model != nullptr) {\n        llama_model_meta_val_str(g_model, "general.architecture", architecture, sizeof(architecture));\n    }\n    const char * chat_template = g_model != nullptr ? llama_model_chat_template(g_model, nullptr) : nullptr;\n    std::string metadata(architecture);\n    metadata.push_back('\\n');\n    if (chat_template != nullptr) metadata.append(chat_template);\n    return env->NewStringUTF(metadata.c_str());\n}\n\nstatic llama_context *init_context'''
if marker not in cpp_text:
    raise SystemExit("ai_chat.cpp anchor 3 not found")
cpp_text = cpp_text.replace(marker, addition, 1)

# Preserve llama.cpp's native behavior: use the GGUF template only when explicit.
# Missing template metadata intentionally remains a plain-text fallback.

# Tiny models such as SmolLM2-135M can fall into short repetition loops. Keep the
# upstream sampler chain but enable conservative repetition/DRY penalties.
sampler_needle = '''static common_sampler *new_sampler(float temp) {\n    common_params_sampling sparams;\n    sparams.temp = temp;\n    return common_sampler_init(g_model, sparams);\n}\n'''
sampler_replacement = '''static common_sampler *new_sampler(float temp) {\n    common_params_sampling sparams;\n    sparams.temp = temp;\n    sparams.top_k = 40;\n    sparams.top_p = 0.90f;\n    sparams.min_p = 0.05f;\n    sparams.penalty_last_n = 64;\n    sparams.penalty_repeat = 1.12f;\n    sparams.dry_multiplier = 0.6f;\n    sparams.dry_allowed_length = 3;\n    sparams.dry_penalty_last_n = 128;\n    return common_sampler_init(g_model, sparams);\n}\n'''
if sampler_needle not in cpp_text:
    raise SystemExit("ai_chat.cpp sampler anchor not found")
cpp_text = cpp_text.replace(sampler_needle, sampler_replacement, 1)

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
needle = '''    @FastNative\n    private external fun lastLoadError(): String\n\n    @FastNative\n    private external fun prepare(): Int\n'''
replacement = '''    @FastNative\n    private external fun lastLoadError(): String\n\n    @FastNative\n    private external fun modelMetadataNative(): String\n\n    @FastNative\n    private external fun prepare(): Int\n'''
if needle not in kt_text:
    raise SystemExit("InferenceEngineImpl.kt metadata native anchor not found")
kt_text = kt_text.replace(needle, replacement, 1)

needle = '''    override suspend fun loadModel(pathToModel: String) =\n'''
replacement = '''    override suspend fun modelMetadata(): String =\n        withContext(llamaDispatcher) {\n            check(_state.value is InferenceEngine.State.ModelReady) {\n                "Model metadata requested before model ready"\n            }\n            modelMetadataNative()\n        }\n\n    override suspend fun loadModel(pathToModel: String) =\n'''
if needle not in kt_text:
    raise SystemExit("InferenceEngineImpl.kt modelMetadata method anchor not found")
kt_text = kt_text.replace(needle, replacement, 1)
kt.write_text(kt_text, encoding="utf-8")

interface_text = interface.read_text(encoding="utf-8")
needle = '''    suspend fun loadModel(pathToModel: String)\n\n'''
replacement = '''    suspend fun loadModel(pathToModel: String)\n\n    /** Returns general.architecture plus the exact embedded chat template. */\n    suspend fun modelMetadata(): String\n\n'''
if needle not in interface_text:
    raise SystemExit("InferenceEngine.kt metadata anchor not found")
interface.write_text(interface_text.replace(needle, replacement, 1), encoding="utf-8")

print("OFFIA_LLAMA_CHAT_V5: diagnostics + GGUF-native template detection + safe plain fallback + anti-repeat patched")
