package ia.off

internal fun externalPublicMemoryIds(sessions: Iterable<ChatSession>): Set<String> {
    val ids = linkedSetOf<String>()
    sessions.forEach { session ->
        session.messages.forEach { message ->
            val audit = message.generation?.publicKnowledge ?: return@forEach
            if (audit.knowledgeClass != "external_public") return@forEach
            audit.storedMemoryIds.filterTo(ids) { it.isNotBlank() }
        }
    }
    return ids
}

internal fun matchExternalPublicMemoryIds(
    resolvedMemoryIds: Iterable<String>,
    sessions: Iterable<ChatSession>,
): List<String> {
    val knownPublicIds = externalPublicMemoryIds(sessions)
    if (knownPublicIds.isEmpty()) return emptyList()
    val matched = linkedSetOf<String>()
    resolvedMemoryIds.forEach { id ->
        if (id.isNotBlank() && id in knownPublicIds) matched += id
    }
    return matched.toList()
}
