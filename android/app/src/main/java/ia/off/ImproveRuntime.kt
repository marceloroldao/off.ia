package ia.off

import android.content.Context

data class ImproveProviderSelection(
    val provider: ImproveProvider?,
    val kind: ImproveProviderKind?,
    val configured: Boolean,
    val reason: String? = null,
)

fun configuredImproveProvider(
    context: Context,
    settings: AppSettings,
): ImproveProviderSelection {
    val kind = settings.improveProvider
        ?: return ImproveProviderSelection(null, null, configured = false, reason = "Nenhum provedor selecionado")

    if (settings.blockNetworkAfterModelDownload && kind != ImproveProviderKind.MA2A) {
        return ImproveProviderSelection(null, kind, configured = false, reason = "Rede bloqueada nas configurações")
    }

    val credentials = SecureCredentialStore(context.applicationContext)
    return when (kind) {
        ImproveProviderKind.OPENAI -> {
            val key = credentials.get(SecureCredentialStore.OPENAI_API_KEY)
            if (key.isNullOrBlank()) {
                ImproveProviderSelection(null, kind, configured = false, reason = "Chave OpenAI não configurada")
            } else {
                ImproveProviderSelection(
                    provider = OpenAiImproveProvider(key, settings.openAiModel),
                    kind = kind,
                    configured = true,
                )
            }
        }
        ImproveProviderKind.GEMINI -> {
            val key = credentials.get(SecureCredentialStore.GEMINI_API_KEY)
            if (key.isNullOrBlank()) {
                ImproveProviderSelection(null, kind, configured = false, reason = "Chave Gemini não configurada")
            } else {
                ImproveProviderSelection(
                    provider = GeminiImproveProvider(key, settings.geminiModel),
                    kind = kind,
                    configured = true,
                )
            }
        }
        ImproveProviderKind.MA2A -> ImproveProviderSelection(
            provider = null,
            kind = kind,
            configured = false,
            reason = "MA2A ainda aguarda o contrato de rede",
        )
    }
}

fun ImproveProviderKind.displayName(): String = when (this) {
    ImproveProviderKind.OPENAI -> "OpenAI"
    ImproveProviderKind.GEMINI -> "Gemini"
    ImproveProviderKind.MA2A -> "MA2A"
}
