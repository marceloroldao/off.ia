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
 * This remains separate from Memoria.ia. Sessions preserve visible chat history
 * and response diagnostics; semantic learning/retrieval remains owned by
 * Memoria.ia + BDR.
 */
class ChatStore(context: Context) {
    companion object {
        private const val SCHEMA_VERSION = 3
        private const val PREVIOUS_SCHEMA_VERSION = 2
        private const val FILE_NAME = "chat-workspace-v2.json"
        private const val LEGACY_FILE_NAME = "chat-history-v1.json"
        private const val MAX_MESSAGES_PER_SESSION = 2000
        private const val MAX_SESSIONS = 100
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)

    fun load(): ChatWorkspace {
        if (file.isFile) {
            runCatching { parseWorkspace(file.readText(Charsets.UTF_8)) }.getOrNull()?.let { workspace ->
                // Rewrite schema-v2 workspaces as v3 after successful migration.
                runCatching { save(workspace) }
                return workspace
            }
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
                .forEach { message -> messagesArray.put(messageToJson(message)) }

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

    private fun messageToJson(message: ChatMessage): JSONObject = JSONObject().apply {
        put("id", message.id)
        put("role", message.role)
        put("text", message.text)
        put("created_at", message.createdAt)

        message.memory?.let { memory ->
            put("memory", JSONObject().apply {
                put("status", memory.status.name)
                put("memory_ids", JSONArray(memory.memoryIds))
                memory.confidence?.let { put("confidence", it) }
                put("selected_context", memory.selectedContext)
                put("trajectory_used", memory.trajectoryUsed)
                put("conversation_window_count", memory.conversationWindowCount)
            })
        }

        message.generation?.let { generation ->
            put("generation", JSONObject().apply {
                put("source", generation.source.name)
                generation.modelName?.let { put("model_name", it) }
                generation.latencyMs?.let { put("latency_ms", it) }
            })
        }
    }

    private fun parseWorkspace(text: String): ChatWorkspace {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schema_version", -1)
        require(schemaVersion == SCHEMA_VERSION || schemaVersion == PREVIOUS_SCHEMA_VERSION)

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
                parseMessage(message, updatedAt)?.let(messages::add)
            }
            sessions += ChatSession(id, title, messages, updatedAt)
        }

        if (sessions.isEmpty()) return newWorkspace()
        val requestedActive = root.optString("active_session_id")
        val active = sessions.firstOrNull { it.id == requestedActive }?.id ?: sessions.first().id
        return ChatWorkspace(sessions, active)
    }

    private fun parseMessage(json: JSONObject, fallbackCreatedAt: Long): ChatMessage? {
        val role = json.optString("role").trim()
        val body = json.optString("text")
        if (role.isEmpty() || body.isEmpty() || body == "…") return null

        val memoryJson = json.optJSONObject("memory")
        val memory = memoryJson?.let { item ->
            val status = runCatching { MemoryStatus.valueOf(item.optString("status")) }.getOrNull()
                ?: MemoryStatus.UNAVAILABLE
            val idsJson = item.optJSONArray("memory_ids") ?: JSONArray()
            val ids = buildList {
                for (index in 0 until idsJson.length()) {
                    idsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val confidence = if (item.has("confidence") && !item.isNull("confidence")) {
                item.optDouble("confidence").takeUnless { it.isNaN() }
            } else null
            ResponseMemoryMetadata(
                status = status,
                memoryIds = ids,
                confidence = confidence,
                selectedContext = item.optString("selected_context"),
                trajectoryUsed = item.optBoolean("trajectory_used", false),
                conversationWindowCount = item.optInt("conversation_window_count", 0),
            )
        }

        val generationJson = json.optJSONObject("generation")
        val generation = generationJson?.let { item ->
            val source = runCatching { ResponseSource.valueOf(item.optString("source")) }.getOrNull()
                ?: ResponseSource.LOCAL
            GenerationMetadata(
                source = source,
                modelName = item.optString("model_name").takeIf { it.isNotBlank() },
                latencyMs = if (item.has("latency_ms") && !item.isNull("latency_ms")) item.optLong("latency_ms") else null,
            )
        }

        return ChatMessage(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = role,
            text = body,
            createdAt = json.optLong("created_at", fallbackCreatedAt),
            memory = memory,
            generation = generation,
        )
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
                    messages += ChatMessage(role = role, text = text)
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
