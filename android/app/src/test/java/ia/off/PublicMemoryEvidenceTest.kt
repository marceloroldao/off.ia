package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicMemoryEvidenceTest {
    @Test
    fun matchesOnlyResolvedIdsPreviouslyAuditedAsExternalPublic() {
        val sessions = listOf(
            ChatSession(
                id = "session-public",
                title = "Public",
                messages = mutableListOf(
                    ChatMessage(
                        role = "OFF.IA",
                        text = "Curiosity answer",
                        generation = GenerationMetadata(
                            source = ResponseSource.CURIOSITY,
                            publicKnowledge = PublicKnowledgeAudit(
                                storedMemoryIds = listOf("pub-1", "pub-2"),
                            ),
                        ),
                    ),
                ),
                updatedAt = 1L,
            ),
        )

        val matched = matchExternalPublicMemoryIds(
            resolvedMemoryIds = listOf("local-1", "pub-2", "pub-1", "pub-2"),
            sessions = sessions,
        )

        assertEquals(listOf("pub-2", "pub-1"), matched)
    }

    @Test
    fun ignoresNonExternalAuditClassesAndBlankIds() {
        val sessions = listOf(
            ChatSession(
                id = "session-private",
                title = "Private",
                messages = mutableListOf(
                    ChatMessage(
                        role = "OFF.IA",
                        text = "Other answer",
                        generation = GenerationMetadata(
                            source = ResponseSource.CURIOSITY,
                            publicKnowledge = PublicKnowledgeAudit(
                                knowledgeClass = "not_external_public",
                                storedMemoryIds = listOf("pub-wrong", ""),
                            ),
                        ),
                    ),
                ),
                updatedAt = 2L,
            ),
        )

        assertTrue(matchExternalPublicMemoryIds(listOf("pub-wrong"), sessions).isEmpty())
        assertTrue(externalPublicMemoryIds(sessions).isEmpty())
    }
}
