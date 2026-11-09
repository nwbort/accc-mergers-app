package fyi.mergers.app

import fyi.mergers.app.data.IndustryDivisionFile
import fyi.mergers.app.data.IndustryDivisions
import fyi.mergers.app.data.IndustryIndex
import fyi.mergers.app.data.IndustryPhase
import fyi.mergers.app.data.Manifest
import fyi.mergers.app.data.ManifestHash
import fyi.mergers.app.data.MergerEvent
import fyi.mergers.app.data.Reasons
import fyi.mergers.app.data.StatementBlock
import fyi.mergers.app.data.TableItem
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
import fyi.mergers.app.notify.Push
import fyi.mergers.app.notify.Wording
import fyi.mergers.app.tracking.FollowedIndustry
import fyi.mergers.app.tracking.TrackedEvents
import fyi.mergers.app.tracking.TrackingState
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
            "https://mergers.fyi/mergers/MN-01016/Asahi%20warehouse%20lease.pdf?raw=1",
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
        assertEquals("New merger: ${filed.mergerName}", filed.short)
    }

    @Test
    fun notifications_use_short_wording() {
        mapOf(
            "Phase 1 - initial assessment determination: Approved" to "Phase 1: Approved",
            "Phase 2 - detailed assessment determination: Not approved" to "Phase 2: Not approved",
            "Waiver application determination: Approved" to "Waiver: Approved",
            "Merger notified to ACCC" to "Merger notified",
            "Timeline extended by 15 business days – following request by parties" to "Extended by 15 business days",
            "Timeline extended by 15 business days – following request by Notifying Party to extend the period to respond to the ACCC's Notice of Competition Concerns"
                to "Extended by 15 business days",
            "Questionnaire released for Coles acquisition of supermarket and liquor site in Kalgoorlie, WA" to "Questionnaire released",
            "Remedy Offer Questionnaire - IAG - RACI" to "Remedy questionnaire",
            "Remedy Offer - 1 July 2026 - IAG - RACI" to "Remedy offered",
            "ACCC decided notification is subject to Phase 2 review" to "Moved to Phase 2",
            "Trescal - TR Calibration - Phase 2 Notice" to "Moved to Phase 2",
            "Summary of Notice of Competition Concerns" to "Competition concerns notice",
            "Phase 1 consulation" to "Phase 1 consultation",
            "Consideration of Notification ceased – Phase 2 fee not paid by due date" to "Assessment ceased",
            "Tribunal appeal – Determination" to "Tribunal: Determination",
            "Federal Court – Orders" to "Federal Court: Orders",
            "IAG-RACI - Phase 2 Annexure A" to "IAG-RACI - Phase 2 Annexure A",
        ).forEach { (title, short) -> assertEquals(title, short, Wording.short(title)) }

        val determined = division("G").node("4110")!!.let(TrackedEvents::industry)
            .first { it.title == TrackedEvents.INDUSTRY_DETERMINATION }
        assertTrue(determined.short, determined.short!!.matches(Regex("""(Phase \d|Waiver) (not )?approved( with conditions)?: .+""")))
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
    fun reasons_drop_the_boilerplate_and_keep_the_findings() {
        val event = detail("MN-01016").events.first { it.determination_table_content.isNotEmpty() }
        val blocks = Reasons.forEvent(event)
        assertEquals(
            Reasons.Block.Paragraph(
                "The ACCC has determined that the Acquisition may be put into effect as it considers that the " +
                    "Acquisition is unlikely to have the effect of substantially lessening competition in any market. " +
                    "In reaching its decision, and based on the material before it, the ACCC makes the following findings:",
            ),
            blocks.first(),
        )
        val bullets = blocks[1] as Reasons.Block.Bullets
        assertEquals(
            listOf(
                "there is no horizontal competitive overlap",
                "there are sufficient suitable alternative sites available for Asahi’s competitors",
                "the acquisition does not otherwise limit or prevent competition from rivals.",
            ),
            bullets.items.map { it.text },
        )
        assertEquals(2, blocks.size)
    }

    @Test
    fun waiver_reasons_keep_lettered_items() {
        val event = detail("WA-01087").events.first { it.determination_table_content.isNotEmpty() }
        val blocks = Reasons.forEvent(event)
        assertTrue(blocks.none { it is Reasons.Block.Paragraph && Reasons.isBoilerplate(it.text) })
        assertTrue((blocks.first() as Reasons.Block.Paragraph).text.startsWith("Based on the information provided"))
        val letters = blocks.filterIsInstance<Reasons.Block.Lettered>().single()
        assertEquals("a.", letters.items.first().first)
    }

    @Test
    fun pdf_line_breaks_are_told_apart() {
        assertEquals(
            "A post-acquisition firm.\n\n• one\n\no sub point\n\nb. lettered",
            Reasons.clean("A post-\nacquisition\nfirm.\n• one\no sub point\nb. lettered"),
        )
    }

    @Test
    fun statement_of_reasons_is_shown_over_the_placeholder_row() {
        val json = """{"title": "Determination", "determination_table_content": [
            {"item": "Reasons for determination", "details": "The reasons are set out in section 2."}],
          "determination_statement_of_reasons": [
            {"type": "paragraph", "number": "2.1", "text": "When making a determination in Phase 1, the Australian Competition and Consumer Commission (ACCC) undertakes"},
            {"type": "heading", "text": "Industry background"},
            {"type": "paragraph", "number": "2.2", "text": "Komatsu supplies trucks."},
            {"type": "bullet_list", "items": ["one", "two"], "sub_items": [["a sub"], []]},
            {"type": "lettered_list", "items": [{"letter": "a", "text": "Large haul trucks"}]}]}"""
        val event = MergersJson.decodeFromString(MergerEvent.serializer(), json)
        assertEquals(
            listOf(
                Reasons.Block.Heading("Industry background"),
                Reasons.Block.Paragraph("Komatsu supplies trucks.", "2.2"),
                Reasons.Block.Bullets(listOf(Reasons.Block.Bullet("one", listOf("a sub")), Reasons.Block.Bullet("two"))),
                Reasons.Block.Lettered(listOf("(a)" to "Large haul trucks")),
            ),
            Reasons.forEvent(event),
        )
        // Without the statement, the placeholder alone shows nothing.
        assertTrue(Reasons.forEvent(event.copy(determination_statement_of_reasons = emptyList<StatementBlock>())).isEmpty())
        assertTrue(Reasons.forEvent(MergerEvent(determination_table_content = listOf(TableItem("Other", "x")))).isEmpty())
    }

    @Test
    fun manifest_hash_matches_the_site() {
        // Pinned by test_file_hash_is_pinned in the site's
        // scripts/tests/test_data_manifest.py: change both or neither.
        assertEquals("5161c4b975b1", ManifestHash.of("{\"merger_id\":\"MN-01016\",\"merger_name\":\"Asahi – Warehouse\"}"))
    }

    @Test
    fun manifest_parses_the_site_shape() {
        val m = MergersJson.decodeFromString(
            Manifest.serializer(),
            """{"format":1,"mergers":{"MN-01016":"38a5f2df2aea"},"phase2_current":["MN-01072"],"industries":{"G":"0123456789ab"},"later":true}""",
        )
        assertEquals(Manifest.FORMAT, m.format)
        assertEquals(mapOf("MN-01016" to "38a5f2df2aea"), m.mergers)
        assertEquals(listOf("MN-01072"), m.phase2_current)
        assertEquals(mapOf("G" to "0123456789ab"), m.industries)
    }

    @Test
    fun push_topics_follow_what_is_followed() {
        // Nothing followed: no topics, so no token is ever asked for.
        assertEquals(emptySet<String>(), Push.topics(TrackingState()))

        val state = TrackingState(
            tracked = listOf("MN-01016", "WA-01087"),
            industries = listOf(
                FollowedIndustry("4511", "Cafes and Restaurants"),
                FollowedIndustry("H", "Accommodation and Food Services"),
                FollowedIndustry("9999", "Not an industry"),
            ),
        )
        // Names scripts/push/fcm.py sends to; industries by their division file.
        assertEquals(
            setOf("all", "merger-MN-01016", "merger-WA-01087", "industry-H", "industry-_orphans"),
            Push.topics(state),
        )
        assertEquals(setOf("all", "list"), Push.topics(TrackingState(notifyNewFilings = true)))
        assertTrue("list" in Push.topics(state.copy(autoTrackPhase2 = true)))

        // Every name is one FCM accepts.
        val valid = Regex("[A-Za-z0-9_.~%-]+")
        Push.topics(state.copy(autoTrackPhase2 = true)).forEach { assertTrue(it, valid.matches(it)) }
    }
}
