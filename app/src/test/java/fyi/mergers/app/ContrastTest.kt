package fyi.mergers.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import fyi.mergers.app.data.DigestColor
import fyi.mergers.app.data.RecentEventType
import fyi.mergers.app.data.Status
import fyi.mergers.app.ui.theme.AppealIndigo
import fyi.mergers.app.ui.theme.Dark
import fyi.mergers.app.ui.theme.Light
import fyi.mergers.app.ui.theme.RefiledAmber
import fyi.mergers.app.ui.theme.WaiverAmber
import fyi.mergers.app.ui.theme.digestColors
import fyi.mergers.app.ui.theme.eventTypeColors
import fyi.mergers.app.ui.theme.recentEventColors
import fyi.mergers.app.ui.theme.statusColors
import fyi.mergers.app.ui.theme.statusGlyphColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's own colours against WCAG 2.2: 4.5:1 for text (the badges and
 * flags are 10–12 sp, so never "large"), 3:1 for a glyph that carries
 * meaning (1.4.11). Material's scheme roles are left to Material; these are
 * the pairs the app picks itself, in light and dark.
 */
class ContrastTest {
    private val statuses = listOf(
        Status.UNDER_ASSESSMENT, Status.ASSESSMENT_SUSPENDED, Status.ASSESSMENT_COMPLETED,
        Status.ASSESSMENT_CEASED, Status.APPROVED, Status.NOT_APPROVED, Status.DECLINED,
        Status.NOT_OPPOSED, Status.REFERRED_TO_PHASE_2, Status.APPROVED_WITH_CONDITIONS, null,
    )

    private val eventTypes = listOf(
        "consultation_due", "notice_of_competition_concerns", "public_benefit_assessment",
        "public_benefit_response_due", "determination_due", "tribunal_hearing",
        "federal_court_hearing", "something_new",
    )

    @Test
    fun badge_and_flag_text_reads_on_its_fill() {
        val problems = statuses.map { "status ${it ?: "unknown"}" to statusColors(it) } +
            listOf("waiver" to WaiverAmber, "under appeal" to AppealIndigo, "re-filed" to RefiledAmber)
        problems.forEach { (what, c) -> assertText(what, c.onFill, c.fill) }
    }

    @Test
    fun outcome_glyphs_stand_out_in_both_themes() {
        for (dark in listOf(false, true)) {
            val card = if (dark) Dark.surfaceContainerLow else Light.surfaceContainerLowest
            statuses.forEach {
                val c = statusGlyphColors(it, dark)
                // The symbol on the shape, and the shape on the card it leads.
                assertGlyph("${it ?: "unknown"} symbol, dark=$dark", c.onFill, c.fill)
                if (dark) assertGlyph("${it ?: "unknown"} shape, dark", c.fill, card)
            }
        }
    }

    @Test
    fun event_tiles_show_their_glyph() {
        for (dark in listOf(false, true)) {
            val surface = if (dark) Dark.surface else Light.surface
            val tiles = eventTypes.map { it to eventTypeColors(it, dark) } +
                RecentEventType.entries.map { it.name to recentEventColors(it, dark) }
            tiles.forEach { (what, c) ->
                assertGlyph("$what tile, dark=$dark", c.onFill, c.fill.compositeOver(surface))
            }
        }
    }

    @Test
    fun digest_ink_reads_where_it_is_written() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) Dark else Light
            DigestColor.entries.forEach { color ->
                val c = digestColors(color, dark)
                val what = "$color, dark=$dark"
                // Counts and headings on the page, an outcome on its card, a count on its chip.
                assertText("$what on the page", c.ink, scheme.surface)
                assertText("$what on a card", c.ink, scheme.surfaceContainerLow)
                assertText("$what on its chip", c.ink, c.wash.compositeOver(scheme.surface))
            }
        }
    }

    private fun assertText(what: String, ink: Color, back: Color) = assertAtLeast(4.5, what, ink, back)

    private fun assertGlyph(what: String, ink: Color, back: Color) = assertAtLeast(3.0, what, ink, back)

    private fun assertAtLeast(min: Double, what: String, ink: Color, back: Color) {
        val ratio = contrast(ink.compositeOver(back), back)
        assertTrue("$what: %.2f:1, wants %.1f:1".format(ratio, min), ratio >= min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
