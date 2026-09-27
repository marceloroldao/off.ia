package ia.off

/** A reply exists only when the person chooses an earlier user entry in this chat. */
internal fun selectedReplyTarget(
    messages: List<ChatMessage>,
    selected: ChatMessage?,
    newSequence: Long,
): ExplicitReplyTarget? {
    val target = selected ?: return null
    if (target.role != "Você" || target.createdAt >= newSequence) return null
    val existing = messages.firstOrNull { it.id == target.id && it.role == "Você" }
    if (existing?.createdAt != target.createdAt) return null
    return ExplicitReplyTarget(existing.id, existing.createdAt)
}

/** Millisecond timestamps are source addresses, so two quick sends must not tie. */
internal fun nextUserSequence(messages: List<ChatMessage>, clockMillis: Long): Long =
    maxOf(clockMillis, (messages.filter { it.role == "Você" }.maxOfOrNull { it.createdAt } ?: -1L) + 1L)

/** Retry only durable, user-selected intentions after a process interruption. */
internal suspend fun recoverExplicitReplies(
    memory: MemoryGateway,
    sessionId: String,
    messages: MutableList<ChatMessage>,
): Int {
    if (!memory.available) return 0
    var recovered = 0
    for (index in messages.indices) {
        val item = messages[index]
        val target = item.replyTo ?: continue
        if (item.role != "Você" || item.replyRecorded ||
            messages.none { it.id == target.sourceId && it.role == "Você" &&
                it.createdAt == target.sequence && it.createdAt < item.createdAt }) continue
        val linked = runCatching {
            memory.linkUserReply(sessionId, item.id, item.createdAt, target)
        }.getOrDefault(false)
        if (linked) {
            memory.flush()
            messages[index] = item.copy(replyRecorded = true)
            recovered++
        }
    }
    return recovered
}
