package ia.off

enum class MemoryStatus {
    HIT,
    MISS,
    UNRESOLVED,
    UNAVAILABLE,
}

data class MemoryResolution(
    val status: MemoryStatus,
    val contextItems: List<String> = emptyList(),
    val memoryIds: List<String> = emptyList(),
    val confidence: Double? = null,
)

data class MemoryLearnResult(
    val memoryIds: List<String> = emptyList(),
)

/**
 * Android-side contract consumed by OFF.IA.
 *
 * The implementation must be supplied by Memoria.ia. OFF.IA deliberately
 * does not implement semantic memory or durable persistence here.
 */
interface MemoryGateway {
    val available: Boolean

    suspend fun resolve(message: String): MemoryResolution

    suspend fun learnTurn(userText: String, assistantText: String): MemoryLearnResult

    suspend fun flush()
}

/**
 * Explicit dependency-gated implementation used until Memoria.ia exposes
 * its Android/mobile runtime contract. It never pretends a memory operation
 * succeeded and it never stores data in a substitute database.
 */
object UnavailableMemoryGateway : MemoryGateway {
    override val available: Boolean = false

    override suspend fun resolve(message: String) =
        MemoryResolution(status = MemoryStatus.UNAVAILABLE)

    override suspend fun learnTurn(userText: String, assistantText: String) =
        MemoryLearnResult()

    override suspend fun flush() = Unit
}

fun materializePrompt(userText: String, resolution: MemoryResolution): String {
    if (resolution.contextItems.isEmpty()) return userText

    val selectedContext = resolution.contextItems.joinToString(separator = "\n") { "- $it" }
    return """
        Contexto selecionado pela Memoria.ia:
        $selectedContext

        Pergunta do usuário:
        $userText
    """.trimIndent()
}
