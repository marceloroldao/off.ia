package ia.off

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Durable transcript for the Android UI.
 *
 * This is intentionally separate from Memoria.ia. The transcript preserves what
 * the user sees; semantic retrieval/learning remains owned by Memoria.ia + BDR.
 */
class ChatStore(context: Context) {
    companion object {
        private const val SCHEMA_VERSION = 1
        private const val FILE_NAME = "chat-history-v1.json"
        private const val MAX_MESSAGES = 2000
    }

    private val file = File(context.filesDir, FILE_NAME)

    fun load(): List<ChatMessage> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optInt("schema_version", -1) != SCHEMA_VERSION) return@runCatching emptyList()
            val array = root.optJSONArray("messages") ?: JSONArray()
            buildList {
                val start = (array.length() - MAX_MESSAGES).coerceAtLeast(0)
                for (i in start until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val role = item.optString("role").trim()
                    val text = item.optString("text")
                    if (role.isNotEmpty() && text.isNotEmpty() && text != "…") {
                        add(ChatMessage(role, text))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(messages: List<ChatMessage>) {
        val stable = messages
            .asSequence()
            .filter { it.text.isNotEmpty() && it.text != "…" }
            .takeLastCompat(MAX_MESSAGES)
            .toList()

        val array = JSONArray()
        stable.forEach { message ->
            array.put(JSONObject().apply {
                put("role", message.role)
                put("text", message.text)
            })
        }
        val root = JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("messages", array)
        }

        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("Não foi possível substituir o histórico do chat")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Não foi possível concluir o histórico do chat")
        }
    }
}

private fun <T> Sequence<T>.takeLastCompat(limit: Int): Sequence<T> {
    val buffer = ArrayDeque<T>(limit)
    for (item in this) {
        if (buffer.size == limit) buffer.removeFirst()
        buffer.addLast(item)
    }
    return buffer.asSequence()
}
