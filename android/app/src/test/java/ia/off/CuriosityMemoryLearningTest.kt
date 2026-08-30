package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("derived", memory.requests[2].importKind)
        assertEquals(listOf("memory-1", "memory-2"), memory.requests[2].parentMemoryIds)
        assertEquals("offia-curiosity", memory.requests[2].providerId)
        assertEquals(1, memory.flushCount)
        assertTrue(report.synthesisStored)
        assertEquals(listOf("memory-1", "memory-2"), report.sourceMemoryIds)
        assertEquals(listOf("memory-1", "memory-2", "memory-3"), report.storedMemoryIds)
    }

    @Test
    fun blankSynthesisPersistsSourcesOnly() = runBlocking {
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
            ),
        )

        val report = learnCuriosityResult(
            memory = memory,
            result = result,
            synthesis = "   ",
            sessionId = "session-blank",
            requestId = "curiosity-blank",
            acquiredTime = "2026-08-30T12:00:30Z",
        )

        assertEquals(1, memory.requests.size)
        assertEquals("imported", memory.requests.single().importKind)
        assertFalse(report.synthesisStored)
        assertEquals(listOf("memory-1"), report.storedMemoryIds)
        assertEquals(1, memory.flushCount)
    }

    @Test
    fun derivedSynthesisUsesFirstActuallyLearnedSourceAsPrimaryProvenance() = runBlocking {
        val memory = RecordingMemoryGateway()
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Empty Source",
                    url = "https://pt.wikipedia.org/wiki/Empty",
                    domain = "pt.wikipedia.org",
                    excerpt = "   ",
                ),
                CuriositySource(
                    title = "Accepted Source",
                    url = "https://en.wikipedia.org/wiki/Accepted",
                    domain = "en.wikipedia.org",
                    excerpt = "Accepted public evidence.",
                ),
            ),
        )

        val report = learnCuriosityResult(
            memory = memory,
            result = result,
            synthesis = "Derived answer from accepted evidence.",
            sessionId = "session-primary",
            requestId = "curiosity-primary",
            acquiredTime = "2026-08-30T12:00:45Z",
        )

        assertEquals(2, memory.requests.size)
        assertEquals("https://en.wikipedia.org/wiki/Accepted", memory.requests[0].sourceUrl)
        assertEquals("https://en.wikipedia.org/wiki/Accepted", memory.requests[1].sourceUrl)
        assertEquals("derived", memory.requests[1].importKind)
        assertEquals(listOf("memory-1"), memory.requests[1].parentMemoryIds)
        assertTrue(report.synthesisStored)
    }

    @Test
    fun flushFailureDoesNotDiscardLearnedPublicKnowledgeReport() = runBlocking {
        val memory = RecordingMemoryGateway(failFlush = true)
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "A public fact is 7319.",
                ),
            ),
        )

        val report = learnCuriosityResult(
            memory = memory,
            result = result,
            synthesis = "",
            sessionId = "session-2",
            requestId = "curiosity-2",
            acquiredTime = "2026-08-30T12:01:00Z",
        )

        assertTrue(report.learned)
        assertTrue(report.flushFailed)
        assertEquals(listOf("memory-1"), report.storedMemoryIds)
        assertEquals(1, memory.flushCount)
    }
}

private class RecordingMemoryGateway(
    private val failFlush: Boolean = false,
) : MemoryGateway {
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
        if (failFlush) error("synthetic flush failure")
    }
}
