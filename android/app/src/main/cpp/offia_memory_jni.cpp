#include <jni.h>

#include "memoria/mobile.h"

#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
memoria_mobile_runtime* from_handle(jlong handle) {
    return reinterpret_cast<memoria_mobile_runtime*>(static_cast<intptr_t>(handle));
}

jlong to_handle(memoria_mobile_runtime* runtime) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(runtime));
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

std::string status_name(memoria_mobile_resolution_status status) {
    switch (status) {
        case MEMORIA_MOBILE_MISS: return "MISS";
        case MEMORIA_MOBILE_HIT: return "HIT";
        case MEMORIA_MOBILE_UNRESOLVED: return "UNRESOLVED";
    }
    return "UNRESOLVED";
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_ia_off_NativeMemoryGateway_nativeOpen(JNIEnv* env, jobject, jstring path) {
    try {
        const std::string storage = from_jstring(env, path);
        memoria_mobile_runtime* runtime = nullptr;
        const auto status = memoria_mobile_open(storage.c_str(), &runtime);
        if (status != MEMORIA_MOBILE_OK || !runtime) {
            throw_illegal_state(env, memoria_mobile_last_error());
            return 0;
        }
        return to_handle(runtime);
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
Java_ia_off_NativeMemoryGateway_nativeResolve(JNIEnv* env, jobject, jlong handle, jstring message) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return nullptr;
    }
    try {
        const std::string query = from_jstring(env, message);
        memoria_mobile_resolution result{};
        size_t needed = 0;
        auto status = memoria_mobile_resolve(
            runtime, query.data(), query.size(), &result, nullptr, 0, &needed);

        std::string context;
        if (status == MEMORIA_MOBILE_BUFFER_TOO_SMALL && result.status == MEMORIA_MOBILE_HIT) {
            std::vector<char> buffer(needed);
            size_t actual = 0;
            status = memoria_mobile_resolve(
                runtime,
                query.data(), query.size(),
                &result,
                buffer.empty() ? nullptr : buffer.data(), buffer.size(),
                &actual);
            if (status == MEMORIA_MOBILE_OK) context.assign(buffer.data(), actual);
        }
        if (status != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, memoria_mobile_last_error());
            return nullptr;
        }

        // Four fields; context may contain arbitrary newlines because Kotlin splits with limit=4.
        std::string packed = status_name(result.status) + "\n" +
            std::to_string(result.memory_id) + "\n" +
            std::to_string(result.score) + "\n" + context;
        return env->NewStringUTF(packed.c_str());
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_ia_off_NativeMemoryGateway_nativeLearn(JNIEnv* env, jobject, jlong handle, jstring user, jstring assistant) {
    auto* runtime = from_handle(handle);
    if (!runtime) {
        throw_illegal_state(env, "Memoria.ia runtime is closed");
        return 0;
    }
    try {
        const std::string user_text = from_jstring(env, user);
        const std::string assistant_text = from_jstring(env, assistant);
        uint64_t memory_id = 0;
        const auto status = memoria_mobile_learn_turn(
            runtime,
            user_text.data(), user_text.size(),
            assistant_text.data(), assistant_text.size(),
            &memory_id);
        if (status != MEMORIA_MOBILE_OK) {
            throw_illegal_state(env, memoria_mobile_last_error());
            return 0;
        }
        return static_cast<jlong>(memory_id);
    } catch (const std::exception& e) {
        throw_illegal_state(env, e.what());
        return 0;
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
        throw_illegal_state(env, memoria_mobile_last_error());
    }
}
