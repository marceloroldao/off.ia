package ia.off

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NativeMemoryGateway(context: Context) : MemoryGateway, AutoCloseable {
    companion object {
        private const val DURABLE_STORAGE_ROOT = "memoria-v2"
        private const val MAX_TRAJECTORY_TURNS = 8
        private const val LOCAL_MODEL_ID = "offia-local"

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

        // Gate 2B: the legacy Android resolve surface is retained for UI stability,
        // but its implementation now consumes the Memoria-owned Context Compiler.
        // OFF.IA transports the packet and does not reinterpret its relations.
        val json = JSONObject(nativeCompileContext(requireHandle(), request.toString()))
        val status = when (json.optString("status")) {
            "HIT" -> MemoryStatus.HIT
            "MISS" -> MemoryStatus.MISS
            "UNRESOLVED" -> MemoryStatus.UNRESOLVED
            else -> MemoryStatus.UNAVAILABLE
        }
        val packet = json.optJSONObject("packet")
        val memoryIds = parseIds(packet?.optJSONArray("memory_ids"))
        val confidence = packet?.let {
            if (it.has("confidence")) it.optDouble("confidence") else Double.NaN
        } ?: Double.NaN
        val activation = packet?.optJSONObject("activation")

        MemoryResolution(
            status = status,
            contextItems = if (status == MemoryStatus.HIT && packet != null) listOf(json.toString()) else emptyList(),
            memoryIds = memoryIds,
            confidence = confidence.takeUnless { it.isNaN() },
            trajectoryUsed = activation?.optBoolean("trajectory_used", false) ?: false,
            conversationWindowCount = conversationWindow.takeLast(MAX_TRAJECTORY_TURNS).size,
        )
    }

    override suspend fun compileContext(message: String, namespace: String): CognitivePacketResult =
        withContext(Dispatchers.IO) {
            require(message.isNotBlank()) { "Pergunta vazia para Context Compiler" }
            val request = JSONObject().apply {
                put("query", message)
                put("namespace", namespace)
            }
            val raw = nativeCompileContext(requireHandle(), request.toString())
            val json = JSONObject(raw)
            val status = when (json.optString("status")) {
                "HIT" -> MemoryStatus.HIT
                "UNRESOLVED" -> MemoryStatus.UNRESOLVED
                else -> MemoryStatus.UNAVAILABLE
            }
            val packet = json.optJSONObject("packet")?.toString()
            CognitivePacketResult(status = status, packetJson = packet)
        }

    override suspend fun validateModelResponse(
        query: String,
        responseId: String,
        modelId: String,
        responseText: String,
        namespace: String,
    ): ModelResponseValidation = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Pergunta vazia para ResponseValidator" }
        require(responseId.isNotBlank()) { "response_id vazio" }
        require(modelId.isNotBlank()) { "model_id vazio" }
        require(responseText.isNotBlank()) { "Resposta vazia para ResponseValidator" }
        val request = JSONObject().apply {
            put("query", query)
            put("response_id", responseId)
            put("model_id", modelId)
            put("response_text", responseText)
            put("namespace", namespace)
        }
        val json = JSONObject(nativeValidateResponse(requireHandle(), request.toString()))
        check(!json.optBoolean("promoted", true)) { "ResponseValidator promoveu candidato automaticamente" }
        ModelResponseValidation(
            responseId = json.optString("response_id", responseId),
            candidateMemoryId = json.optString("candidate_memory_id").takeIf { it.isNotBlank() },
            consistencyStatus = json.optString("overall_status").takeIf { it.isNotBlank() },
        )
    }

    override suspend fun decideLearning(
        decisionId: String,
        candidateMemoryId: String,
        accepted: Boolean,
        validatorSource: String,
        validatorId: String,
        namespace: String,
    ): LearningDecisionResult = withContext(Dispatchers.IO) {
        require(decisionId.isNotBlank()) { "decision_id vazio" }
        require(candidateMemoryId.isNotBlank()) { "candidate_memory_id vazio" }
        require(validatorSource in setOf("USER_CONFIRMED", "SENSOR_OBSERVED")) {
            "Somente USER_CONFIRMED ou SENSOR_OBSERVED podem validar aprendizado"
        }
        require(validatorId.isNotBlank()) { "validator_id vazio" }
        val request = JSONObject().apply {
            put("decision_id", decisionId)
            put("candidate_memory_id", candidateMemoryId)
            put("accepted", accepted)
            put("validator_source", validatorSource)
            put("validator_id", validatorId)
            put("namespace", namespace)
        }
        val json = JSONObject(nativeDecideLearning(requireHandle(), request.toString()))
        LearningDecisionResult(
            decisionId = json.optString("decision_id", decisionId),
            accepted = json.optBoolean("accepted", false),
            promotedMemoryId = json.optString("learning_memory_id").takeIf {
                accepted && json.optBoolean("promoted", false) && it.isNotBlank()
            },
        )
    }

    override suspend fun learnTurn(userText: String, assistantText: String): MemoryLearnResult =
        withContext(Dispatchers.IO) {
            // The factual write remains USER-only in JNI. After that trusted write,
            // the model output crosses only the ResponseValidator quarantine path.
            val learnedJson = JSONObject(nativeLearn(requireHandle(), userText, assistantText))
            val userMemoryIds = parseIds(learnedJson.optJSONArray("memory_ids"))
            if (assistantText.isBlank()) {
                return@withContext MemoryLearnResult(memoryIds = userMemoryIds)
            }

            // The trusted user turn already has a durable deterministic id
            // (`mobile:<sequence>`). Reuse it as the response id so the candidate
            // identity can always be reconstructed after process death:
            //   mobile:42 -> response:mobile:42
            check(userMemoryIds.isNotEmpty()) { "Memoria.ia não retornou memory_id factual do usuário" }
            val responseId = userMemoryIds.first()
            val validation = validateModelResponse(
                query = userText,
                responseId = responseId,
                modelId = LOCAL_MODEL_ID,
                responseText = assistantText,
            )
            val candidate = validation.candidateMemoryId
            if (!candidate.isNullOrBlank()) {
                EpistemicAuditBridge.record(
                    responseId,
                    EpistemicResponseAudit(
                        responseId = validation.responseId,
                        candidateMemoryId = candidate,
                        validationStatus = validation.consistencyStatus,
                    ),
                )
            }
            MemoryLearnResult(
                memoryIds = userMemoryIds,
                responseId = validation.responseId,
                candidateMemoryId = validation.candidateMemoryId,
                validationStatus = validation.consistencyStatus,
            )
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
    private external fun nativeCompileContext(handle: Long, requestJson: String): String
    private external fun nativeValidateResponse(handle: Long, requestJson: String): String
    private external fun nativeDecideLearning(handle: Long, requestJson: String): String
    private external fun nativeLearn(handle: Long, user: String, assistant: String): String
    private external fun nativeLearnExternal(handle: Long, requestJson: String): String
    private external fun nativeExport(handle: Long, requestJson: String): String
    private external fun nativeFlush(handle: Long)
}
