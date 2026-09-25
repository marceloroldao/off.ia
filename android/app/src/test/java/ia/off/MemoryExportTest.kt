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
                1 -> """{"status":"OK","format":"memoria.mobile.structural-text.v1","count":2,"page":{"next_offset":null},"observations":[{"hierarchy_id":"conversation:s1","source_id":"u2","source_kind":"user_turn","sequence":12,"text":"Também conheço um gato chamado Nino."}]}"""
                else -> error("offset inesperado: $offset")
            }
        }
        val result = JSONObject(collectFullMemorySnapshot(gateway))
        assertEquals("offia.memoria.export.v2", result.getString("format"))
        assertEquals(0, result.getJSONArray("turns").length())
        val structural = result.getJSONObject("structural")
        assertEquals(2, structural.getInt("count"))
        val entries = structural.getJSONArray("observations")
        assertEquals("u1", entries.getJSONObject(0).getString("source_id"))
        assertEquals("conversation:s1", entries.getJSONObject(1).getString("hierarchy_id"))
        assertTrue(entries.getJSONObject(1).getString("text").contains("Nino"))
    }
}
