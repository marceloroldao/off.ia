package ia.off

data class ModelRuntimeProfile(
    val architecture: String,
    val templateFamily: String,
    val templateSource: String,
) {
    val displayLabel: String
        get() = "$architecture • $templateFamily • $templateSource"
}

/**
 * llama.cpp returns architecture on the first line and the exact embedded
 * tokenizer.chat_template after it. OFF.IA only classifies this metadata for
 * diagnostics; llama.cpp remains responsible for applying the template once.
 */
fun parseModelRuntimeProfile(metadata: String): ModelRuntimeProfile {
    val separator = metadata.indexOf('\n')
    val architecture = (if (separator >= 0) metadata.substring(0, separator) else metadata)
        .trim()
        .ifBlank { "arquitetura desconhecida" }
    val template = if (separator >= 0) metadata.substring(separator + 1) else ""
    return classifyModelRuntimeProfile(architecture, template)
}

fun classifyModelRuntimeProfile(
    architecture: String,
    embeddedTemplate: String,
): ModelRuntimeProfile {
    val arch = architecture.trim().ifBlank { "arquitetura desconhecida" }
    if (embeddedTemplate.isBlank()) {
        return ModelRuntimeProfile(
            architecture = arch,
            templateFamily = "Texto simples",
            templateSource = "fallback explícito",
        )
    }

    val normalized = embeddedTemplate.lowercase()
    val family = when {
        "<|im_start|>" in normalized || "chatml" in normalized || arch.startsWith("qwen", ignoreCase = true) ->
            "ChatML/Qwen"
        "<start_of_turn>" in normalized || arch.startsWith("gemma", ignoreCase = true) ->
            "Gemma"
        "<|start_header_id|>" in normalized || "[inst]" in normalized || arch.startsWith("llama", ignoreCase = true) ->
            "Llama"
        else -> "Template próprio"
    }
    return ModelRuntimeProfile(
        architecture = arch,
        templateFamily = family,
        templateSource = "GGUF incorporado",
    )
}
