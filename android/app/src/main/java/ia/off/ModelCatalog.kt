package ia.off

object ModelCatalog {
    /**
     * Small default model for first-run onboarding.
     *
     * The download is pinned to an immutable Hugging Face revision so the
     * expected size and SHA-256 remain reproducible. Users can still import or
     * select any compatible GGUF from the Model Manager.
     */
    val defaultModel = ModelDownloadDescriptor(
        id = "qwen2.5-0.5b-instruct-q4_k_m",
        displayName = "Qwen2.5 0.5B Instruct Q4_K_M",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/21ef23001f314d0895bd8439b08157c2d4cd9bb7/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf?download=true",
        expectedSizeBytes = 397_808_192L,
        sha256 = "6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653",
        licenseName = "Apache-2.0",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/blob/main/LICENSE",
        sourceUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF",
    )

    val recommendedModels: List<ModelDownloadDescriptor> = listOf(defaultModel)
}
