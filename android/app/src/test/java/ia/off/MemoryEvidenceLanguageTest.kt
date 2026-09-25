package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEvidenceLanguageTest {
    @Test
    fun retrievalOfAnOldQuestionIsShownOnlyAsARecord() {
        val text = renderMemoryEvidence(MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("qual nome do meu pai?"),
        ))
        assertTrue(text.contains("registro relacionado"))
        assertTrue(text.contains("qual nome do meu pai?"))
        assertFalse(text.contains("Seu pai se chama"))
    }

    @Test
    fun competingObservationsAreBothVisibleWithoutInventingAnAnswer() {
        val text = renderMemoryEvidence(MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("A fonte é 24 V.", "A fonte é 12 V."),
        ))
        assertTrue(text.contains("A fonte é 24 V."))
        assertTrue(text.contains("A fonte é 12 V."))
    }

    @Test
    fun unresolvedAndEmptyHitDoNotPretendToAnswer() {
        val unknown = "Ainda não encontrei registros suficientes na Memoria.ia para responder."
        assertEquals(unknown, renderMemoryEvidence(MemoryResolution(MemoryStatus.UNRESOLVED)))
        assertEquals(unknown, renderMemoryEvidence(MemoryResolution(MemoryStatus.HIT)))
    }
}
