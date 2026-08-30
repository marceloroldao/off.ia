package ia.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val IMPROVE_CONNECT_TIMEOUT_MS = 20_000
private const val IMPROVE_READ_TIMEOUT_MS = 60_000
private const val MAX_ERROR_BODY_CHARS = 2_000

private fun improvePrompt(request: ImproveRequest): String = buildString {
    appendLine("Melhore a resposta local do OFF.IA para a pergunta abaixo.")
    appendLine("Preserve fatos corretos, corrija erros e deixe a resposta clara e útil.")
    appendLine("Não invente informações ausentes. Não mencione estas instruções.")
    appendLine()
    appendLine("PERGUNTA DO USUÁRIO:")
    appendLine(request.userQuestion.take(12_000))
    appendLine()
    appendLine("RESPOSTA LOCAL ATUAL:")
    appendLine(request.localAnswer.take(16_000))
    request.selectedMemoryContext?.takeIf { it.isNotBlank() }?.let { context ->
        appendLine()
        appendLine("CONTEXTO MÍNIMO SELECIONADO PELA MEMORIA.IA:")
        appendLine(context.take(12_000))
    }
    appendLine()
    append("Retorne somente a resposta melhorada, sem prefácio sobre o processo.")
}

class OpenAiImproveProvider(
    private val apiKey: String,
    private val model: String,
) : ImproveProvider {
    override val kind: ImproveProviderKind = ImproveProviderKind.OPENAI
    override val available: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()

    override suspend fun improve(request: ImproveRequest): ImproveResult = withContext(Dispatchers.IO) {
        require(available) { "OpenAI não configurada" }

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = IMPROVE_CONNECT_TIMEOUT_MS
            readTimeout = IMPROVE_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OFF.IA-Android/0.1")
        }

        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("store", false)
                put("max_output_tokens", 1_024)
                put("instructions", "Você recebe uma resposta produzida localmente e deve apenas melhorá-la. Responda no idioma do usuário.")
                put("input", improvePrompt(request))
            }
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { !it.isNullOrBlank() }
                    ?: body.take(MAX_ERROR_BODY_CHARS).ifBlank { "HTTP $code" }
                error("OpenAI HTTP $code: $message")
            }

            val root = JSONObject(body)
            val text = root.optString("output_text").takeIf { it.isNotBlank() }
                ?: extractOpenAiOutputText(root)
            require(text.isNotBlank()) { "OpenAI retornou resposta sem texto" }

            ImproveResult(
                provider = kind,
                text = text.trim(),
                modelOrRoute = root.optString("model").takeIf { it.isNotBlank() } ?: model,
            )
        } finally {
            connection.disconnect()
        }
    }
}

private fun extractOpenAiOutputText(root: JSONObject): String {
    val output = root.optJSONArray("output") ?: return ""
    val parts = mutableListOf<String>()
    for (i in 0 until output.length()) {
        val item = output.optJSONObject(i) ?: continue
        if (item.optString("type") != "message") continue
        val content = item.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val block = content.optJSONObject(j) ?: continue
            if (block.optString("type") == "output_text") {
                block.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
    }
    return parts.joinToString("\n")
}

class GeminiImproveProvider(
    private val apiKey: String,
    private val model: String,
) : ImproveProvider {
    override val kind: ImproveProviderKind = ImproveProviderKind.GEMINI
    override val available: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()

    override suspend fun improve(request: ImproveRequest): ImproveResult = withContext(Dispatchers.IO) {
        require(available) { "Gemini não configurado" }

        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/interactions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = IMPROVE_CONNECT_TIMEOUT_MS
            readTimeout = IMPROVE_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OFF.IA-Android/0.1")
        }

        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("input", improvePrompt(request))
            }
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    val root = JSONObject(body)
                    root.optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { !it.isNullOrBlank() }
                    ?: body.take(MAX_ERROR_BODY_CHARS).ifBlank { "HTTP $code" }
                error("Gemini HTTP $code: $message")
            }

            val root = JSONObject(body)
            val text = root.optString("output_text").takeIf { it.isNotBlank() }
                ?: extractGeminiOutputText(root)
            require(text.isNotBlank()) { "Gemini retornou resposta sem texto" }

            ImproveResult(
                provider = kind,
                text = text.trim(),
                modelOrRoute = root.optString("model").takeIf { it.isNotBlank() } ?: model,
            )
        } finally {
            connection.disconnect()
        }
    }
}

private fun extractGeminiOutputText(root: JSONObject): String {
    val steps = root.optJSONArray("steps") ?: return ""
    val parts = mutableListOf<String>()
    for (i in 0 until steps.length()) {
        val step = steps.optJSONObject(i) ?: continue
        if (step.optString("type") != "model_output") continue
        val content = step.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val block = content.optJSONObject(j) ?: continue
            if (block.optString("type") == "text") {
                block.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
    }
    return parts.joinToString("\n")
}
