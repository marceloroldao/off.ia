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

data class ChatInteraction(
    val userQuestion: String,
    val response: ChatMessage,
)

/**
 * Durable UI transcript workspace.
 *
 * This remains separate from Memoria.ia. Sessions preserve visible chat history,
 * generation provenance and response diagnostics; semantic learning/retrieval
 * remains owned by Memoria.ia + BDR.
 */
class ChatStore(context: Context) {
    companion object {
        private const val SCHEMA_VERSION = 4
        private val READABLE_SCHEMA_VERSIONS = setOf(2, 3, SCHEMA_VERSION)
        private const val FILE_NAME = "chat-workspace-v2.json"
        private const val LEGACY_FILE_NAME = "chat-history-v1.json"
        private const val MAX_MESSAGES_PER_SESSION = 2000
        private const val MAX_SESSIONS = 100
        private const val MAX_IMPROVEMENTS_PER_MESSAGE = 5
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)

    fun load(): ChatWorkspace {
        if (file.isFile) {
            runCatching { parseWorkspace(file.readText(Charsets.UTF_8)) }.getOrNull()?.let { workspace ->
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
    fun findInteraction(responseId: String): ChatInteraction? {
        val workspace = readWorkspaceWithoutRewrite() ?: return null
        workspace.sessions.forEach { session ->
            val responseIndex = session.messages.indexOfFirst { it.id == responseId }
            if (responseIndex < 0) return@forEach
            val response = session.messages[responseIndex]
            if (response.role == "Você") return null
            val userIndex = (responseIndex - 1 downTo 0).firstOrNull { session.messages[it].role == "Você" }
                ?: return null
            return ChatInteraction(
                userQuestion = session.messages[userIndex].text,
                response = response,
            )
        }
        return null
    }

    @Synchronized
    fun appendImprovement(responseId: String, record: ImprovementRecord): Boolean {
        val workspace = readWorkspaceWithoutRewrite() ?: return false
        workspace.sessions.forEach { session ->
            val index = session.messages.indexOfFirst { it.id == responseId }
            if (index < 0) return@forEach
            val current = session.messages[index]
            session.messages[index] = current.copy(
                improvements = (current.improvements + record).takeLast(MAX_IMPROVEMENTS_PER_MESSAGE),
            )
            session.updatedAt = System.currentTimeMillis()
            save(workspace)
            return true
        }
        return false
    }

    @Synchronized
    fun save(workspace: ChatWorkspace) {
        mergeTransientCuriosityAudits(workspace)
        mergeStoredImprovements(workspace)

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

    private fun mergeTransientCuriosityAudits(workspace: ChatWorkspace) {
        workspace.sessions.forEach { session ->
            session.messages.indices.forEach { index ->
                val message = session.messages[index]
                val generation = message.generation ?: return@forEach
                if (generation.source != ResponseSource.CURIOSITY) return@forEach
                val pending = CuriosityPublicAuditBridge.take(message.id) ?: return@forEach
                if (generation.publicKnowledge == null) {
                    session.messages[index] = message.copy(
                        generation = generation.copy(publicKnowledge = pending),
                    )
                }
            }
        }
    }

    private fun mergeStoredImprovements(workspace: ChatWorkspace) {
        val stored = readWorkspaceWithoutRewrite() ?: return
        val improvementsById = stored.sessions
            .asSequence()
            .flatMap { it.messages.asSequence() }
            .filter { it.improvements.isNotEmpty() }
            .associate { it.id to it.improvements }

        if (improvementsById.isEmpty()) return
        workspace.sessions.forEach { session ->
            session.messages.indices.forEach { index ->
                val message = session.messages[index]
                val storedImprovements = improvementsById[message.id].orEmpty()
                if (storedImprovements.isNotEmpty()) {
                    val merged = (message.improvements + storedImprovements)
                        .distinctBy { improvement ->
                            listOf(
                                improvement.provider.name,
                                improvement.modelOrRoute.orEmpty(),
                                improvement.text,
                                improvement.createdAt.toString(),
                            ).joinToString("\u0000")
                        }
                        .takeLast(MAX_IMPROVEMENTS_PER_MESSAGE)
                    session.messages[index] = message.copy(improvements = merged)
                }
            }
        }
    }

    private fun readWorkspaceWithoutRewrite(): ChatWorkspace? {
        if (!file.isFile) return null
        return runCatching { parseWorkspace(file.readText(Charsets.UTF_8)) }.getOrNull()
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
                put("learned_memory_ids", JSONArray(memory.learnedMemoryIds))
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
                if (generation.publicSources.isNotEmpty()) {
                    put("public_sources", JSONArray().apply {
                        generation.publicSources.forEach { source ->
                            put(JSONObject().apply {
                                put("title", source.title)
                                put("url", source.url)
                                put("domain", source.domain)
                                source.excerpt?.let { put("excerpt", it) }
                            })
                        }
                    })
                }
                generation.publicKnowledge?.let { audit ->
                    put("public_knowledge", JSONObject().apply {
                        put("knowledge_class", audit.knowledgeClass)
                        put("source_memory_ids", JSONArray(audit.sourceMemoryIds))
                        put("stored_memory_ids", JSONArray(audit.storedMemoryIds))
                        put("synthesis_stored", audit.synthesisStored)
                        put("failed_source_count", audit.failedSourceCount)
                        put("flush_failed", audit.flushFailed)
                    })
                }
            })
        }

        if (message.improvements.isNotEmpty()) {
            put("improvements", JSONArray().apply {
                message.improvements.takeLast(MAX_IMPROVEMENTS_PER_MESSAGE).forEach { improvement ->
                    put(JSONObject().apply {
                        put("provider", improvement.provider.name)
                        put("text", improvement.text)
                        improvement.modelOrRoute?.let { put("model_or_route", it) }
                        improvement.latencyMs?.let { put("latency_ms", it) }
                        put("created_at", improvement.createdAt)
                    })
                }
            })
        }
    }

    private fun parseWorkspace(text: String): ChatWorkspace {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schema_version", -1)
        require(schemaVersion in READABLE_SCHEMA_VERSIONS)

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
            val ids = jsonStringList(item.optJSONArray("memory_ids"))
            val learnedIds = jsonStringList(item.optJSONArray("learned_memory_ids"))
            val confidence = if (item.has("confidence") && !item.isNull("confidence")) {
                item.optDouble("confidence").takeUnless { it.isNaN() }
            } else null
            ResponseMemoryMetadata(
                status = status,
                memoryIds = ids,
                learnedMemoryIds = learnedIds,
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
            val publicSources = buildList {
                val array = item.optJSONArray("public_sources") ?: return@buildList
                for (index in 0 until array.length()) {
                    val sourceJson = array.optJSONObject(index) ?: continue
                    val title = sourceJson.optString("title").trim()
                    val url = sourceJson.optString("url").trim()
                    val domain = sourceJson.optString("domain").trim()
                    if (title.isBlank() || url.isBlank()) continue
                    add(
                        CuriositySource(
                            title = title,
                            url = url,
                            domain = domain.ifBlank { "fonte pública" },
                            excerpt = sourceJson.optString("excerpt").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            val publicKnowledge = item.optJSONObject("public_knowledge")?.let { audit ->
                PublicKnowledgeAudit(
                    knowledgeClass = audit.optString("knowledge_class")
                        .takeIf { it == "external_public" }
                        ?: "external_public",
                    sourceMemoryIds = jsonStringList(audit.optJSONArray("source_memory_ids")).distinct(),
                    storedMemoryIds = jsonStringList(audit.optJSONArray("stored_memory_ids")).distinct(),
                    synthesisStored = audit.optBoolean("synthesis_stored", false),
                    failedSourceCount = audit.optInt("failed_source_count", 0).coerceAtLeast(0),
                    flushFailed = audit.optBoolean("flush_failed", false),
                )
            }
            GenerationMetadata(
                source = source,
                modelName = item.optString("model_name").takeIf { it.isNotBlank() },
                latencyMs = if (item.has("latency_ms") && !item.isNull("latency_ms")) item.optLong("latency_ms") else null,
                publicSources = publicSources,
                publicKnowledge = publicKnowledge,
            )
        }

        val improvements = buildList {
            val array = json.optJSONArray("improvements") ?: return@buildList
            val start = (array.length() - MAX_IMPROVEMENTS_PER_MESSAGE).coerceAtLeast(0)
            for (index in start until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val provider = runCatching {
                    ImproveProviderKind.valueOf(item.optString("provider"))
                }.getOrNull() ?: continue
                val text = item.optString("text")
                if (text.isBlank()) continue
                add(
                    ImprovementRecord(
                        provider = provider,
                        text = text,
                        modelOrRoute = item.optString("model_or_route").takeIf { it.isNotBlank() },
                        latencyMs = if (item.has("latency_ms") && !item.isNull("latency_ms")) item.optLong("latency_ms") else null,
                        createdAt = item.optLong("created_at", fallbackCreatedAt),
                    ),
                )
            }
        }

        return ChatMessage(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = role,
            text = body,
            createdAt = json.optLong("created_at", fallbackCreatedAt),
            memory = memory,
            generation = generation,
            improvements = improvements,
        )
    }

    private fun jsonStringList(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
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
