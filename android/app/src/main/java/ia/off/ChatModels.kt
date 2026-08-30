package ia.off

import java.util.UUID

enum class ResponseSource {
    USER,
    LOCAL,
    CURIOSITY,
    OPENAI,
    GEMINI,
    MA2A,
    SYSTEM,
}

data class ResponseMemoryMetadata(
    val status: MemoryStatus,
    val memoryIds: List<String> = emptyList(),
    val learnedMemoryIds: List<String> = emptyList(),
    val confidence: Double? = null,
    val selectedContext: String = "",
    val trajectoryUsed: Boolean = false,
    val conversationWindowCount: Int = 0,
)

data class ImprovementRecord(
    val provider: ImproveProviderKind,
    val text: String,
    val modelOrRoute: String? = null,
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GenerationMetadata(
    val source: ResponseSource = ResponseSource.LOCAL,
    val modelName: String? = null,
    val latencyMs: Long? = null,
    val publicSources: List<CuriositySource> = emptyList(),
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val memory: ResponseMemoryMetadata? = null,
    val generation: GenerationMetadata? = null,
    val improvements: List<ImprovementRecord> = emptyList(),
)
