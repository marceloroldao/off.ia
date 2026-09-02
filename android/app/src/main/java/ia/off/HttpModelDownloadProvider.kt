package ia.off

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class HttpModelDownloadProvider(
    context: Context,
) : ModelDownloadProvider {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    @Volatile
    private var cancelled = false

    override suspend fun download(
        descriptor: ModelDownloadDescriptor,
        onState: (ModelDownloadState) -> Unit,
    ): ModelDownloadState = withContext(Dispatchers.IO) {
        cancelled = false
        val target = File(modelsDir, descriptor.fileName)
        val temp = File(modelsDir, "${descriptor.fileName}.part")
        var connection: HttpURLConnection? = null

        try {
            if (target.isFile && verifyFile(target, descriptor)) {
                return@withContext ModelDownloadState.Ready(target.absolutePath).also(onState)
            }
            if (target.exists()) target.delete()
            if (temp.exists()) temp.delete()

            connection = (URL(descriptor.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "OFF.IA-Android/0.1")
                setRequestProperty("Accept", "application/octet-stream")
                connect()
            }

            val responseCode = connection.responseCode
            require(responseCode in 200..299) {
                "Download HTTP $responseCode"
            }

            val totalBytes = connection.contentLengthLong
                .takeIf { it > 0L }
                ?: descriptor.expectedSizeBytes
            var downloaded = 0L
            var lastReported = 0L

            onState(ModelDownloadState.Downloading(downloaded, totalBytes))
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (cancelled) throw DownloadCancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (downloaded - lastReported >= 1024 * 1024 || downloaded == totalBytes) {
                            lastReported = downloaded
                            onState(ModelDownloadState.Downloading(downloaded, totalBytes))
                        }
                    }
                    output.fd.sync()
                }
            }

            descriptor.expectedSizeBytes?.let { expected ->
                require(temp.length() == expected) {
                    "Tamanho inválido: ${temp.length()} bytes; esperado $expected"
                }
            }

            onState(ModelDownloadState.Verifying)
            require(verifyFile(temp, descriptor)) {
                "Falha na verificação SHA-256 do modelo"
            }

            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Falha ao concluir o download do modelo" }
            ModelDownloadState.Ready(target.absolutePath).also(onState)
        } catch (_: DownloadCancelledException) {
            temp.delete()
            ModelDownloadState.Cancelled.also(onState)
        } catch (e: CancellationException) {
            temp.delete()
            ModelDownloadState.Cancelled.also(onState)
        } catch (e: Exception) {
            temp.delete()
            ModelDownloadState.Failed(e.message ?: e.javaClass.simpleName).also(onState)
        } finally {
            connection?.disconnect()
        }
    }

    override fun cancel() {
        cancelled = true
    }

    private fun verifyFile(file: File, descriptor: ModelDownloadDescriptor): Boolean {
        if (!file.isFile || !file.canRead()) return false
        descriptor.expectedSizeBytes?.let { if (file.length() != it) return false }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return actual.equals(descriptor.sha256, ignoreCase = true)
    }

    private class DownloadCancelledException : RuntimeException()
}
