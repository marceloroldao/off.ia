package ia.off

import java.net.URI
import java.time.Instant
import java.util.LinkedHashMap

data class CuriosityMemoryLearningReport(
    val sourceMemoryIds: List<String> = emptyList(),
    val storedMemoryIds: List<String> = emptyList(),
    val failedSourceCount: Int = 0,
    val synthesisStored: Boolean = false,
    val flushFailed: Boolean = false,
    val failureReason: String? = null,
) {
    val learned: Boolean
        get() = storedMemoryIds.isNotEmpty()

    fun toPublicKnowledgeAudit() = PublicKnowledgeAudit(
        sourceMemoryIds = sourceMemoryIds,
        storedMemoryIds = storedMemoryIds,
        synthesisStored = synthesisStored,
        failedSourceCount = failedSourceCount,
        flushFailed = flushFailed,
    )
}

/**
 * Small process-local presentation cache for the Curiosity audit returned by
 * Memoria.ia. It is not authoritative memory: Memoria.ia + BDR own memory and
 * ChatStore owns the durable UI transcript copy.
 */
internal object CuriosityPublicAuditBridge {
    private const val MAX_ENTRIES = 128
    private val pending = LinkedHashMap<String, PublicKnowledgeAudit>()

    @Synchronized
    fun record(responseId: String, audit: PublicKnowledgeAudit) {
        if (responseId.isBlank()) return
        pending.remove(responseId)
        pending[responseId] = audit
        while (pending.size > MAX_ENTRIES) {
            val eldest = pending.entries.firstOrNull()?.key ?: break
            pending.remove(eldest)
        }
    }

    @Synchronized
    fun peek(responseId: String): PublicKnowledgeAudit? = pending[responseId]

    @Synchronized
    fun clearForTests() = pending.clear()
}

private data class NormalizedPublicSource(
    val content: String,
    val url: String,
    val domain: String,
    val title: String,
    val excerpt: String,
)

/**
 * Fail closed before crossing the mobile ABI. MediaWiki normally returns an
 * absolute `fullurl`, but consumers/providers may also return protocol-relative
 * URLs. Memoria.ia deliberately requires http(s) provenance and a domain, so
 * normalize those values from the URL itself rather than trusting duplicated UI
 * metadata.
 */
private fun normalizePublicSource(source: CuriositySource): NormalizedPublicSource {
    val raw = source.rawContent?.trim().orEmpty()
    val excerpt = source.excerpt?.trim().orEmpty()
    val content = raw.ifBlank { excerpt }
    require(content.isNotBlank()) { "Fonte pública sem conteúdo utilizável" }

    val candidateUrl = source.url.trim().let { value ->
        when {
            value.startsWith("//") -> "https:$value"
            else -> value
        }
    }
    require(candidateUrl.isNotBlank()) { "Fonte pública sem URL" }

    val uri = runCatching { URI(candidateUrl) }
        .getOrElse { throw IllegalArgumentException("URL pública inválida: ${candidateUrl.take(120)}") }
    val scheme = uri.scheme?.lowercase().orEmpty()
    require(scheme == "http" || scheme == "https") {
        "Fonte pública exige URL http(s)"
    }
    val host = uri.host?.trim()?.lowercase().orEmpty()
    require(host.isNotBlank()) { "Fonte pública sem domínio válido" }

    val title = source.title.trim().ifBlank { host }
    require(title.isNotBlank()) { "Fonte pública sem título" }

    return NormalizedPublicSource(
        content = content,
        url = uri.toASCIIString(),
        domain = host,
        title = title,
        excerpt = excerpt.ifBlank { content.take(1_200) }.take(1_200),
    )
}

/**
 * Persists public source evidence before any LLM rendering.
 *
 * The full plaintext content actually read by the Curiosity provider is the
 * authoritative imported payload. The short excerpt is only presentation /
 * prompt material. LLM synthesis is intentionally not persisted here.
 */
suspend fun learnCuriositySources(
    memory: MemoryGateway,
    result: CuriosityResult,
    sessionId: String,
    requestId: String,
    acquiredTime: String = Instant.now().toString(),
): CuriosityMemoryLearningReport {
    if (!memory.available) return CuriosityMemoryLearningReport()

    val timestamp = acquiredTime.trim()
    require(timestamp.isNotBlank()) { "Curiosidade sem acquired_time" }

    val sourceIds = linkedSetOf<String>()
    val storedIds = linkedSetOf<String>()
    var failedSources = 0
    var firstFailureReason: String? = null

    result.sources.forEachIndexed { index, source ->
        try {
            val normalized = normalizePublicSource(source)
            val learned = memory.learnExternalKnowledge(
                ExternalKnowledgeSource(
                    content = normalized.content,
                    sourceUrl = normalized.url,
                    sourceDomain = normalized.domain,
                    sourceTitle = normalized.title,
                    acquiredTime = timestamp,
                    sourceExcerpt = normalized.excerpt,
                    providerId = "wikipedia-curiosity",
                    importKind = "imported",
                    validationConfidence = 0.85,
                    requestId = "$requestId:source:$index",
                    sessionId = sessionId,
                ),
            )
            if (learned.memoryIds.isNotEmpty()) {
                sourceIds += learned.memoryIds
                storedIds += learned.memoryIds
            }
        } catch (e: Exception) {
            failedSources += 1
            if (firstFailureReason == null) {
                firstFailureReason = e.message ?: e.javaClass.simpleName
            }
        }
    }

    var flushFailed = false
    if (storedIds.isNotEmpty()) {
        try {
            memory.flush()
        } catch (_: Exception) {
            flushFailed = true
        }
    }

    val report = CuriosityMemoryLearningReport(
        sourceMemoryIds = sourceIds.toList(),
        storedMemoryIds = storedIds.toList(),
        failedSourceCount = failedSources,
        synthesisStored = false,
        flushFailed = flushFailed,
        failureReason = firstFailureReason,
    )
    CuriosityPublicAuditBridge.record(requestId, report.toPublicKnowledgeAudit())
    return report
}

/**
 * Transitional compatibility wrapper. Synthesis is deliberately ignored:
 * llama.cpp output is UI rendering, not authoritative external_public memory.
 */
suspend fun learnCuriosityResult(
    memory: MemoryGateway,
    result: CuriosityResult,
    synthesis: String,
    sessionId: String,
    requestId: String,
    acquiredTime: String = Instant.now().toString(),
): CuriosityMemoryLearningReport {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSynthesis = synthesis
    return learnCuriositySources(
        memory = memory,
        result = result,
        sessionId = sessionId,
        requestId = requestId,
        acquiredTime = acquiredTime,
    )
}
