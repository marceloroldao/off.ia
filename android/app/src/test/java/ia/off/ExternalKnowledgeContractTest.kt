package ia.off

import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalKnowledgeContractTest {
    private fun source(
        url: String = "https://pt.wikipedia.org/wiki/Oceano",
        domain: String = "pt.wikipedia.org",
        importKind: String = "imported",
        parents: List<String> = emptyList(),
    ) = ExternalKnowledgeSource(
        content = "O oceano é uma massa de água salgada com texto público e aspas \"reais\".",
        sourceUrl = url,
        sourceDomain = domain,
        sourceTitle = "Oceano",
        acquiredTime = "2026-08-31T01:30:00Z",
        sourceExcerpt = "O oceano é uma massa de água salgada.",
        providerId = "wikipedia-curiosity",
        importKind = importKind,
        validationConfidence = 0.85,
        requestId = "curiosity-test:source:0",
        sessionId = "session-test",
        parentMemoryIds = parents,
    )

    @Test
    fun acceptsRealWikipediaStylePublicSource() {
        validateExternalKnowledgeSource(source())
    }

    @Test
    fun rejectsNonHttpSourceUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalKnowledgeSource(source(url = "pt.wikipedia.org/wiki/Oceano"))
        }
    }

    @Test
    fun rejectsMalformedSourceDomain() {
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalKnowledgeSource(source(domain = "pt.wikipedia.org/wiki"))
        }
    }

    @Test
    fun derivedRequiresPublicParentsAtConsumerBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalKnowledgeSource(source(importKind = "derived"))
        }
        validateExternalKnowledgeSource(source(importKind = "derived", parents = listOf("external:1")))
    }
}
