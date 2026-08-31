package ia.off

internal fun validateExternalKnowledgeSource(source: ExternalKnowledgeSource) {
    require(source.content.isNotBlank()) { "Conhecimento público vazio" }
    require(source.sourceUrl.startsWith("https://") || source.sourceUrl.startsWith("http://")) {
        "URL de origem pública deve usar http(s)"
    }
    require(source.sourceDomain.isNotBlank() && source.sourceDomain.none { it.isWhitespace() || it == '/' || it == '\\' }) {
        "Domínio de origem pública inválido"
    }
    require(source.sourceTitle.isNotBlank()) { "Título de origem pública vazio" }
    require(source.acquiredTime.isNotBlank()) { "Data de aquisição pública vazia" }
    require(source.validationConfidence in 0.0..1.0) { "Confiança pública deve estar entre 0 e 1" }
    require(source.importKind in setOf("imported", "synthesized", "derived")) {
        "Tipo de importação pública inválido"
    }
    if (source.importKind == "derived") {
        require(source.parentMemoryIds.isNotEmpty()) { "Conhecimento público derivado requer memória-pai" }
        require(source.parentMemoryIds.all { it.isNotBlank() }) { "ID de memória-pai público inválido" }
    } else {
        require(source.parentMemoryIds.isEmpty()) { "Somente conhecimento público derivado aceita memória-pai" }
    }
}

/**
 * Produces the exact JSON shape consumed by Memoria.ia's post-v1 C parser.
 * Forward slashes are deliberately kept literal so http(s) URLs remain
 * `https://...` when validated natively.
 */
internal fun buildExternalKnowledgeRequestJson(source: ExternalKnowledgeSource): String {
    validateExternalKnowledgeSource(source)
    return buildString {
        append('{')
        field("content", source.content)
        append(','); field("source_class", "external_public")
        append(','); field("source_url", source.sourceUrl)
        append(','); field("source_domain", source.sourceDomain)
        append(','); field("source_title", source.sourceTitle)
        append(','); field("acquired_time", source.acquiredTime)
        append(','); field("source_excerpt", source.sourceExcerpt)
        append(','); field("provider_id", source.providerId)
        append(','); field("import_kind", source.importKind)
        append(',').append("\"validation_confidence\":").append(source.validationConfidence)
        append(','); field("request_id", source.requestId)
        append(','); field("session_id", source.sessionId)
        append(','); field("namespace", source.namespace)
        append(',').append("\"parent_memory_ids\":[")
        source.parentMemoryIds.forEachIndexed { index, id ->
            if (index > 0) append(',')
            quoted(id)
        }
        append("]}")
    }
}

private fun StringBuilder.field(name: String, value: String) {
    quoted(name)
    append(':')
    quoted(value)
}

private fun StringBuilder.quoted(value: String) {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    append('"')
}
