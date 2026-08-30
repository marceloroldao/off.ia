package ia.off

import java.time.Instant

data class CuriosityMemoryLearningReport(
    val sourceMemoryIds: List<String> = emptyList(),
    val storedMemoryIds: List<String> = emptyList(),
    val failedSourceCount: Int = 0,
    val synthesisStored: Boolean = false,
    val flushFailed: Boolean = false,
) {
    val learned: Boolean
        get() = storedMemoryIds.isNotEmpty()
}

suspend fun learnCuriosityResult(
    memory: MemoryGateway,
    result: CuriosityResult,
    synthesis: String,
    sessionId: String,
    requestId: String,
    acquiredTime: String = Instant.now().toString(),
): CuriosityMemoryLearningReport {
    if (!memory.available) return CuriosityMemoryLearningReport()

    val sourceIds = linkedSetOf<String>()
    val storedIds = linkedSetOf<String>()
    var primaryLearnedSource: CuriositySource? = null
    var failedSources = 0

    result.sources.forEachIndexed { index, source ->
        val excerpt = source.excerpt?.trim().orEmpty()
        if (excerpt.isBlank()) return@forEachIndexed
        try {
            val learned = memory.learnExternalKnowledge(
                ExternalKnowledgeSource(
                    content = excerpt,
                    sourceUrl = source.url,
                    sourceDomain = source.domain,
                    sourceTitle = source.title,
                    acquiredTime = acquiredTime,
                    sourceExcerpt = excerpt.take(1_200),
                    providerId = "wikipedia-curiosity",
                    importKind = "imported",
                    validationConfidence = 0.85,
                    requestId = "$requestId:source:$index",
                    sessionId = sessionId,
                ),
            )
            if (learned.memoryIds.isNotEmpty()) {
                if (primaryLearnedSource == null) primaryLearnedSource = source
                sourceIds += learned.memoryIds
                storedIds += learned.memoryIds
            }
        } catch (_: Exception) {
            failedSources += 1
        }
    }

    var synthesisStored = false
    val synthesized = synthesis.trim()
    val primarySource = primaryLearnedSource
    if (synthesized.isNotBlank() && primarySource != null && sourceIds.isNotEmpty()) {
        try {
            val learned = memory.learnExternalKnowledge(
                ExternalKnowledgeSource(
                    content = synthesized,
                    sourceUrl = primarySource.url,
                    sourceDomain = primarySource.domain,
                    sourceTitle = primarySource.title,
                    acquiredTime = acquiredTime,
                    sourceExcerpt = primarySource.excerpt.orEmpty().trim().take(1_200),
                    providerId = "offia-curiosity",
                    importKind = "derived",
                    validationConfidence = 0.80,
                    requestId = "$requestId:synthesis",
                    sessionId = sessionId,
                    parentMemoryIds = sourceIds.toList(),
                ),
            )
            storedIds += learned.memoryIds
            synthesisStored = learned.memoryIds.isNotEmpty()
        } catch (_: Exception) {
            // Imported public sources remain valid even if Memoria.ia rejects
            // a derived synthesis conservatively.
        }
    }

    var flushFailed = false
    if (storedIds.isNotEmpty()) {
        try {
            memory.flush()
        } catch (_: Exception) {
            // Curiosity itself remains usable. The UI can report that durable
            // synchronization needs attention without discarding the answer.
            flushFailed = true
        }
    }

    return CuriosityMemoryLearningReport(
        sourceMemoryIds = sourceIds.toList(),
        storedMemoryIds = storedIds.toList(),
        failedSourceCount = failedSources,
        synthesisStored = synthesisStored,
        flushFailed = flushFailed,
    )
}
