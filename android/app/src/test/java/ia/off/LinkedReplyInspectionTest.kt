package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedReplyInspectionTest {
    @Test
    fun keepsExactTargetEvidenceUnverified() {
        val packet = parseLinkedReplyInspection("""
            {"status":"CANDIDATES","qualified":false,"selection_used":false,
             "answer":null,"evidence_boundary":"explicit_reply_only",
             "evidence_scope":"exact_target","distinct_reply_trails":1,
             "explicit_reply_occurrences":1,"repeat_question_links":0,
             "groups_truncated":false,"groups":[
               {"representative_text":"Auri","occurrences":1,"source_regions":1}]}
        """.trimIndent())
        assertEquals(1, packet.distinctTrails)
        assertEquals("Auri", packet.groups.single().text)
        assertTrue(renderLinkedReplyInspection(packet).contains("não são fatos verificados"))
        assertFalse(renderLinkedReplyInspection(packet).contains("HIT"))
    }

    @Test
    fun showsNoLinksAtOtherTargetAddress() {
        val packet = parseLinkedReplyInspection("""
            {"status":"UNRESOLVED","qualified":false,"selection_used":false,
             "answer":null,"evidence_boundary":"explicit_reply_only",
             "evidence_scope":"exact_target","distinct_reply_trails":0,
             "explicit_reply_occurrences":0,"repeat_question_links":0,
             "groups_truncated":false,"groups":[]}
        """.trimIndent())
        assertTrue(packet.groups.isEmpty())
        assertTrue(renderLinkedReplyInspection(packet).contains("Nenhuma entrada vinculada"))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsGlobalPacketInExactTargetView() {
        parseLinkedReplyInspection("""
            {"status":"CANDIDATES","qualified":false,"selection_used":false,
             "answer":null,"evidence_boundary":"explicit_reply_only",
             "evidence_scope":"matching_targets","distinct_reply_trails":0,
             "explicit_reply_occurrences":0,"repeat_question_links":0,
             "groups_truncated":false,"groups":[]}
        """.trimIndent())
    }
}
