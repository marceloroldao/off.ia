package ia.off

import android.content.Context

data class ImproveProviderSelection(
    val provider: ImproveProvider?,
    val kind: ImproveProviderKind?,
    val configured: Boolean,
    val reason: String? = null,
)

/**
 * New OFF.IA builds never construct OpenAI/Gemini clients locally.
 * Transformer access is exclusively mediated by M2A2 and the Memoria.ia server.
 *
 * This function remains the single UI/runtime selection boundary so a future
 * M2A2ImproveProvider can be inserted without changing message semantics.
 */
fun configuredImproveProvider(
    context: Context,
    settings: AppSettings,
): ImproveProviderSelection {
    @Suppress("UNUSED_VARIABLE")
    val applicationContext = context.applicationContext

    if (settings.blockNetworkAfterModelDownload) {
        return ImproveProviderSelection(
            provider = null,
            kind = ImproveProviderKind.MA2A,
            configured = false,
            reason = "Rede M2A2 bloqueada nas configurações",
        )
    }

    return ImproveProviderSelection(
        provider = null,
        kind = ImproveProviderKind.MA2A,
        configured = false,
        reason = "M2A2 aguardando conexão com o servidor Memoria.ia",
    )
}

fun ImproveProviderKind.displayName(): String = when (this) {
    ImproveProviderKind.OPENAI -> "OpenAI (histórico)"
    ImproveProviderKind.GEMINI -> "Gemini (histórico)"
    ImproveProviderKind.MA2A -> "M2A2"
}
