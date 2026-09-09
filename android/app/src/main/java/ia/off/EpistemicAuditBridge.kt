package ia.off

import java.util.concurrent.ConcurrentHashMap

/**
 * Transient bridge between the Memoria.ia gateway and the visible chat audit.
 *
 * Factual user memory IDs are durable and deterministic (`mobile:<sequence>`).
 * The ResponseValidator candidate for a normal local turn is anchored to that
 * ID (`response:<user-memory-id>`), so candidate identity can be reconstructed
 * after restart even if this transient map is lost. The bridge only preserves
 * additional diagnostics such as validation status until ChatStore saves them.
 */
data class EpistemicResponseAudit(
    val responseId: String,
    val candidateMemoryId: String,
    val validationStatus: String?,
)

object EpistemicAuditBridge {
    private val pending = ConcurrentHashMap<String, EpistemicResponseAudit>()

    fun record(userMemoryId: String, audit: EpistemicResponseAudit) {
        if (userMemoryId.isBlank()) return
        pending[userMemoryId] = audit
    }

    fun peek(userMemoryId: String): EpistemicResponseAudit? = pending[userMemoryId]

    fun consume(userMemoryId: String): EpistemicResponseAudit? = pending.remove(userMemoryId)
}
