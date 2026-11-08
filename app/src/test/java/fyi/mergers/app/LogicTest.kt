package fyi.mergers.app

import fyi.mergers.app.data.IndustryDivisionFile
import fyi.mergers.app.data.IndustryDivisions
import fyi.mergers.app.data.IndustryIndex
import fyi.mergers.app.data.IndustryPhase
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerList
import fyi.mergers.app.data.MergersJson
import fyi.mergers.app.data.SortOrder
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.documentUrl
import fyi.mergers.app.data.headerStatus
import fyi.mergers.app.data.node
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.outcomeText
import fyi.mergers.app.data.phaseStatus
import fyi.mergers.app.data.sortedBy
import fyi.mergers.app.tracking.TrackedEvents
import fyi.mergers.app.ui.reflow
import kotlinx.serialization.Serializable
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

    @Serializable
    private data class DivisionCases(val cases: List<DivisionCase>)

    @Serializable
    private data class DivisionCase(val code: String, val file: String)

    /**
     * The site's golden fixture (accc-mergers: fixtures/industry-division-cases.json),
     * which pins the same rule in its JS and Python. If this fails, the app is
     * asking for the wrong file and industry pages come up empty.
     */
    @Test
    fun industry_files_are_found_as_the_site_finds_them() {
        val text = Fixtures::class.java.classLoader!!.getResource("fixtures/industry-division-cases.json")!!.readText()
        val cases = MergersJson.decodeFromString(DivisionCases.serializer(), text).cases
        assertTrue(cases.size > 50)
        cases.forEach { c -> assertEquals(c.code, "industries/${c.file}", IndustryDivisions.path(c.code)) }

        // And every node in the snapshot is in the file the rule names.
        ('A'..'S').forEach { d ->
            val file = division("$d")
            assertEquals("$d", file.division)
            file.nodes.keys.forEach { code -> assertEquals(code, "$d", IndustryDivisions.stem(code)) }
        }
    }

    private fun division(stem: String): IndustryDivisionFile =
        MergersJson.decodeFromString(IndustryDivisionFile.serializer(), Fixtures.read("industries/$stem.json")!!)

    @Test
    fun an_industry_node_is_unpacked_like_the_site() {
        val g = division("G")
        val supermarkets = g.node("4110")!!
        assertEquals("Supermarket and Grocery Stores", supermarkets.name)
        assertEquals("Class", supermarkets.levelLabel)
        // The breadcrumb is rebuilt from the parents, division first.
        assertEquals(listOf("G", "41", "411"), supermarkets.ancestors.map { it.code })
        assertEquals("Retail Trade", supermarkets.ancestors.first().name)
        assertEquals(26, supermarkets.mergers.size)
        assertEquals("MN-40049", supermarkets.mergers.first().merger_id)
        assertEquals(listOf(2, 15, 2), listOf(supermarkets.phase2Count, supermarkets.waiverCount, supermarkets.activeCount))

        // Each child's count comes off its own entry.
        val retail = g.node("G")!!
        assertTrue(retail.ancestors.isEmpty())
        assertEquals(listOf("39", "40", "41", "42", "43"), retail.children.map { it.code })
        assertEquals(47, retail.children.single { it.code == "41" }.count)

        // Grouped as the site groups them.
        val phases = supermarkets.mergers.groupingBy(IndustryPhase::of).eachCount()
        assertEquals(15, phases[IndustryPhase.WAIVER])
        assertEquals(2, phases[IndustryPhase.PHASE_2])

        assertNull(g.node("9999"))
        assertNull(division("_orphans").node("4110"))

        val index = MergersJson.decodeFromString(IndustryIndex.serializer(), Fixtures.read("industries.json")!!)
        assertEquals(26, index.industries.single { it.code == "4110" }.merger_count)
    }

    @Test
    fun industry_event_keys_match_the_sites() {
        val node = division("G").node("4110")!!
        val events = TrackedEvents.industry(node)
        // TrackingContext.jsx: `ind_${industry_code}_${merger_id}_${date}_${display_title}`
        val filed = events.first { it.mergerId == "MN-40049" }
        assertEquals("ind_4110_MN-40049_2026-10-30T12:00:00Z_New merger filed", filed.key)
        assertEquals("4110", filed.industryCode)
        // A filing and, once decided, a determination: never anything upcoming.
        assertEquals(node.mergers.size + node.mergers.count { it.determination_date != null }, events.size)
        assertTrue(events.none { it.upcoming })
    }

    @Test
    fun industry_links() {
        assertEquals("4110", MainActivity.industryCodeFrom("https://mergers.fyi/industries/4110/supermarket-and-grocery-stores"))
        assertEquals("G", MainActivity.industryCodeFrom("https://mergers.fyi/industries/G"))
        assertEquals("41", MainActivity.industryCodeFrom("https://mergers.fyi/industries/41?x=1"))
        assertNull(MainActivity.industryCodeFrom("https://mergers.fyi/industries"))
        assertNull(MainActivity.industryCodeFrom("https://mergers.fyi/mergers/MN-01016"))
        assertNull(MainActivity.mergerIdFrom("https://mergers.fyi/industries/4110"))
    }

    @Test
    fun pdf_text_is_reflowed() {
        assertEquals(
            "The ACCC has determined that the Acquisition may be put into effect.\n• there is no overlap\n• alternatives exist",
            reflow("The ACCC has determined that the\nAcquisition may be put into effect.\n• there is no overlap\n• alternatives\nexist"),
        )
    }
}
