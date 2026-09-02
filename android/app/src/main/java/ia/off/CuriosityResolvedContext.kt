package ia.off

data class CuriosityResolvedContext(
    val learning: CuriosityMemoryLearningReport,
    val resolution: MemoryResolution,
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
    val learning = learnCuriositySources(
        memory = memory,
        result = result,
        sessionId = sessionId,
        requestId = requestId,
    )

    if (!learning.learned || learning.failedSourceCount > 0 || learning.flushFailed) {
        return CuriosityResolvedContext(
            learning = learning,
            resolution = MemoryResolution(status = MemoryStatus.UNRESOLVED),
        )
    }

    val resolution = memory.resolve(
        message = userQuestion,
        sessionId = sessionId,
        conversationWindow = emptyList(),
    )
    return CuriosityResolvedContext(learning = learning, resolution = resolution)
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
