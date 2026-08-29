package ia.off

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class InstalledModel(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val ggufVersion: Int?,
    val valid: Boolean,
    val active: Boolean,
)

class ModelManager(context: Context) {
    companion object {
        private const val APP_PREFS = "offia-local"
        private const val PREF_MODEL_PATH = "model-path"
        private const val MODELS_DIR = "models"
    }

    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, MODELS_DIR).apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    fun installedModels(): List<InstalledModel> {
        val activePath = prefs.getString(PREF_MODEL_PATH, null)
        return modelsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && !it.name.endsWith(".part", ignoreCase = true) }
            .map { file ->
                val version = readGgufVersion(file)
                InstalledModel(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    ggufVersion = version,
                    valid = version != null,
                    active = activePath == file.absolutePath,
                )
            }
            .sortedWith(compareByDescending<InstalledModel> { it.active }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun activeModel(): InstalledModel? = installedModels().firstOrNull { it.active }

    fun storageBytes(): Long = installedModels().sumOf { it.sizeBytes }

    fun delete(model: InstalledModel): Boolean {
        require(!model.active) { "O modelo ativo deve ser trocado antes de ser excluído" }
        val canonicalRoot = modelsDir.canonicalFile
        val target = File(model.path).canonicalFile
        require(target.parentFile == canonicalRoot) { "Modelo fora do diretório gerenciado" }
        return !target.exists() || target.delete()
    }

    private fun readGgufVersion(file: File): Int? = runCatching {
        if (!file.isFile || file.length() < 8L) return@runCatching null
        val header = ByteArray(8)
        file.inputStream().use { input ->
            if (input.read(header) != header.size) return@runCatching null
        }
        if (header.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "GGUF") return@runCatching null
        val version = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        version.takeIf { it in 2..3 }
    }.getOrNull()
}

fun Long.formatStorageSize(): String {
    val mib = this / (1024.0 * 1024.0)
    return if (mib >= 1024.0) "%.2f GB".format(mib / 1024.0) else "%.1f MB".format(mib)
}
