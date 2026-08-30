package ia.off

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ChatSession(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage>,
    var updatedAt: Long,
)

data class ChatWorkspace(
    val sessions: MutableList<ChatSession>,
    var activeSessionId: String,
)

/**
 * Durable UI transcript workspace.
 *
 * This remains separate from Memoria.ia. Sessions preserve visible chat history;
 * semantic learning/retrieval remains owned by Memoria.ia + BDR.
 */
class ChatStore(context: Context) {
    companion object {
        private const val SCHEMA_VERSION = 2
        private const val FILE_NAME = "chat-workspace-v2.json"
        private const val LEGACY_FILE_NAME = "chat-history-v1.json"
        private const val MAX_MESSAGES_PER_SESSION = 2000
        private const val MAX_SESSIONS = 100
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)

    fun load(): ChatWorkspace {
        if (file.isFile) {
            runCatching { parseWorkspace(file.readText(Charsets.UTF_8)) }.getOrNull()?.let { return it }
        }

        val migrated = migrateLegacy()
        if (migrated != null) {
            save(migrated)
            return migrated
        }

        return newWorkspace()
    }

    fun newWorkspace(): ChatWorkspace {
        val session = newSession("Nova conversa")
        return ChatWorkspace(mutableListOf(session), session.id)
    }

    fun newSession(title: String = "Nova conversa"): ChatSession = ChatSession(
        id = UUID.randomUUID().toString(),
        title = title,
        messages = mutableListOf(),
        updatedAt = System.currentTimeMillis(),
    )

    @Synchronized
    fun save(workspace: ChatWorkspace) {
        val keptSessions = workspace.sessions
            .sortedByDescending { it.updatedAt }
            .take(MAX_SESSIONS)

        val sessionsArray = JSONArray()
        keptSessions.forEach { session ->
            val messagesArray = JSONArray()
            session.messages
                .asSequence()
                .filter { it.text.isNotEmpty() && it.text != "…" }
                .takeLastCompat(MAX_MESSAGES_PER_SESSION)
                .forEach { message ->
                    messagesArray.put(JSONObject().apply {
                        put("role", message.role)
                        put("text", message.text)
                    })
                }

            sessionsArray.put(JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("updated_at", session.updatedAt)
                put("messages", messagesArray)
            })
        }

        val active = keptSessions.firstOrNull { it.id == workspace.activeSessionId }?.id
            ?: keptSessions.firstOrNull()?.id
            ?: newSession().id

        val root = JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("active_session_id", active)
            put("sessions", sessionsArray)
        }
        atomicWrite(root.toString())
    }

    private fun parseWorkspace(text: String): ChatWorkspace {
        val root = JSONObject(text)
        require(root.optInt("schema_version", -1) == SCHEMA_VERSION)
        val sessionsJson = root.optJSONArray("sessions") ?: JSONArray()
        val sessions = mutableListOf<ChatSession>()
        for (i in 0 until sessionsJson.length()) {
            val item = sessionsJson.optJSONObject(i) ?: continue
            val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
            val title = item.optString("title").ifBlank { "Conversa" }
            val updatedAt = item.optLong("updated_at", System.currentTimeMillis())
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()
            val messages = mutableListOf<ChatMessage>()
            val start = (messagesJson.length() - MAX_MESSAGES_PER_SESSION).coerceAtLeast(0)
            for (m in start until messagesJson.length()) {
                val message = messagesJson.optJSONObject(m) ?: continue
                val role = message.optString("role").trim()
                val body = message.optString("text")
                if (role.isNotEmpty() && body.isNotEmpty() && body != "…") {
                    messages += ChatMessage(role, body)
                }
            }
            sessions += ChatSession(id, title, messages, updatedAt)
        }

        if (sessions.isEmpty()) return newWorkspace()
        val requestedActive = root.optString("active_session_id")
        val active = sessions.firstOrNull { it.id == requestedActive }?.id ?: sessions.first().id
        return ChatWorkspace(sessions, active)
    }

    private fun migrateLegacy(): ChatWorkspace? {
        if (!legacyFile.isFile) return null
        return runCatching {
            val root = JSONObject(legacyFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray("messages") ?: JSONArray()
            val messages = mutableListOf<ChatMessage>()
            val start = (array.length() - MAX_MESSAGES_PER_SESSION).coerceAtLeast(0)
            for (i in start until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role").trim()
                val text = item.optString("text")
                if (role.isNotEmpty() && text.isNotEmpty() && text != "…") {
                    messages += ChatMessage(role, text)
                }
            }
            val title = messages.firstOrNull { it.role == "Você" }?.text?.take(40)?.ifBlank { null }
                ?: "Conversa anterior"
            val session = newSession(title).apply { this.messages += messages }
            ChatWorkspace(mutableListOf(session), session.id)
        }.getOrNull()
    }

    private fun atomicWrite(text: String) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("Não foi possível substituir o workspace de conversas")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Não foi possível concluir o workspace de conversas")
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
