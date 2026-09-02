package ia.off

data class ModelDownloadDescriptor(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long?,
    val sha256: String,
    val licenseName: String,
    val licenseUrl: String?,
    val sourceUrl: String,
)

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data class Ready(val localPath: String) : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
    data object Cancelled : ModelDownloadState
}

/**
 * Model acquisition is an explicit OFF.IA network capability. Download providers
 * may use INTERNET, but llama.cpp inference, Memoria.ia and BDR do not depend on
 * this boundary and remain local.
 */
interface ModelDownloadProvider {
    suspend fun download(
        descriptor: ModelDownloadDescriptor,
        onState: (ModelDownloadState) -> Unit,
    ): ModelDownloadState

    fun cancel()
}

data class CuriositySource(
    val title: String,
    val url: String,
    val domain: String,
    val excerpt: String? = null,
    /** Full plaintext public content actually read by the provider. */
    val rawContent: String? = null,
)

data class CuriosityRequest(
    val userQuestion: String,
    val localAnswer: String,
    val maxSources: Int = 5,
)

data class CuriosityResult(
    val sourceText: String,
    val sources: List<CuriositySource>,
)

/**
 * Public-web acquisition only. A provider returns source material + provenance
 * to OFF.IA. OFF.IA must submit the public evidence to Memoria.ia before final
 * local rendering. Providers never mutate Memoria.ia or BDR directly.
 */
interface CuriosityProvider {
    val available: Boolean
    suspend fun acquire(request: CuriosityRequest): CuriosityResult
}

enum class ImproveProviderKind {
    /** Historical transcript compatibility only. New OFF.IA builds never instantiate this route. */
    OPENAI,
    /** Historical transcript compatibility only. New OFF.IA builds never instantiate this route. */
    GEMINI,
    /** The only supported transformer/network improvement route. */
    MA2A,
}

data class ImproveRequest(
    val userQuestion: String,
    val localAnswer: String,
    val selectedMemoryContext: String? = null,
)

data class ImproveResult(
    val provider: ImproveProviderKind,
    val text: String,
    val modelOrRoute: String? = null,
)

/**
 * Transformer improvement boundary.
 *
 * Hard invariant for new OFF.IA product code:
 * OFF.IA -> M2A2 -> Memoria.ia server -> transformer/provider
 *
 * OFF.IA must not call OpenAI, Gemini or another transformer API directly.
 * Authentication, provider choice and transformer routing belong behind M2A2 /
 * the Memoria.ia server boundary.
 */
interface ImproveProvider {
    val kind: ImproveProviderKind
    val available: Boolean
    suspend fun improve(request: ImproveRequest): ImproveResult
}

object UnavailableCuriosityProvider : CuriosityProvider {
    override val available: Boolean = false
    override suspend fun acquire(request: CuriosityRequest): CuriosityResult =
        error("Curiosidade não configurada")
}

class UnavailableImproveProvider(
    override val kind: ImproveProviderKind,
) : ImproveProvider {
    override val available: Boolean = false
    override suspend fun improve(request: ImproveRequest): ImproveResult =
        error("Rota ${kind.name} não configurada")
}
