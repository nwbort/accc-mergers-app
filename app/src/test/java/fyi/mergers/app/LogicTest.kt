package fyi.mergers.app

import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerList
import fyi.mergers.app.data.MergersJson
import fyi.mergers.app.data.SortOrder
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.documentUrl
import fyi.mergers.app.data.headerStatus
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.outcomeText
import fyi.mergers.app.data.phaseStatus
import fyi.mergers.app.data.sortedBy
import fyi.mergers.app.tracking.TrackedEvents
import fyi.mergers.app.ui.reflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The rules ported from the site, checked against the real data snapshot. */
class LogicTest {
    @Before
    fun pin() = Fixtures.pinToday()

    private fun detail(id: String): MergerDetail =
        MergersJson.decodeFromString(MergerDetail.serializer(), Fixtures.read("mergers/$id.json")!!)

    @Test
    fun every_fixture_parses() {
        val list = MergersJson.decodeFromString(MergerList.serializer(), Fixtures.read("mergers/list.json")!!)
        assertEquals(40, list.mergers.size)
        listOf("MN-01016", "MN-01068", "MN-20046", "MN-01072", "MN-65005", "WA-01087", "MN-20013").forEach {
            assertEquals(it, detail(it).merger_id)
        }
    }

    @Test
    fun outcome_reads_as_on_the_site() {
        val approved = detail("MN-01016").outcomeInput()
        assertEquals(Status.APPROVED, approved.headerStatus()!!.label)
        assertTrue(approved.headerStatus()!!.decided)
        assertEquals("Phase 1 approved", approved.phaseStatus())

        val live = detail("MN-20046").outcomeInput()
        assertFalse(live.headerStatus()!!.decided)
        assertEquals(Status.UNDER_ASSESSMENT, live.outcomeText())
        assertEquals("Phase 1 under assessment", live.phaseStatus())

        val coles = detail("MN-01068").outcomeInput()
        assertEquals("Phase 2 not approved · Under appeal", coles.phaseStatus())

        assertEquals("Waiver not approved", detail("WA-01087").outcomeInput().phaseStatus())
    }

    @Test
    fun event_keys_match_the_sites() {
        val m = detail("MN-01016")
        val e = TrackedEvents.timeline(m).first { it.title.startsWith("Phase 1") }
        // TrackingContext.jsx: `${merger_id}_${date}_${display_title}`
        assertEquals("MN-01016_2025-09-05T12:00:00Z_Phase 1 - initial assessment determination: Approved", e.key)

        // Tribunal documents are keyed by their link, not their editable title.
        val appealDoc = TrackedEvents.timeline(detail("MN-01068")).first { it.key.contains("_appeal_") }
        assertTrue(appealDoc.key.contains("competitiontribunal.gov.au"))
    }

    @Test
    fun upcoming_deadlines_come_from_the_detail_file() {
        val live = TrackedEvents.upcoming(detail("MN-20046")).map { it.title to it.date!!.take(10) }
        assertEquals(
            listOf("Determination due" to "2026-12-18", "Consultation responses due" to "2026-11-13"),
            live,
        )
        // Decided: nothing due, except the Tribunal hearing on a live appeal.
        assertTrue(TrackedEvents.upcoming(detail("MN-01016")).isEmpty())
        assertEquals(listOf("Tribunal hearing"), TrackedEvents.upcoming(detail("MN-01068")).map { it.title })
    }

    @Test
    fun only_forward_refilings_are_followed() {
        assertEquals("MN-20013", TrackedEvents.forwardRefile(detail("WA-01087")))
        assertNull(TrackedEvents.forwardRefile(detail("MN-20013")))
    }

    @Test
    fun list_sorts_like_the_site() {
        val list = MergersJson.decodeFromString(MergerList.serializer(), Fixtures.read("mergers/list.json")!!).mergers
        val byName = list.sortedBy(SortOrder.NAME).map { it.merger_name.lowercase() }
        assertEquals(byName.sorted(), byName)
        val updated = list.sortedBy(SortOrder.UPDATED).map { it.latest_event_date ?: "" }
        assertEquals(updated.sortedDescending(), updated)
    }

    @Test
    fun links() {
        assertEquals("MN-01016", MainActivity.mergerIdFrom("https://mergers.fyi/mergers/MN-01016/asahi-warehouse"))
        assertEquals("WA-01087", MainActivity.mergerIdFrom("https://mergers.fyi/mergers/WA-01087"))
        assertNull(MainActivity.mergerIdFrom("https://mergers.fyi/mergers"))
        assertNull(MainActivity.mergerIdFrom("https://example.com/mergers/MN-01016"))
        assertEquals(
            "https://mergers.fyi/mergers/MN-01016/Asahi%20warehouse%20lease.pdf",
            documentUrl("/mergers/MN-01016/Asahi warehouse lease.pdf", "https://accc.gov.au/x.pdf"),
        )
        assertEquals("https://accc.gov.au/x.pdf", documentUrl(null, "https://accc.gov.au/x.pdf"))
    }

    @Test
    fun pdf_text_is_reflowed() {
        assertEquals(
            "The ACCC has determined that the Acquisition may be put into effect.\n• there is no overlap\n• alternatives exist",
            reflow("The ACCC has determined that the\nAcquisition may be put into effect.\n• there is no overlap\n• alternatives\nexist"),
        )
    }
}
