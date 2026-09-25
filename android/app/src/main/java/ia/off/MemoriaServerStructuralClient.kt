package ia.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supplies a short-lived Memoria.ia Server Device token.
 *
 * The structural client never owns enrollment credentials or a server admin
 * key. Token acquisition/refresh is a separate device-identity responsibility.
 */
fun interface DeviceTokenProvider {
    suspend fun token(): String
}

data class ServerStructuralObserveResult(
    val stored: Boolean,
    val duplicate: Boolean,
    val observationId: String?,
    val symbolCount: Int,
    val semanticProjection: Boolean,
)

data class ServerStructuralContext(
    val sourceText: String,
    val score: Double,
    val exactOverlap: Int,
    val associationMass: Double,
    val observationIds: List<String>,
)

data class ServerStructuralResolveResult(
    val status: String,
    val contexts: List<ServerStructuralContext>,
    val scannedObservations: Int,
    val querySymbolCount: Int,
    val semanticProjection: Boolean,
)

internal data class StructuralObserveRequest(
    val text: String,
    val sequence: Long,
    val sessionId: String,
)

internal data class StructuralResolveRequest(
    val query: String,
    val limit: Int,
    val maxScan: Int,
)

internal interface StructuralMemoryTransport {
    suspend fun observe(
        baseUrl: String,
        deviceToken: String,
        request: StructuralObserveRequest,
    ): ServerStructuralObserveResult

    suspend fun resolve(
        baseUrl: String,
        deviceToken: String,
        request: StructuralResolveRequest,
    ): ServerStructuralResolveResult
}

internal class HttpStructuralMemoryTransport : StructuralMemoryTransport {
    override suspend fun observe(
        baseUrl: String,
        deviceToken: String,
        request: StructuralObserveRequest,
    ): ServerStructuralObserveResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("text", request.text)
            .put("sequence", request.sequence)
            .put("session_id", request.sessionId)

        val root = post(
            url = baseUrl + "/api/server/v1/device/memory/structural/observe",
            deviceToken = deviceToken,
            payload = payload,
            expectedCodes = setOf(200, 201),
        )
        ServerStructuralObserveResult(
            stored = root.optBoolean("stored", false),
            duplicate = root.optBoolean("duplicate", false),
            observationId = root.optString("observation_id").takeIf { it.isNotBlank() },
            symbolCount = root.optInt("symbol_count", 0),
            semanticProjection = root.optBoolean("semantic_projection", true),
        )
    }

    override suspend fun resolve(
        baseUrl: String,
        deviceToken: String,
        request: StructuralResolveRequest,
    ): ServerStructuralResolveResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("query", request.query)
            .put("limit", request.limit)
            .put("max_scan", request.maxScan)

        val root = post(
            url = baseUrl + "/api/server/v1/device/memory/structural/resolve",
            deviceToken = deviceToken,
            payload = payload,
            expectedCodes = setOf(200),
        )
        val contextsJson = root.optJSONArray("contexts")
        val contexts = buildList {
            if (contextsJson != null) {
                for (index in 0 until contextsJson.length()) {
                    val item = contextsJson.optJSONObject(index) ?: continue
                    val idsJson = item.optJSONArray("observation_ids")
                    val ids = buildList {
                        if (idsJson != null) {
                            for (idIndex in 0 until idsJson.length()) {
                                val value = idsJson.optString(idIndex).trim()
                                if (value.isNotBlank()) add(value)
                            }
                        }
                    }
                    add(
                        ServerStructuralContext(
                            sourceText = item.optString("source_text"),
                            score = item.optDouble("score", 0.0),
                            exactOverlap = item.optInt("exact_overlap", 0),
                            associationMass = item.optDouble("association_mass", 0.0),
                            observationIds = ids,
                        ),
                    )
                }
            }
        }
        ServerStructuralResolveResult(
            status = root.optString("status", "UNRESOLVED"),
            contexts = contexts,
            scannedObservations = root.optInt("scanned_observations", 0),
            querySymbolCount = root.optInt("query_symbol_count", 0),
            semanticProjection = root.optBoolean("semantic_projection", true),
        )
    }

    private fun post(
        url: String,
        deviceToken: String,
        payload: JSONObject,
        expectedCodes: Set<Int>,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Device $deviceToken")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in expectedCodes) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in expectedCodes) {
                val detail = runCatching {
                    JSONObject(body).optString("detail").ifBlank {
                        JSONObject(body).optString("error")
                    }
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "Memoria.ia Server rejeitou a operação estrutural (HTTP $code)" +
                        detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                )
            }
            JSONObject(body.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * OFF.IA-side client for the device-scoped structural text memory gate.
 *
 * This is deliberately separate from the local MemoryGateway/RC6 path. It does
 * not replace local memory yet and it never receives hierarchy_id/source_id:
 * those ownership fields are derived by Memoria.ia Server from the authenticated
 * device identity.
 */
interface StructuralTextMemoryClient {
    suspend fun observeUserText(
        text: String,
        sequence: Long,
        sessionId: String,
    ): ServerStructuralObserveResult

    suspend fun resolve(
        query: String,
        limit: Int = 3,
        maxScan: Int = 2048,
    ): ServerStructuralResolveResult
}

class MemoriaServerStructuralClient internal constructor(
    serverBaseUrl: String,
    private val tokenProvider: DeviceTokenProvider,
    private val transport: StructuralMemoryTransport,
) : StructuralTextMemoryClient {
    constructor(
        serverBaseUrl: String,
        tokenProvider: DeviceTokenProvider,
    ) : this(serverBaseUrl, tokenProvider, HttpStructuralMemoryTransport())

    private val baseUrl = serverBaseUrl.trim().trimEnd('/').also {
        require(it.startsWith("http://") || it.startsWith("https://")) {
            "Memoria.ia Server URL deve usar http(s)"
        }
    }

    override suspend fun observeUserText(
        text: String,
        sequence: Long,
        sessionId: String,
    ): ServerStructuralObserveResult {
        val normalizedText = text.trim()
        require(normalizedText.isNotBlank()) { "Texto estrutural vazio" }
        require(normalizedText.length <= 20_000) { "Texto estrutural excede 20000 caracteres" }
        require(sequence >= 0) { "sequence deve ser >= 0" }
        require(sessionId.length <= 256) { "sessionId excede 256 caracteres" }

        return transport.observe(
            baseUrl = baseUrl,
            deviceToken = token(),
            request = StructuralObserveRequest(
                text = normalizedText,
                sequence = sequence,
                sessionId = sessionId,
            ),
        ).also { result ->
            require(!result.semanticProjection) {
                "Servidor estrutural retornou projeção semântica inesperada"
            }
        }
    }

    override suspend fun resolve(
        query: String,
        limit: Int,
        maxScan: Int,
    ): ServerStructuralResolveResult {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "Consulta estrutural vazia" }
        require(normalizedQuery.length <= 4_000) { "Consulta estrutural excede 4000 caracteres" }
        require(limit in 1..20) { "limit deve estar entre 1 e 20" }
        require(maxScan in 1..100_000) { "maxScan deve estar entre 1 e 100000" }

        return transport.resolve(
            baseUrl = baseUrl,
            deviceToken = token(),
            request = StructuralResolveRequest(
                query = normalizedQuery,
                limit = limit,
                maxScan = maxScan,
            ),
        ).also { result ->
            require(!result.semanticProjection) {
                "Servidor estrutural retornou projeção semântica inesperada"
            }
        }
    }

    private suspend fun token(): String =
        tokenProvider.token().trim().also {
            require(it.isNotBlank()) { "Device token indisponível" }
        }
}


fun ServerStructuralResolveResult.toMemoryResolution(): MemoryResolution {
    val mappedStatus = when (status.uppercase()) {
        "HIT" -> MemoryStatus.HIT
        "MISS" -> MemoryStatus.MISS
        "UNRESOLVED" -> MemoryStatus.UNRESOLVED
        else -> MemoryStatus.UNRESOLVED
    }
    val selectedContexts = contexts
        .map { it.sourceText.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val ids = contexts
        .flatMap { it.observationIds }
        .filter { it.isNotBlank() }
        .distinct()
    return MemoryResolution(
        status = if (mappedStatus == MemoryStatus.HIT && selectedContexts.isEmpty()) {
            MemoryStatus.UNRESOLVED
        } else {
            mappedStatus
        },
        contextItems = selectedContexts,
        memoryIds = ids,
        // Association score ranks contexts; it is not a calibrated probability.
        confidence = null,
        trajectoryUsed = false,
        conversationWindowCount = 0,
    )
}


internal fun selectLaboratoryMemoryResolution(
    local: MemoryResolution,
    structural: MemoryResolution?,
): MemoryResolution =
    if (structural?.status == MemoryStatus.HIT && structural.contextItems.isNotEmpty()) {
        structural
    } else {
        local
    }


data class StructuralTurnGateResult(
    val resolution: MemoryResolution?,
    val observed: Boolean,
)

internal suspend fun resolveThenObserveUserText(
    client: StructuralTextMemoryClient?,
    userText: String,
    sequence: Long,
    sessionId: String,
): StructuralTurnGateResult {
    if (client == null) {
        return StructuralTurnGateResult(resolution = null, observed = false)
    }
    val resolution = runCatching {
        client.resolve(userText).toMemoryResolution()
    }.getOrNull()

    // Observation intentionally happens only after resolve has completed.
    // Therefore the current query cannot reinforce its own lookup.
    val observed = runCatching {
        client.observeUserText(
            text = userText,
            sequence = sequence,
            sessionId = sessionId,
        )
    }.isSuccess

    return StructuralTurnGateResult(
        resolution = resolution,
        observed = observed,
    )
}
