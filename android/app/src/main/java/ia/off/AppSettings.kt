package ia.off

import android.content.Context

data class AppSettings(
    val autoDownloadDefaultModel: Boolean = true,
    val wifiOnlyModelDownloads: Boolean = false,
    val confirmBeforeCloud: Boolean = true,
    val blockNetworkAfterModelDownload: Boolean = false,
    val laboratoryMode: Boolean = false,
    val improveProvider: ImproveProviderKind? = null,
    val openAiModel: String = "gpt-5.4-mini",
    val geminiModel: String = "gemini-3.7-flash",
)

class AppSettingsStore(context: Context) {
    companion object {
        private const val PREFS = "offia-settings-v1"
        private const val AUTO_DEFAULT_MODEL = "auto_download_default_model"
        private const val WIFI_ONLY = "wifi_only_model_downloads"
        private const val CONFIRM_CLOUD = "confirm_before_cloud"
        private const val BLOCK_NETWORK = "block_network_after_model_download"
        private const val LAB_MODE = "laboratory_mode"
        private const val IMPROVE_PROVIDER = "improve_provider"
        private const val OPENAI_MODEL = "openai_model"
        private const val GEMINI_MODEL = "gemini_model"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val provider = prefs.getString(IMPROVE_PROVIDER, null)?.let { raw ->
            runCatching { ImproveProviderKind.valueOf(raw) }.getOrNull()
        }
        return AppSettings(
            autoDownloadDefaultModel = prefs.getBoolean(AUTO_DEFAULT_MODEL, true),
            wifiOnlyModelDownloads = prefs.getBoolean(WIFI_ONLY, false),
            confirmBeforeCloud = prefs.getBoolean(CONFIRM_CLOUD, true),
            blockNetworkAfterModelDownload = prefs.getBoolean(BLOCK_NETWORK, false),
            laboratoryMode = prefs.getBoolean(LAB_MODE, false),
            improveProvider = provider,
            openAiModel = prefs.getString(OPENAI_MODEL, "gpt-5.4-mini")?.ifBlank { "gpt-5.4-mini" }
                ?: "gpt-5.4-mini",
            geminiModel = prefs.getString(GEMINI_MODEL, "gemini-3.7-flash")?.ifBlank { "gemini-3.7-flash" }
                ?: "gemini-3.7-flash",
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(AUTO_DEFAULT_MODEL, settings.autoDownloadDefaultModel)
            .putBoolean(WIFI_ONLY, settings.wifiOnlyModelDownloads)
            .putBoolean(CONFIRM_CLOUD, settings.confirmBeforeCloud)
            .putBoolean(BLOCK_NETWORK, settings.blockNetworkAfterModelDownload)
            .putBoolean(LAB_MODE, settings.laboratoryMode)
            .also { editor ->
                settings.improveProvider?.let { editor.putString(IMPROVE_PROVIDER, it.name) }
                    ?: editor.remove(IMPROVE_PROVIDER)
            }
            .putString(OPENAI_MODEL, settings.openAiModel.trim().ifBlank { "gpt-5.4-mini" })
            .putString(GEMINI_MODEL, settings.geminiModel.trim().ifBlank { "gemini-3.7-flash" })
            .apply()
    }
}
