package ia.off

import org.json.JSONObject

data class LinkedReplyGroup(
    val text: String,
    val occurrences: Int,
    val sourceRegions: Int,
)

/** Read-only, source-addressed diagnostic. Groups are never verified facts. */
data class LinkedReplyInspection(
    val status: String,
    val distinctTrails: Int,
    val occurrences: Int,
    val repeatedQuestionLinks: Int,
    val groupsTruncated: Boolean,
    val groups: List<LinkedReplyGroup>,
)

internal fun parseLinkedReplyInspection(raw: String): LinkedReplyInspection {
    val json = JSONObject(raw)
    check(json.getString("evidence_boundary") == "explicit_reply_only" &&
        json.getString("evidence_scope") == "exact_target" &&
        !json.getBoolean("qualified") && !json.getBoolean("selection_used") &&
        json.isNull("answer")) { "Diagnóstico de respostas sem isolamento de origem" }
    val status = json.getString("status")
    check(status in setOf("UNRESOLVED", "CANDIDATES", "CONFLICT")) {
        "Estado de evidência inesperado"
    }
    val rows = json.getJSONArray("groups")
    val groups = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            add(LinkedReplyGroup(
                row.getString("representative_text"),
                row.getInt("occurrences"),
                row.getInt("source_regions"),
            ))
        }
    }
    val distinct = json.getInt("distinct_reply_trails")
    val occurrences = json.getInt("explicit_reply_occurrences")
    val repeats = json.getInt("repeat_question_links")
    val truncated = json.getBoolean("groups_truncated")
    check(distinct >= 0 && occurrences >= 0 && repeats >= 0 &&
        groups.size <= distinct && (truncated || groups.size == distinct) &&
        groups.all { it.occurrences > 0 && it.sourceRegions > 0 }) {
        "Pacote de evidência inconsistente"
    }
    return LinkedReplyInspection(status, distinct, occurrences, repeats, truncated, groups)
}

internal fun renderLinkedReplyInspection(value: LinkedReplyInspection): String = buildString {
    append("Vínculos explícitos: ${value.occurrences} • trilhas distintas: ${value.distinctTrails}")
    if (value.repeatedQuestionLinks > 0) {
        append(" • perguntas repetidas: ${value.repeatedQuestionLinks}")
    }
    append("\n")
    if (value.distinctTrails > 1) {
        append("Há trilhas diferentes; nenhuma foi escolhida como resposta.\n")
    }
    if (value.groups.isEmpty()) {
        append("Nenhuma entrada vinculada a este endereço.")
    } else {
        value.groups.forEachIndexed { index, group ->
            append("\n${index + 1}. ${group.text.take(240)}")
            append("\n   ${group.occurrences} ocorrência(s), ${group.sourceRegions} conversa(s)")
        }
        if (value.groupsTruncated) append("\nMais trilhas no export de diagnóstico.")
    }
    append("\n\nEntradas vinculadas são observações do usuário; não são fatos verificados.")
}
