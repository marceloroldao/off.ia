from pathlib import Path

p = Path('android/app/src/main/cpp/offia_memory_jni.cpp')
s = p.read_text(encoding='utf-8')
old = '''        if (status != MEMORIA_MOBILE_OK && status != MEMORIA_MOBILE_UNRESOLVED) {
            throw_illegal_state(env, "Falha ao consultar Memoria.ia");
            return nullptr;
        }
'''
new = '''        if (status != MEMORIA_MOBILE_OK && status != MEMORIA_MOBILE_UNRESOLVED) {
            std::string detail = "Memoria.ia resolve status=" +
                std::to_string(static_cast<int>(status));
            if (!response.empty()) {
                detail += " response=" + response.substr(0, 512);
            }
            throw_illegal_state(env, detail);
            return nullptr;
        }
'''
if old not in s:
    raise SystemExit('resolve diagnostic anchor not found')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
