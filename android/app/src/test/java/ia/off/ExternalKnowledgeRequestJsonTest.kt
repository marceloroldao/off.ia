package ia.off

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalKnowledgeRequestJsonTest {
    @Test
    fun `keeps http url literal and emits required Memoria fields`() {
        val json = buildExternalKnowledgeRequestJson(
            ExternalKnowledgeSource(
                content = "A fonte diz: \"oceano\" / água.",
                sourceUrl = "https://pt.wikipedia.org/wiki/Oceano",
                sourceDomain = "pt.wikipedia.org",
                sourceTitle = "Oceano",
                acquiredTime = "2026-08-31T01:00:00Z",
                sourceExcerpt = "O oceano cobre a Terra.",
                providerId = "wikipedia-curiosity",
                importKind = "imported",
                validationConfidence = 0.85,
                requestId = "req:source:0",
                sessionId = "session",
            ),
        )

        assertTrue(json.contains("\"content\":\"A fonte diz: \\\"oceano\\\" / água.\""))
        assertTrue(json.contains("\"source_class\":\"external_public\""))
        assertTrue(json.contains("\"source_url\":\"https://pt.wikipedia.org/wiki/Oceano\""))
        assertFalse(json.contains("https:\\/\\/"))
        assertTrue(json.contains("\"source_domain\":\"pt.wikipedia.org\""))
        assertTrue(json.contains("\"source_title\":\"Oceano\""))
        assertTrue(json.contains("\"acquired_time\":\"2026-08-31T01:00:00Z\""))
        assertTrue(json.contains("\"import_kind\":\"imported\""))
        assertTrue(json.contains("\"parent_memory_ids\":[]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non http source url before native call`() {
        buildExternalKnowledgeRequestJson(
            ExternalKnowledgeSource(
                content = "conteúdo",
                sourceUrl = "pt.wikipedia.org/wiki/Oceano",
                sourceDomain = "pt.wikipedia.org",
                sourceTitle = "Oceano",
                acquiredTime = "2026-08-31T01:00:00Z",
            ),
        )
    }
}
