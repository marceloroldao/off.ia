package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuriosityResolvedContextTest {
    @Test
    fun persistsThenResolvesBeforeRendering() = runBlocking {
        val memory = OrderingMemoryGateway()
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "short excerpt",
                    rawContent = "full public source content",
                ),
            ),
        )

        val outcome = learnAndResolveCuriosity(
            memory = memory,
            result = result,
            userQuestion = "Pergunta pública",
            sessionId = "session-1",
            requestId = "curiosity-1",
        )

        assertEquals(listOf("learn", "flush", "resolve"), memory.calls)
        assertTrue(outcome.readyForRendering)
        assertEquals(listOf("resolved public fact"), outcome.resolution.contextItems)
        val prompt = materializeResolvedCuriosityPrompt("Pergunta pública", outcome.resolution)
        assertTrue(prompt.contains("resolved public fact"))
        assertFalse(prompt.contains("full public source content"))
    }

    @Test
    fun rejectedSourceNeverResolvesOrRenders() = runBlocking {
        val memory = OrderingMemoryGateway(failLearn = true)
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "short excerpt",
                    rawContent = "full public source content",
                ),
            ),
        )

        val outcome = learnAndResolveCuriosity(
            memory = memory,
            result = result,
            userQuestion = "Pergunta pública",
            sessionId = "session-2",
            requestId = "curiosity-2",
        )

        assertEquals(listOf("learn"), memory.calls)
        assertFalse(outcome.readyForRendering)
        assertEquals(MemoryStatus.UNRESOLVED, outcome.resolution.status)
    }
}

private class OrderingMemoryGateway(
    private val failLearn: Boolean = false,
) : MemoryGateway {
    override val available: Boolean = true
    val calls = mutableListOf<String>()

    override suspend fun resolve(
        message: String,
        sessionId: String?,
        conversationWindow: List<MemoryWindowTurn>,
    ): MemoryResolution {
        calls += "resolve"
        return MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("resolved public fact"),
            memoryIds = listOf("public-1"),
            confidence = 0.9,
        )
    }

    override suspend fun learnTurn(userText: String, assistantText: String) = MemoryLearnResult()

    override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource): ExternalKnowledgeLearnResult {
        calls += "learn"
        if (failLearn) error("synthetic rejection")
        return ExternalKnowledgeLearnResult(memoryIds = listOf("public-1"), sourceAttached = true)
    }

    override suspend fun exportSnapshotPage(turnOffset: Int, episodeOffset: Int, limit: Int): String? = null

    override suspend fun flush() {
        calls += "flush"
    }
}
