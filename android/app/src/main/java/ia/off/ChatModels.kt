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
    // Epistemic audit metadata returned by Memoria.ia's ResponseValidator.
    // These identifiers do not make model output factual. They only preserve
    // the candidate boundary needed for a later explicit Learning Gate decision.
    val responseId: String? = null,
    val candidateMemoryId: String? = null,
    val validationStatus: String? = null,
    // Explicit Learning Gate outcome. Null means no decision was made.
    val learningDecisionId: String? = null,
    val learningAccepted: Boolean? = null,
    val promotedMemoryId: String? = null,
)

data class ImprovementRecord(
    val provider: ImproveProviderKind,
    val text: String,
    val modelOrRoute: String? = null,
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PublicKnowledgeAudit(
    val knowledgeClass: String = "external_public",
    val sourceMemoryIds: List<String> = emptyList(),
    val storedMemoryIds: List<String> = emptyList(),
    val synthesisStored: Boolean = false,
    val failedSourceCount: Int = 0,
    val flushFailed: Boolean = false,
)

data class GenerationMetadata(
    val source: ResponseSource = ResponseSource.LOCAL,
    val modelName: String? = null,
    val latencyMs: Long? = null,
    val publicSources: List<CuriositySource> = emptyList(),
    val publicKnowledge: PublicKnowledgeAudit? = null,
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
