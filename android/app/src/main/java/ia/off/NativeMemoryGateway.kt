package ia.off

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NativeMemoryGateway(
    context: Context,
    storageRoot: String = DURABLE_STORAGE_ROOT,
    private val legacyFallbackEnabled: Boolean = true,
) : MemoryGateway, AutoCloseable {
    companion object {
        private const val DURABLE_STORAGE_ROOT = "memoria-v2"
        private const val MAX_TRAJECTORY_TURNS = 8

        init {
            System.loadLibrary("offia-memory")
        }
    }

    private var handle: Long

    init {
        val storage = File(context.filesDir, storageRoot).apply { mkdirs() }
        handle = nativeOpen(storage.absolutePath)
        check(handle != 0L) { "Falha ao abrir Memoria.ia nativa" }
    }

    override val available: Boolean
        get() = handle != 0L

    override suspend fun resolve(
        message: String,
        sessionId: String?,
        conversationWindow: List<MemoryWindowTurn>,
    ): MemoryResolution = withContext(Dispatchers.IO) {
        val request = JSONObject().apply {
            put("query", message)
            if (!sessionId.isNullOrBlank() && conversationWindow.isNotEmpty()) {
                put("session_id", sessionId)
                val window = JSONArray()
                conversationWindow.takeLast(MAX_TRAJECTORY_TURNS).forEach { turn ->
                    window.put(JSONObject().apply {
                        put("session_id", sessionId)
                        put("role", turn.role)
                        put("text", turn.text)
                        put("order", turn.order)
                    })
                }
                put("conversation_window", window)
            }
        }

        val structuralRequest = JSONObject().apply {
            put("hierarchy_id", structuralHierarchy(sessionId))
            put("query", message)
            put("top_k", 3)
        }
        val structural = JSONObject(
            nativeResolveStructural(requireHandle(), structuralRequest.toString()),
        )
        if (structural.optString("status") == "HIT") {
            val contextsJson = structural.optJSONArray("contexts")
            val contexts = buildList {
                if (contextsJson != null) {
                    for (i in 0 until contextsJson.length()) {
                        val item = contextsJson.optJSONObject(i) ?: continue
                        item.optString("source_text").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            val sourceIds = buildList {
                if (contextsJson != null) {
                    for (i in 0 until contextsJson.length()) {
                        val item = contextsJson.optJSONObject(i) ?: continue
                        val ids = item.optJSONArray("source_ids")
                        if (ids != null) {
                            for (j in 0 until ids.length()) {
                                ids.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        } else {
                            item.optString("source_id").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
            }.distinct()
            // Structural score is a ranking weight, not a calibrated probability.
            // Do not expose it through the legacy confidence field.
            return@withContext MemoryResolution(
                status = MemoryStatus.HIT,
                contextItems = contexts,
                memoryIds = sourceIds,
                confidence = null,
                trajectoryUsed = false,
                conversationWindowCount = 0,
            )
        }

        if (!legacyFallbackEnabled) {
            return@withContext MemoryResolution(
                status = MemoryStatus.UNRESOLVED,
                contextItems = emptyList(),
                memoryIds = emptyList(),
                confidence = null,
                trajectoryUsed = false,
                conversationWindowCount = 0,
            )
        }

        // Migration fallback: pre-V2 RC6 memories remain queryable while new
        // user observations accumulate in the structural trail.
        val json = JSONObject(nativeResolve(requireHandle(), request.toString()))
        val status = when (json.optString("status")) {
            "HIT" -> MemoryStatus.HIT
            "MISS" -> MemoryStatus.MISS
            "UNRESOLVED" -> MemoryStatus.UNRESOLVED
            else -> MemoryStatus.UNAVAILABLE
        }
        val memoryIds = parseIds(json.optJSONArray("memory_ids"))
        val context = json.optString("selected_context")
        val confidence = if (json.has("confidence")) json.optDouble("confidence") else Double.NaN

        MemoryResolution(
            status = status,
            contextItems = if (status == MemoryStatus.HIT && context.isNotBlank()) listOf(context) else emptyList(),
            memoryIds = memoryIds,
            confidence = confidence.takeUnless { it.isNaN() },
            trajectoryUsed = json.optBoolean("trajectory_used", false),
            conversationWindowCount = json.optInt("conversation_window_count", 0),
        )
    }

    override suspend fun learnTurn(userText: String, assistantText: String): MemoryLearnResult =
        withContext(Dispatchers.IO) {
            val json = JSONObject(nativeLearn(requireHandle(), userText, assistantText))
            MemoryLearnResult(memoryIds = parseIds(json.optJSONArray("memory_ids")))
        }

    override suspend fun observeUser(
        text: String,
        sessionId: String?,
        sourceId: String,
        sequence: Long,
    ): MemoryLearnResult = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Observação do usuário vazia" }
        require(sequence >= 0L) { "Sequência estrutural inválida" }
        val effectiveSourceId = sourceId.ifBlank { "offia-user-$sequence" }
        val request = JSONObject().apply {
            put("hierarchy_id", structuralHierarchy(sessionId))
            put("source_id", effectiveSourceId)
            put("source_kind", "user_assertion")
            put("sequence", sequence)
            put("text", text)
        }
        val json = JSONObject(nativeObserveStructural(requireHandle(), request.toString()))
        check(json.optString("status") == "OK") { "Memoria.ia V2 rejeitou observação do usuário" }
        MemoryLearnResult(memoryIds = listOf(effectiveSourceId))
    }

    override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource): ExternalKnowledgeLearnResult =
        withContext(Dispatchers.IO) {
            require(source.content.isNotBlank()) { "Conhecimento público vazio" }
            require(source.sourceUrl.isNotBlank()) { "URL de origem pública vazia" }
            require(source.sourceDomain.isNotBlank()) { "Domínio de origem pública vazio" }
            require(source.sourceTitle.isNotBlank()) { "Título de origem pública vazio" }
            require(source.acquiredTime.isNotBlank()) { "Data de aquisição pública vazia" }
            require(source.validationConfidence in 0.0..1.0) { "Confiança pública deve estar entre 0 e 1" }
            require(source.importKind in setOf("imported", "synthesized", "derived")) {
                "Tipo de importação pública inválido"
            }
            if (source.importKind == "derived") {
                require(source.parentMemoryIds.isNotEmpty()) { "Conhecimento público derivado requer memória-pai" }
                require(source.parentMemoryIds.all { it.isNotBlank() }) { "ID de memória-pai público inválido" }
            } else {
                require(source.parentMemoryIds.isEmpty()) { "Somente conhecimento público derivado aceita memória-pai" }
            }

            val request = JSONObject().apply {
                put("content", source.content)
                put("source_class", "external_public")
                put("source_url", source.sourceUrl)
                put("source_domain", source.sourceDomain)
                put("source_title", source.sourceTitle)
                put("acquired_time", source.acquiredTime)
                put("source_excerpt", source.sourceExcerpt)
                put("provider_id", source.providerId)
                put("import_kind", source.importKind)
                put("validation_confidence", source.validationConfidence)
                put("request_id", source.requestId)
                put("session_id", source.sessionId)
                put("namespace", source.namespace)
                put("parent_memory_ids", JSONArray(source.parentMemoryIds))
            }

            val json = JSONObject(nativeLearnExternal(requireHandle(), request.toString()))
            val ids = parseIds(json.optJSONArray("stored_memory_ids"))
            check(ids.isNotEmpty()) { "Memoria.ia não retornou memory_id para conhecimento público" }
            ExternalKnowledgeLearnResult(
                memoryIds = ids,
                deduplicated = json.optBoolean("deduplicated", false),
                sourceAttached = json.optBoolean("source_attached", false),
                sourceCount = json.optInt("source_count", 0),
                sourceType = json.optString("source_type").takeIf { it.isNotBlank() },
            )
        }

    override suspend fun exportSnapshotPage(
        turnOffset: Int,
        episodeOffset: Int,
        limit: Int,
    ): String = withContext(Dispatchers.IO) {
        require(turnOffset >= 0 && episodeOffset >= 0) { "Offsets de exportação inválidos" }
        require(limit in 1..64) { "Limite de exportação deve estar entre 1 e 64" }
        val request = JSONObject().apply {
            put("turn_offset", turnOffset)
            put("turn_limit", limit)
            put("episode_offset", episodeOffset)
            put("episode_limit", limit)
        }
        nativeExport(requireHandle(), request.toString())
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

    private fun structuralHierarchy(sessionId: String?): String =
        "conversation:" + sessionId?.takeIf { it.isNotBlank() }.orEmpty().ifBlank { "default" }

    private fun parseIds(array: JSONArray?): List<String> = buildList {
        if (array != null) {
            for (i in 0 until array.length()) {
                val id = array.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
    }

    private external fun nativeOpen(path: String): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeResolve(handle: Long, requestJson: String): String
    private external fun nativeResolveStructural(handle: Long, requestJson: String): String
    private external fun nativeObserveStructural(handle: Long, requestJson: String): String
    private external fun nativeLearn(handle: Long, user: String, assistant: String): String
    private external fun nativeLearnExternal(handle: Long, requestJson: String): String
    private external fun nativeExport(handle: Long, requestJson: String): String
    private external fun nativeFlush(handle: Long)
}
