import os
from pathlib import Path

mode = os.environ.get("OFFIA_KV_CACHE_TYPE", "q8_0").strip().lower()
allowed = {
    "f16": "GGML_TYPE_F16",
    "q8_0": "GGML_TYPE_Q8_0",
}
if mode not in allowed:
    raise SystemExit(
        f"unsupported production OFFIA_KV_CACHE_TYPE={mode!r}; expected one of {sorted(allowed)}"
    )

root = Path(__file__).resolve().parents[1] / "vendor" / "llama.cpp"
cpp = root / "examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
source = cpp.read_text(encoding="utf-8")
needle = """    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
"""
replacement = f"""    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.type_k = {allowed[mode]};
    ctx_params.type_v = {allowed[mode]};
    LOGi("OFF.IA KV cache mode: {mode} (K/V)");
    auto *context = llama_init_from_model(g_model, ctx_params);
"""
if source.count(needle) != 1:
    raise SystemExit("ai_chat.cpp KV-cache anchor missing or not unique")
cpp.write_text(source.replace(needle, replacement, 1), encoding="utf-8")
print(f"OFFIA_KV_CACHE_TYPE={mode} -> {allowed[mode]} for K and V")
