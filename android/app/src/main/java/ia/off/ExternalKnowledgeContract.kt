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
