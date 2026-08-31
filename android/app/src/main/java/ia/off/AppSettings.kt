package ia.off

import android.content.Context

data class AppSettings(
    val autoDownloadDefaultModel: Boolean = true,
    val wifiOnlyModelDownloads: Boolean = false,
    val confirmBeforeM2A2: Boolean = true,
    val blockNetworkAfterModelDownload: Boolean = false,
    val laboratoryMode: Boolean = false,
    val improveProvider: ImproveProviderKind? = ImproveProviderKind.MA2A,
)

class AppSettingsStore(context: Context) {
    companion object {
        private const val PREFS = "offia-settings-v1"
        private const val AUTO_DEFAULT_MODEL = "auto_download_default_model"
        private const val WIFI_ONLY = "wifi_only_model_downloads"
        // Keep the old preference key so existing installs migrate without resetting user choice.
        private const val CONFIRM_EXTERNAL = "confirm_before_cloud"
        private const val BLOCK_NETWORK = "block_network_after_model_download"
        private const val LAB_MODE = "laboratory_mode"
        private const val IMPROVE_PROVIDER = "improve_provider"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val storedProvider = prefs.getString(IMPROVE_PROVIDER, null)
        // OPENAI/GEMINI values from older alpha builds are intentionally migrated
        // to MA2A. Direct transformer APIs are no longer an OFF.IA product path.
        val provider = when (storedProvider) {
            null, ImproveProviderKind.MA2A.name -> ImproveProviderKind.MA2A
            else -> ImproveProviderKind.MA2A
        }
        return AppSettings(
            autoDownloadDefaultModel = prefs.getBoolean(AUTO_DEFAULT_MODEL, true),
            wifiOnlyModelDownloads = prefs.getBoolean(WIFI_ONLY, false),
            confirmBeforeM2A2 = prefs.getBoolean(CONFIRM_EXTERNAL, true),
            blockNetworkAfterModelDownload = prefs.getBoolean(BLOCK_NETWORK, false),
            laboratoryMode = prefs.getBoolean(LAB_MODE, false),
            improveProvider = provider,
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(AUTO_DEFAULT_MODEL, settings.autoDownloadDefaultModel)
            .putBoolean(WIFI_ONLY, settings.wifiOnlyModelDownloads)
            .putBoolean(CONFIRM_EXTERNAL, settings.confirmBeforeM2A2)
            .putBoolean(BLOCK_NETWORK, settings.blockNetworkAfterModelDownload)
            .putBoolean(LAB_MODE, settings.laboratoryMode)
            .putString(IMPROVE_PROVIDER, ImproveProviderKind.MA2A.name)
            // Clean obsolete direct-provider settings from older alpha builds.
            .remove("openai_model")
            .remove("gemini_model")
            .apply()
    }
}
