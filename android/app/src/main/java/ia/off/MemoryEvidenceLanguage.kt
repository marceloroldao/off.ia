package ia.off

/**
 * Language boundary for the current mobile memory contract. A structural HIT
 * identifies related records, not a verified answer. Keep their original words
 * visible and never turn a question or a model output into an asserted fact.
 * Cognitive inference and evidence qualification belong in Memoria.ia.
 */
fun renderMemoryEvidence(resolution: MemoryResolution): String {
    if (resolution.status == MemoryStatus.UNAVAILABLE) {
        return "A Memoria.ia está indisponível. Não consigo recuperar registros agora."
    }
    val excerpts = resolution.contextItems
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .map { it.take(600) }
        .toList()
    if (resolution.status != MemoryStatus.HIT || excerpts.isEmpty()) {
        return "Ainda não encontrei registros suficientes na Memoria.ia para responder."
    }
    return if (excerpts.size == 1) {
        "Encontrei este registro relacionado na Memoria.ia: “${excerpts.single()}”"
    } else {
        "Encontrei estes registros relacionados na Memoria.ia:\n" +
            excerpts.joinToString("\n") { "• $it" }
    }
}
