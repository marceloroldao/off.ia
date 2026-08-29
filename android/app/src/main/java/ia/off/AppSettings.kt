package ia.off

import android.content.Context

data class AppSettings(
    val autoDownloadDefaultModel: Boolean = true,
    val wifiOnlyModelDownloads: Boolean = false,
    val confirmBeforeCloud: Boolean = true,
    val blockNetworkAfterModelDownload: Boolean = false,
    val laboratoryMode: Boolean = false,
)

class AppSettingsStore(context: Context) {
    companion object {
        private const val PREFS = "offia-settings-v1"
        private const val AUTO_DEFAULT_MODEL = "auto_download_default_model"
        private const val WIFI_ONLY = "wifi_only_model_downloads"
        private const val CONFIRM_CLOUD = "confirm_before_cloud"
        private const val BLOCK_NETWORK = "block_network_after_model_download"
        private const val LAB_MODE = "laboratory_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        autoDownloadDefaultModel = prefs.getBoolean(AUTO_DEFAULT_MODEL, true),
        wifiOnlyModelDownloads = prefs.getBoolean(WIFI_ONLY, false),
        confirmBeforeCloud = prefs.getBoolean(CONFIRM_CLOUD, true),
        blockNetworkAfterModelDownload = prefs.getBoolean(BLOCK_NETWORK, false),
        laboratoryMode = prefs.getBoolean(LAB_MODE, false),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(AUTO_DEFAULT_MODEL, settings.autoDownloadDefaultModel)
            .putBoolean(WIFI_ONLY, settings.wifiOnlyModelDownloads)
            .putBoolean(CONFIRM_CLOUD, settings.confirmBeforeCloud)
            .putBoolean(BLOCK_NETWORK, settings.blockNetworkAfterModelDownload)
            .putBoolean(LAB_MODE, settings.laboratoryMode)
            .apply()
    }
}
