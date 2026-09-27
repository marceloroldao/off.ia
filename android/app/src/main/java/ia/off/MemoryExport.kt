package ia.off

import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_PAGE_LIMIT = 64

data class ExportBuildIdentity(
    val versionName: String,
    val offiaCommit: String,
    val memoriaCommit: String,
    val laboratoryModeEnabled: Boolean,
)

/**
 * Collects Memoria.ia's bounded diagnostic pages into one portable OFF.IA file.
 * The semantic content is copied verbatim; OFF.IA does not interpret or mutate it.
 */
suspend fun collectFullMemorySnapshot(
    memory: MemoryGateway,
    workspace: ChatWorkspace? = null,
    build: ExportBuildIdentity? = null,
): String {
    check(memory.available) { "Memoria.ia indisponível" }

    var turnOffset = 0
    var episodeOffset = 0
    var nextTurn: Int? = 0
    var nextEpisode: Int? = 0
    var firstPage: JSONObject? = null
    val turns = JSONArray()
    val episodes = JSONArray()
    val structuralObservations = JSONArray()

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
    var structuralOffset: Int? = 0
    var structuralCount = 0
    var structuralFormat: String? = null
    while (structuralOffset != null) {
        val raw = memory.exportStructuralPage(structuralOffset, EXPORT_PAGE_LIMIT)
            ?: error("Memoria.ia não retornou a trilha estrutural V2")
        val page = JSONObject(raw)
        check(page.optString("status") == "OK") { "Exportação estrutural inválida" }
        if (structuralFormat == null) structuralFormat = page.getString("format")
        structuralCount = page.getInt("count")
        page.getJSONArray("observations").let { observations ->
            for (index in 0 until observations.length()) structuralObservations.put(observations.get(index))
        }
        val next = nextOffset(page.getJSONObject("page"))
        check(next == null || next > structuralOffset) { "Página estrutural não avançou" }
        structuralOffset = next
    }
    check(structuralObservations.length() == structuralCount) {
        "Exportação estrutural incompleta"
    }
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
        put("format", "offia.memoria.export.v2")
        put("source", sourceMetadata)
        put("turns", turns)
        put("episodes", episodes)
        put("structural", JSONObject().apply {
            put("format", structuralFormat)
            put("count", structuralCount)
            put("observations", structuralObservations)
        })
        build?.let { identity ->
            put("app", JSONObject().apply {
                put("version_name", identity.versionName)
                put("offia_commit", identity.offiaCommit)
                put("memoria_commit", identity.memoriaCommit)
                put("laboratory_mode_enabled", identity.laboratoryModeEnabled)
            })
        }
        workspace?.let { put("reply_capture", replyCaptureSummary(it, structuralObservations)) }
    }.toString(2)
}

/** Counts only explicit UI selections and native links; no semantic classification. */
private fun replyCaptureSummary(workspace: ChatWorkspace, observations: JSONArray): JSONObject {
    val nativeLinks = buildSet {
        for (index in 0 until observations.length()) {
            val item = observations.optJSONObject(index) ?: continue
            if (item.optJSONObject("reply_to") == null) continue
            add(Triple(item.optString("hierarchy_id"), item.optString("source_id"), item.optLong("sequence")))
        }
    }
    var selected = 0
    var recorded = 0
    var pending = 0
    var recordedMissingNative = 0
    var pendingWithNative = 0
    workspace.sessions.forEach { session ->
        session.messages.forEach { message ->
            if (message.role == "Você" && message.replyTo != null) {
                selected++
                val address = Triple("conversation:${session.id}", message.id, message.createdAt)
                if (message.replyRecorded) {
                    recorded++
                    if (address !in nativeLinks) recordedMissingNative++
                } else {
                    pending++
                    if (address in nativeLinks) pendingWithNative++
                }
            }
        }
    }
    val captureStatus = when {
        recordedMissingNative > 0 -> "RECORDED_MISSING_NATIVE"
        pending > 0 -> "PENDING"
        nativeLinks.isNotEmpty() -> "LINKED"
        selected == 0 -> "NO_SELECTION_RECORDED"
        else -> "NO_NATIVE_LINK"
    }
    return JSONObject().apply {
        put("format", "offia.reply-capture.v1")
        put("status", captureStatus)
        put("selected_count", selected)
        put("recorded_count", recorded)
        put("pending_count", pending)
        put("native_link_count", nativeLinks.size)
        put("recorded_missing_native_count", recordedMissingNative)
        put("pending_with_native_count", pendingWithNative)
    }
}

private fun nextOffset(page: JSONObject): Int? =
    if (page.isNull("next_offset")) null else page.getInt("next_offset")
