package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuriosityMemoryLearningTest {
    @Test
    fun storesPublicSourcesBeforeDerivedSynthesis() = runBlocking {
        val memory = RecordingMemoryGateway()
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "A public fact is 7319.",
                ),
                CuriositySource(
                    title = "Source B",
                    url = "https://en.wikipedia.org/wiki/B",
                    domain = "en.wikipedia.org",
                    excerpt = "Independent public evidence for 7319.",
                ),
            ),
        )

        val report = learnCuriosityResult(
            memory = memory,
            result = result,
            synthesis = "The verified public answer is 7319.",
            sessionId = "session-1",
            requestId = "curiosity-1",
            acquiredTime = "2026-08-30T12:00:00Z",
        )

        assertEquals(3, memory.requests.size)
        assertEquals("imported", memory.requests[0].importKind)
        assertEquals("imported", memory.requests[1].importKind)
        assertEquals("synthesized", memory.requests[2].importKind)
        assertEquals(listOf("memory-1", "memory-2"), memory.requests[2].parentMemoryIds)
        assertEquals("offia-curiosity", memory.requests[2].providerId)
        assertEquals(1, memory.flushCount)
        assertTrue(report.synthesisStored)
        assertEquals(listOf("memory-1", "memory-2"), report.sourceMemoryIds)
        assertEquals(listOf("memory-1", "memory-2", "memory-3"), report.storedMemoryIds)
    }
}

private class RecordingMemoryGateway : MemoryGateway {
    override val available: Boolean = true
    val requests = mutableListOf<ExternalKnowledgeSource>()
    var flushCount: Int = 0

    override suspend fun resolve(
        message: String,
        sessionId: String?,
        conversationWindow: List<MemoryWindowTurn>,
    ) = MemoryResolution(status = MemoryStatus.MISS)

    override suspend fun learnTurn(userText: String, assistantText: String) = MemoryLearnResult()

    override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource): ExternalKnowledgeLearnResult {
        requests += source
        val id = "memory-${requests.size}"
        return ExternalKnowledgeLearnResult(
            memoryIds = listOf(id),
            sourceAttached = true,
            sourceCount = 1,
            sourceType = "external_import",
        )
    }

    override suspend fun exportSnapshotPage(turnOffset: Int, episodeOffset: Int, limit: Int): String? = null

    override suspend fun flush() {
        flushCount += 1
    }
}
