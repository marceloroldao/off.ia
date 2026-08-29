import os
from pathlib import Path

mode = os.environ.get("OFFIA_KV_CACHE_TYPE", "f16").strip().lower()
allowed = {
    "f16": "GGML_TYPE_F16",
    "q8_0": "GGML_TYPE_Q8_0",
    "q4_0": "GGML_TYPE_Q4_0",
}
if mode not in allowed:
    raise SystemExit(f"unsupported OFFIA_KV_CACHE_TYPE={mode!r}; expected one of {sorted(allowed)}")

root = Path(__file__).resolve().parents[1] / "vendor" / "llama.cpp"
cpp = root / "examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
text = cpp.read_text(encoding="utf-8")

needle = """    ctx_params.n_threads = n_threads;\n    ctx_params.n_threads_batch = n_threads;\n    auto *context = llama_init_from_model(g_model, ctx_params);\n"""
replacement = f"""    ctx_params.n_threads = n_threads;\n    ctx_params.n_threads_batch = n_threads;\n    ctx_params.type_k = {allowed[mode]};\n    ctx_params.type_v = {allowed[mode]};\n    LOGi(\"OFF.IA KV cache mode: {mode} (K/V)\");\n    auto *context = llama_init_from_model(g_model, ctx_params);\n"""
if needle not in text:
    raise SystemExit("ai_chat.cpp KV-cache anchor not found")
text = text.replace(needle, replacement, 1)
cpp.write_text(text, encoding="utf-8")
print(f"OFFIA_KV_CACHE_TYPE={mode} -> {allowed[mode]} for K and V")
