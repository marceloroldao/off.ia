package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpistemicIdentityTest {
    @Test
    fun reconstructsCandidateIdentityFromLegacyPersistedFactualTurnId() {
        val memory = ResponseMemoryMetadata(
            status = MemoryStatus.HIT,
            learnedMemoryIds = listOf("mobile:42"),
        )

        assertEquals("mobile:42", memory.effectiveResponseId)
        assertEquals("response:mobile:42", memory.effectiveCandidateMemoryId)
        assertNull(memory.validationStatus)
        assertNull(memory.learningDecisionId)
        assertNull(memory.learningAccepted)
        assertNull(memory.promotedMemoryId)
    }

    @Test
    fun explicitAuditIdsTakePrecedenceOverReconstruction() {
        val memory = ResponseMemoryMetadata(
            status = MemoryStatus.HIT,
            learnedMemoryIds = listOf("mobile:42"),
            responseId = "explicit-response",
            candidateMemoryId = "response:explicit-response",
            validationStatus = "SUPPORTED_BY_CONTEXT",
        )

        assertEquals("explicit-response", memory.effectiveResponseId)
        assertEquals("response:explicit-response", memory.effectiveCandidateMemoryId)
        assertEquals("SUPPORTED_BY_CONTEXT", memory.validationStatus)
    }
}
