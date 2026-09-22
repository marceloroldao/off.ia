#include <jni.h>

#include "memoria_mobile.h"

#include <cstdint>
#include <stdexcept>
#include <string>

namespace {
constexpr const char* OFFIA_ORGANIZATION_ID = "offia-local";

memoria_mobile_handle* from_handle(jlong handle) {
    return reinterpret_cast<memoria_mobile_handle*>(static_cast<intptr_t>(handle));
}

jlong to_handle(memoria_mobile_handle* handle) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

std::string from_jstring(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("GetStringUTFChars failed");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throw_illegal_state(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message.c_str());
}

std::string json_escape(const std::string& value) {
    std::string out;
    out.reserve(value.size() + 16);
    for (unsigned char ch : value) {
        switch (ch) {
            case '\\': out += "\\\\"; break;
            case '"': out += "\\\""; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out.push_back(static_cast<char>(ch)); break;
        }
    }
    return out;
}

std::string take_response(memoria_mobile_buffer out) {
    std::string result;
    if (out.data && out.size) {
        result.assign(reinterpret_cast<const char*>(out.data), out.size);
    }
    memoria_mobile_free_buffer(out);
    return result;
}

std::string first_stored_memory_id(const std::string& json) {
    const std::string marker = "\"stored_memory_ids\":[\"";
    const auto start = json.find(marker);
    if (start == std::string::npos) return {};
    const auto value_start = start + marker.size();
    const auto end = json.find('"', value_start);
    if (end == std::string::npos) return {};
    return json.substr(value_start, end - value_start);
}

memoria_mobile_status call_learn(memoria_mobile_handle* handle, const std::string& request, std::string* response) {
    memoria_mobile_buffer in{
        reinterpret_cast<const uint8_t*>(request.data()), request.size()
    };
    memoria_mobile_buffer out{nullptr, 0};
    const auto status = memoria_mobile_learn_turn_json(handle, in, &out);
    *response = take_response(out);
    return status;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_ia_off_NativeMemoryGateway_nativeOpen(JNIEnv* env, jobject, jstring path) {
    try {
        const std::string storage = from_jstring(env, path);
        memoria_mobile_handle* handle = nullptr;
        const auto status = memoria_mobile_open(storage.c_str(), OFFIA_ORGANIZATION_ID, &handle);
        if (status != MEMORIA_MOBILE_OK || !handle) {
            throw_illegal_state(env, "Falha ao abrir Memoria.ia/BDR");
            return 0;
        }
        return to_handle(handle);
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ia_off_NativeMemoryGateway_nativeClose(JNIEnv*, jobject, jlong handle) {
    memoria_mobile_close(from_handle(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeResolve(JNIEnv* env, jobject, jlong handle, jstring request_json) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string request = from_jstring(env, request_json);
        memoria_mobile_buffer in{
            reinterpret_cast<const uint8_t*>(request.data()), request.size()
        };
        memoria_mobile_buffer out{nullptr, 0};
        const auto status = memoria_mobile_resolve_context_json(runtime, in, &out);
        const std::string response = take_response(out);
        if (status != MEMORIA_MOBILE_OK && status != MEMORIA_MOBILE_UNRESOLVED) {
            throw_illegal_state(env, "Falha ao consultar Memoria.ia");
            return nullptr;
        }
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeResolveStructural(JNIEnv* env, jobject, jlong handle, jstring request_json) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string request = from_jstring(env, request_json);
        memoria_mobile_buffer in{
            reinterpret_cast<const uint8_t*>(request.data()), request.size()
        };
        memoria_mobile_buffer out{nullptr, 0};
        const auto status = memoria_mobile_resolve_structural_text_json(runtime, in, &out);
        const std::string response = take_response(out);
        if (status != MEMORIA_MOBILE_OK && status != MEMORIA_MOBILE_UNRESOLVED) {
            throw_illegal_state(env, "Falha ao consultar memória estrutural da Memoria.ia");
            return nullptr;
        }
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeObserveStructural(JNIEnv* env, jobject, jlong handle, jstring request_json) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string request = from_jstring(env, request_json);
        memoria_mobile_buffer in{
            reinterpret_cast<const uint8_t*>(request.data()), request.size()
        };
        memoria_mobile_buffer out{nullptr, 0};
        const auto status = memoria_mobile_observe_structural_text_json(runtime, in, &out);
        const std::string response = take_response(out);
        if (status != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, "Falha ao observar memória estrutural na Memoria.ia");
            return nullptr;
        }
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeLearn(JNIEnv* env, jobject, jlong handle, jstring user, jstring assistant) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string user_text = from_jstring(env, user);
        const std::string assistant_text = from_jstring(env, assistant);

        std::string user_response;
        const std::string user_request =
            "{\"role\":\"user\",\"text\":\"" + json_escape(user_text) + "\"}";
        if (call_learn(runtime, user_request, &user_response) != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, "Falha ao persistir turno do usuario na Memoria.ia");
            return nullptr;
        }
        const std::string user_id = first_stored_memory_id(user_response);
        if (user_id.empty()) {
            throw_illegal_state(env, "Memoria.ia nao retornou memory_id do usuario");
            return nullptr;
        }

        std::string assistant_response;
        const std::string assistant_request =
            "{\"role\":\"assistant\",\"text\":\"" + json_escape(assistant_text) +
            "\",\"ultimate_source_memory_id\":\"" + json_escape(user_id) + "\"}";
        if (call_learn(runtime, assistant_request, &assistant_response) != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, "Falha ao persistir turno do assistente na Memoria.ia");
            return nullptr;
        }
        const std::string assistant_id = first_stored_memory_id(assistant_response);
        if (assistant_id.empty()) {
            throw_illegal_state(env, "Memoria.ia nao retornou memory_id do assistente");
            return nullptr;
        }

        const std::string packed =
            "{\"memory_ids\":[\"" + json_escape(user_id) + "\",\"" +
            json_escape(assistant_id) + "\"]}";
        return env->NewStringUTF(packed.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeLearnExternal(JNIEnv* env, jobject, jlong handle, jstring request_json) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        (void) request_json;
        // Memoria.ia v1.0.0-rc4 exposes ABI v1 but does not yet export the
        // additive external-public learning symbol. Keep the JNI entry point
        // stable and fail explicitly until that ABI is released.
        throw_illegal_state(
            env,
            "Memoria.ia RC4 nao oferece aprendizado external_public na ABI movel"
        );
        return nullptr;
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ia_off_NativeMemoryGateway_nativeExport(JNIEnv* env, jobject, jlong handle, jstring request_json) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string request = from_jstring(env, request_json);
        memoria_mobile_buffer in{
            reinterpret_cast<const uint8_t*>(request.data()), request.size()
        };
        memoria_mobile_buffer out{nullptr, 0};
        const auto status = memoria_mobile_export_snapshot_json(runtime, in, &out);
        const std::string response = take_response(out);
        if (status != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, "Falha ao exportar snapshot da Memoria.ia");
            return nullptr;
        }
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ia_off_NativeMemoryGateway_nativeFlush(JNIEnv* env, jobject, jlong handle) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return;
    }
    if (memoria_mobile_flush(runtime) != MEMORIA_MOBILE_OK) {
        throw_illegal_state(env, "Falha ao sincronizar Memoria.ia/BDR");
    }
}
