package ia.off

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class NativeMemoryGateway(context: Context) : MemoryGateway, AutoCloseable {
    companion object {
        /**
         * Durable Memoria.ia storage root for the installed OFF.IA application.
         *
         * IMPORTANT: do not change this directory merely because the APK version
         * changes. Future schema evolution must use explicit migrations in the
         * owning dependency so an in-place Android app update preserves memory.
         */
        private const val DURABLE_STORAGE_ROOT = "memoria-v2"

        init {
            System.loadLibrary("offia-memory")
        }
    }

    private var handle: Long

    init {
        val storage = File(context.filesDir, DURABLE_STORAGE_ROOT).apply { mkdirs() }
        handle = nativeOpen(storage.absolutePath)
        check(handle != 0L) { "Falha ao abrir Memoria.ia nativa" }
    }

    override val available: Boolean
        get() = handle != 0L

    override suspend fun resolve(message: String): MemoryResolution = withContext(Dispatchers.IO) {
        val json = JSONObject(nativeResolve(requireHandle(), message))
        val status = when (json.optString("status")) {
            "HIT" -> MemoryStatus.HIT
            "MISS" -> MemoryStatus.MISS
            "UNRESOLVED" -> MemoryStatus.UNRESOLVED
            else -> MemoryStatus.UNAVAILABLE
        }
        val idsJson = json.optJSONArray("memory_ids")
        val memoryIds = buildList {
            if (idsJson != null) {
                for (i in 0 until idsJson.length()) {
                    val id = idsJson.optString(i)
                    if (id.isNotBlank()) add(id)
                }
            }
        }
        val context = json.optString("selected_context")
        val confidence = if (json.has("confidence")) json.optDouble("confidence") else Double.NaN

        MemoryResolution(
            status = status,
            contextItems = if (status == MemoryStatus.HIT && context.isNotBlank()) listOf(context) else emptyList(),
            memoryIds = memoryIds,
            confidence = confidence.takeUnless { it.isNaN() },
        )
    }

    override suspend fun learnTurn(userText: String, assistantText: String): MemoryLearnResult =
        withContext(Dispatchers.IO) {
            val json = JSONObject(nativeLearn(requireHandle(), userText, assistantText))
            val idsJson = json.optJSONArray("memory_ids")
            val ids = buildList {
                if (idsJson != null) {
                    for (i in 0 until idsJson.length()) {
                        val id = idsJson.optString(i)
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
            MemoryLearnResult(memoryIds = ids)
        }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        nativeFlush(requireHandle())
    }

    override fun close() {
        val current = handle
        if (current != 0L) {
            nativeClose(current)
            handle = 0L
        }
    }

    private fun requireHandle(): Long = handle.also {
        check(it != 0L) { "Memoria.ia runtime fechado" }
    }

    private external fun nativeOpen(path: String): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeResolve(handle: Long, message: String): String
    private external fun nativeLearn(handle: Long, user: String, assistant: String): String
    private external fun nativeFlush(handle: Long)
}
