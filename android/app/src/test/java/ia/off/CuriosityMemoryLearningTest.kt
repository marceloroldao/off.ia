package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuriosityMemoryLearningTest {
    @Test
    fun storesFullPublicSourcesAndNeverPersistsLlmSynthesis() = runBlocking {
        CuriosityPublicAuditBridge.clearForTests()
        val memory = RecordingMemoryGateway()
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "Short A.",
                    rawContent = "Full public content A with fact 7319 and additional evidence.",
                ),
                CuriositySource(
                    title = "Source B",
                    url = "https://en.wikipedia.org/wiki/B",
                    domain = "en.wikipedia.org",
                    excerpt = "Short B.",
                    rawContent = "Full independent public content B for fact 7319.",
                ),
            ),
        )

        val report = learnCuriosityResult(
            memory = memory,
            result = result,
            synthesis = "LLM synthesis must never become public memory.",
            sessionId = "session-1",
            requestId = "curiosity-1",
            acquiredTime = "2026-08-30T12:00:00Z",
        )

        assertEquals(2, memory.requests.size)
        assertTrue(memory.requests.all { it.importKind == "imported" })
        assertEquals("Full public content A with fact 7319 and additional evidence.", memory.requests[0].content)
        assertEquals("Full independent public content B for fact 7319.", memory.requests[1].content)
        assertTrue(memory.requests.none { it.importKind == "derived" })
        assertEquals(1, memory.flushCount)
        assertFalse(report.synthesisStored)
        assertEquals(listOf("memory-1", "memory-2"), report.sourceMemoryIds)
        assertEquals(listOf("memory-1", "memory-2"), report.storedMemoryIds)

        val audit = CuriosityPublicAuditBridge.peek("curiosity-1")
        assertEquals(report.toPublicKnowledgeAudit(), audit)
        assertEquals("external_public", audit?.knowledgeClass)
        assertFalse(audit?.synthesisStored ?: true)
    }

    @Test
    fun fallsBackToExcerptWhenProviderHasNoRawContent() = runBlocking {
        val memory = RecordingMemoryGateway()
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Legacy Source",
                    url = "https://example.org/legacy",
                    domain = "example.org",
                    excerpt = "Legacy excerpt evidence.",
                ),
            ),
        )

        val report = learnCuriositySources(
            memory = memory,
            result = result,
            sessionId = "session-legacy",
            requestId = "curiosity-legacy",
        )

        assertTrue(report.learned)
        assertEquals("Legacy excerpt evidence.", memory.requests.single().content)
        assertFalse(report.synthesisStored)
    }

    @Test
    fun sourceFailurePreservesNativeDiagnosticReason() = runBlocking {
        CuriosityPublicAuditBridge.clearForTests()
        val memory = RecordingMemoryGateway(failExternal = true)
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "A public fact is 7319.",
                    rawContent = "Full public article body.",
                ),
            ),
        )

        val report = learnCuriositySources(
            memory = memory,
            result = result,
            sessionId = "session-error",
            requestId = "curiosity-error",
            acquiredTime = "2026-08-30T12:00:50Z",
        )

        assertFalse(report.learned)
        assertEquals(1, report.failedSourceCount)
        assertTrue(report.failureReason?.contains("status=2") == true)
    }

    @Test
    fun flushFailureDoesNotPretendDurabilitySucceeded() = runBlocking {
        CuriosityPublicAuditBridge.clearForTests()
        val memory = RecordingMemoryGateway(failFlush = true)
        val result = CuriosityResult(
            sourceText = "public material",
            sources = listOf(
                CuriositySource(
                    title = "Source A",
                    url = "https://pt.wikipedia.org/wiki/A",
                    domain = "pt.wikipedia.org",
                    excerpt = "A public fact is 7319.",
                    rawContent = "Full public article body.",
                ),
            ),
        )

        val report = learnCuriositySources(
            memory = memory,
            result = result,
            sessionId = "session-2",
            requestId = "curiosity-2",
            acquiredTime = "2026-08-30T12:01:00Z",
        )

        assertTrue(report.learned)
        assertTrue(report.flushFailed)
        assertEquals(listOf("memory-1"), report.storedMemoryIds)
        assertEquals(1, memory.flushCount)

        val audit = CuriosityPublicAuditBridge.peek("curiosity-2")
        assertEquals(report.toPublicKnowledgeAudit(), audit)
        assertTrue(audit?.flushFailed == true)
    }
}

private class RecordingMemoryGateway(
    private val failFlush: Boolean = false,
    private val failExternal: Boolean = false,
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
        if (failExternal) error("Memoria.ia external_public status=2 response={\"status\":\"INVALID_ARGUMENT\"}")
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
