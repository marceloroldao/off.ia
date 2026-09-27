package ia.off

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExportTest {
    @Test
    fun includesPaginatedV2ObservationsWithoutPromotingThemToFacts() = runBlocking {
        val gateway = object : MemoryGateway by UnavailableMemoryGateway {
            override val available = true

            override suspend fun exportSnapshotPage(turnOffset: Int, episodeOffset: Int, limit: Int): String =
                """{"status":"OK","format":"memoria.mobile.diagnostic.v1","abi_version":1,"state_schema":1,"generated_at_unix":9,"organization_id":"local","sequence":0,"counts":{"turns":0,"episodes":0},"turn_page":{"next_offset":null},"episode_page":{"next_offset":null},"turns":[],"episodes":[]}"""

            override suspend fun exportStructuralPage(offset: Int, limit: Int): String = when (offset) {
                0 -> """{"status":"OK","format":"memoria.mobile.structural-text.v1","count":2,"page":{"next_offset":1},"observations":[{"hierarchy_id":"conversation:s1","source_id":"u1","source_kind":"user_turn","sequence":11,"text":"Tenho um gato chamado Alt."}]}"""
                1 -> """{"status":"OK","format":"memoria.mobile.structural-text.v1","count":2,"page":{"next_offset":null},"observations":[{"hierarchy_id":"conversation:s1","source_id":"u2","source_kind":"user_turn","sequence":12,"text":"Também conheço um gato chamado Nino.","reply_to":{"source_id":"u1","sequence":11}}]}"""
                else -> error("offset inesperado: $offset")
            }
        }
        val workspace = ChatWorkspace(mutableListOf(ChatSession(
            id = "s1", title = "Teste", updatedAt = 12,
            messages = mutableListOf(
                ChatMessage(id = "u1", role = "Você", text = "Pergunta", createdAt = 11),
                ChatMessage(id = "u2", role = "Você", text = "Resposta", createdAt = 12,
                    replyTo = ExplicitReplyTarget("u1", 11), replyRecorded = true),
            ),
        )), "s1")
        val result = JSONObject(collectFullMemorySnapshot(
            gateway, workspace, ExportBuildIdentity("alpha-test", "offia-rev", "memoria-rev", true),
        ))
        assertEquals("offia.memoria.export.v2", result.getString("format"))
        assertEquals(0, result.getJSONArray("turns").length())
        val structural = result.getJSONObject("structural")
        assertEquals(2, structural.getInt("count"))
        val entries = structural.getJSONArray("observations")
        assertEquals("u1", entries.getJSONObject(0).getString("source_id"))
        assertEquals("conversation:s1", entries.getJSONObject(1).getString("hierarchy_id"))
        assertTrue(entries.getJSONObject(1).getString("text").contains("Nino"))
        assertEquals("u1", entries.getJSONObject(1).getJSONObject("reply_to").getString("source_id"))
        assertEquals("alpha-test", result.getJSONObject("app").getString("version_name"))
        assertTrue(result.getJSONObject("app").getBoolean("laboratory_mode_enabled"))
        val capture = result.getJSONObject("reply_capture")
        assertEquals("LINKED", capture.getString("status"))
        assertEquals(1, capture.getInt("selected_count"))
        assertEquals(1, capture.getInt("recorded_count"))
        assertEquals(1, capture.getInt("native_link_count"))
        assertEquals(0, capture.getInt("recorded_missing_native_count"))
    }

    @Test
    fun distinguishesUnselectedPendingAndMissingNativeLink() = runBlocking {
        val gateway = object : MemoryGateway by UnavailableMemoryGateway {
            override val available = true
            override suspend fun exportSnapshotPage(turnOffset: Int, episodeOffset: Int, limit: Int) =
                """{"status":"OK","format":"memoria.mobile.diagnostic.v1","counts":{"turns":0,"episodes":0},"turn_page":{"next_offset":null},"episode_page":{"next_offset":null},"turns":[],"episodes":[]}"""
            override suspend fun exportStructuralPage(offset: Int, limit: Int) =
                """{"status":"OK","format":"memoria.mobile.structural-text.v1","count":0,"page":{"next_offset":null},"observations":[]}"""
        }
        val question = ChatMessage(id = "q", role = "Você", text = "Pergunta", createdAt = 11)
        val reply = ChatMessage(id = "r", role = "Você", text = "Resposta", createdAt = 12,
            replyTo = ExplicitReplyTarget("q", 11))
        val session = ChatSession("s1", "Teste", mutableListOf(question), 12)
        val workspace = ChatWorkspace(mutableListOf(session), "s1")
        fun statusOf(json: String) = JSONObject(json).getJSONObject("reply_capture").getString("status")

        assertEquals("NO_SELECTION_RECORDED", statusOf(collectFullMemorySnapshot(gateway, workspace)))
        session.messages.add(reply)
        val pending = JSONObject(collectFullMemorySnapshot(gateway, workspace)).getJSONObject("reply_capture")
        assertEquals("PENDING", pending.getString("status"))
        assertEquals(1, pending.getInt("pending_count"))
        session.messages[1] = reply.copy(replyRecorded = true)
        val missing = JSONObject(collectFullMemorySnapshot(gateway, workspace)).getJSONObject("reply_capture")
        assertEquals("RECORDED_MISSING_NATIVE", missing.getString("status"))
        assertEquals(1, missing.getInt("recorded_missing_native_count"))
    }
}
