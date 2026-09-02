package ia.off

import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_PAGE_LIMIT = 64

/**
 * Collects Memoria.ia's bounded diagnostic pages into one portable OFF.IA file.
 * The semantic content is copied verbatim; OFF.IA does not interpret or mutate it.
 */
suspend fun collectFullMemorySnapshot(memory: MemoryGateway): String {
    check(memory.available) { "Memoria.ia indisponível" }

    var turnOffset = 0
    var episodeOffset = 0
    var nextTurn: Int? = 0
    var nextEpisode: Int? = 0
    var firstPage: JSONObject? = null
    val turns = JSONArray()
    val episodes = JSONArray()

    while (nextTurn != null || nextEpisode != null) {
        val raw = memory.exportSnapshotPage(turnOffset, episodeOffset, EXPORT_PAGE_LIMIT)
            ?: error("Memoria.ia não retornou o snapshot")
        val page = JSONObject(raw)
        if (firstPage == null) firstPage = page

        page.optJSONArray("turns")?.let { pageTurns ->
            for (index in 0 until pageTurns.length()) turns.put(pageTurns.get(index))
        }
        page.optJSONArray("episodes")?.let { pageEpisodes ->
            for (index in 0 until pageEpisodes.length()) episodes.put(pageEpisodes.get(index))
        }

        val counts = page.getJSONObject("counts")
        nextTurn = nextOffset(page.getJSONObject("turn_page"))
        nextEpisode = nextOffset(page.getJSONObject("episode_page"))
        turnOffset = nextTurn ?: counts.getInt("turns")
        episodeOffset = nextEpisode ?: counts.getInt("episodes")
    }

    val source = requireNotNull(firstPage) { "Snapshot vazio" }
    val sourceMetadata = JSONObject().apply {
        put("format", source.optString("format"))
        put("abi_version", source.optInt("abi_version"))
        put("state_schema", source.optInt("state_schema"))
        put("generated_at_unix", source.optLong("generated_at_unix"))
        put("organization_id", source.optString("organization_id"))
        put("sequence", source.optLong("sequence"))
        put("counts", source.getJSONObject("counts"))
    }

    return JSONObject().apply {
        put("status", "OK")
        put("format", "offia.memoria.export.v1")
        put("source", sourceMetadata)
        put("turns", turns)
        put("episodes", episodes)
    }.toString(2)
}

private fun nextOffset(page: JSONObject): Int? =
    if (page.isNull("next_offset")) null else page.getInt("next_offset")
