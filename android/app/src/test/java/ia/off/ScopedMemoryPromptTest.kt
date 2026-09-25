package ia.off

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedMemoryPromptTest {
    @Test
    fun legacyStoreCannotSatisfyAnotherConversationAfterStructuralMiss() {
        assertFalse(shouldConsultLegacyMemory(enabled = true, sessionId = "new-chat"))
        assertFalse(shouldConsultLegacyMemory(enabled = true, sessionId = "old-chat"))
        assertFalse(shouldConsultLegacyMemory(enabled = false, sessionId = null))
        assertTrue(shouldConsultLegacyMemory(enabled = true, sessionId = null))
    }

    @Test
    fun selectedQuestionsAreNotDeclaredFactsToTheModel() {
        val prompt = materializePrompt(
            "qual nome da minha mãe?",
            MemoryResolution(
                status = MemoryStatus.HIT,
                contextItems = listOf("qual nome do meu pai?"),
            ),
        )
        assertTrue(prompt.contains("qual nome do meu pai?"))
        assertTrue(prompt.contains("Se não houver evidência suficiente, diga que não sabe."))
        assertFalse(prompt.contains("apenas como fatos de apoio"))
    }
}
