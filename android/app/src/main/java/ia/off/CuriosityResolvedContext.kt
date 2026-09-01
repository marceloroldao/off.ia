package ia.off

data class CuriosityResolvedContext(
    val learning: CuriosityMemoryLearningReport,
    val resolution: MemoryResolution,
    val persistenceLatencyMs: Long = 0L,
    val resolutionLatencyMs: Long = 0L,
) {
    val readyForRendering: Boolean
        get() = learning.learned &&
            learning.failedSourceCount == 0 &&
            !learning.flushFailed &&
            resolution.status == MemoryStatus.HIT &&
            resolution.contextItems.isNotEmpty()
}

suspend fun learnAndResolveCuriosity(
    memory: MemoryGateway,
    result: CuriosityResult,
    userQuestion: String,
    sessionId: String,
    requestId: String,
): CuriosityResolvedContext {
    val persistenceStartedAt = System.nanoTime()
    val learning = learnCuriositySources(
        memory = memory,
        result = result,
        sessionId = sessionId,
        requestId = requestId,
    )
    val persistenceLatencyMs = (System.nanoTime() - persistenceStartedAt) / 1_000_000L

    if (!learning.learned || learning.failedSourceCount > 0 || learning.flushFailed) {
        return CuriosityResolvedContext(
            learning = learning,
            resolution = MemoryResolution(status = MemoryStatus.UNRESOLVED),
            persistenceLatencyMs = persistenceLatencyMs,
        )
    }

    val resolutionStartedAt = System.nanoTime()
    val resolution = memory.resolve(
        message = userQuestion,
        sessionId = sessionId,
        conversationWindow = emptyList(),
    )
    val resolutionLatencyMs = (System.nanoTime() - resolutionStartedAt) / 1_000_000L
    return CuriosityResolvedContext(
        learning = learning,
        resolution = resolution,
        persistenceLatencyMs = persistenceLatencyMs,
        resolutionLatencyMs = resolutionLatencyMs,
    )
}

fun materializeResolvedCuriosityPrompt(
    userQuestion: String,
    resolution: MemoryResolution,
): String {
    require(resolution.status == MemoryStatus.HIT) {
        "Curiosity requires a Memoria.ia HIT before llama.cpp rendering"
    }
    require(resolution.contextItems.isNotEmpty()) {
        "Curiosity requires Memoria.ia-selected context before llama.cpp rendering"
    }
    return materializePrompt(userQuestion, resolution)
}

fun formatCuriosityLatencyMs(value: Long): String = when {
    value < 1_000L -> "${value}ms"
    else -> String.format(java.util.Locale.US, "%.1fs", value / 1_000.0)
}
