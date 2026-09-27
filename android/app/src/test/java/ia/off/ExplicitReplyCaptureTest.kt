package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitReplyCaptureTest {
    @Test
    fun acceptsOnlySelectedEarlierUserEntryInCurrentConversation() {
        val question = ChatMessage(id = "q", role = "Você", text = "Qual nome do meu pai?", createdAt = 12)
        val unrelated = ChatMessage(id = "u", role = "Você", text = "Outra conversa", createdAt = 11)
        val assistant = ChatMessage(id = "a", role = "OFF.IA", text = "Não sei", createdAt = 13)
        assertEquals(ExplicitReplyTarget("q", 12), selectedReplyTarget(listOf(question, assistant), question, 14))
        assertNull(selectedReplyTarget(listOf(question, assistant), unrelated, 14))
        assertNull(selectedReplyTarget(listOf(question, assistant), assistant, 14))
        assertNull(selectedReplyTarget(listOf(question), question, 12))
        assertNull(selectedReplyTarget(listOf(question), question.copy(createdAt = 11), 14))
        assertNull(selectedReplyTarget(listOf(question), null, 14))
        assertEquals(13L, nextUserSequence(listOf(question, assistant), 12))
    }

    @Test
    fun recoveryRetriesOnlyValidExplicitIntentionAndKeepsItIdempotent() = runBlocking {
        val question = ChatMessage(id = "q", role = "Você", text = "Pergunta", createdAt = 12)
        val answer = ChatMessage(id = "r", role = "Você", text = "Resposta", createdAt = 13,
            replyTo = ExplicitReplyTarget("q", 12))
        val invalid = answer.copy(id = "other", replyTo = ExplicitReplyTarget("missing", 11))
        val messages = mutableListOf(question, answer, invalid)
        val calls = mutableListOf<ExplicitReplyTarget>()
        var flushes = 0
        val gateway = object : MemoryGateway by UnavailableMemoryGateway {
            override val available = true
            override suspend fun linkUserReply(sessionId: String, sourceId: String, sequence: Long,
                target: ExplicitReplyTarget): Boolean {
                assertEquals("chat", sessionId)
                assertEquals("r", sourceId)
                assertEquals(13L, sequence)
                calls += target
                return true
            }
            override suspend fun flush() { flushes++ }
        }
        assertEquals(1, recoverExplicitReplies(gateway, "chat", messages))
        assertTrue(messages[1].replyRecorded)
        assertFalse(messages[2].replyRecorded)
        assertEquals(0, recoverExplicitReplies(gateway, "chat", messages))
        assertEquals(listOf(ExplicitReplyTarget("q", 12)), calls)
        assertEquals(1, flushes)
    }
}
