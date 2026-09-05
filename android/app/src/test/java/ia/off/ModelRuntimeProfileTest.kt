package ia.off

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRuntimeProfileTest {
    @Test
    fun detectsChatMlQwenFromEmbeddedMetadata() {
        val profile = classifyModelRuntimeProfile(
            architecture = "qwen2",
            embeddedTemplate = "{% for message in messages %}<|im_start|>{{ message.role }}",
        )
        assertEquals("ChatML/Qwen", profile.templateFamily)
        assertEquals("GGUF incorporado", profile.templateSource)
    }

    @Test
    fun detectsLlamaFromEmbeddedMetadata() {
        val profile = classifyModelRuntimeProfile(
            architecture = "llama",
            embeddedTemplate = "<|start_header_id|>system<|end_header_id|>",
        )
        assertEquals("Llama", profile.templateFamily)
        assertEquals("GGUF incorporado", profile.templateSource)
    }

    @Test
    fun detectsGemmaFromEmbeddedMetadata() {
        val profile = classifyModelRuntimeProfile(
            architecture = "gemma2",
            embeddedTemplate = "<start_of_turn>user",
        )
        assertEquals("Gemma", profile.templateFamily)
        assertEquals("GGUF incorporado", profile.templateSource)
    }

    @Test
    fun missingTemplateUsesExplicitPlainFallback() {
        val profile = parseModelRuntimeProfile("llama\n")
        assertEquals("Texto simples", profile.templateFamily)
        assertEquals("fallback explícito", profile.templateSource)
    }
}
