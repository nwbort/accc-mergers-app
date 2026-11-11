package fyi.mergers.app.data

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The site's weekly digest page ("Catch me up", frontend/src/pages/Digest.jsx)
 * as rules: which buckets it shows, in what order, under what names and
 * colours (DIGEST_COLOR_KEYS in constants/mergerStatus.js), and what each row
 * says. Keep it in step with the page.
 */
enum class DigestColor { NEW_MERGER, CLEARED, DECLINED, CEASED, TRIBUNAL_APPEAL, PHASE_2_REFERRAL, PHASE_1, PHASE_2 }

/**
 * The digest's sections in the page's order, each with its anchor ([id], as
 * the weekly email links /digest#mergers-referred), its summary chip's label
 * and its heading. A section with nothing in it isn't shown, chip or table.
 */
enum class DigestSection(
    val id: String,
    val chip: String,
    val title: String,
    val color: DigestColor,
    val pick: (Digest) -> List<DigestMerger>,
) {
    NEW("new-mergers", "New deals notified", "New mergers", DigestColor.NEW_MERGER, { it.new_deals_notified }),
    CLEARED("mergers-approved", "Deals cleared", "Mergers approved", DigestColor.CLEARED, { it.deals_cleared }),
    CEASED("mergers-ceased", "Assessment ceased", "Assessment ceased", DigestColor.CEASED, { it.deals_assessment_ceased }),
    REFERRED(
        "mergers-referred", "Referred to phase 2", "Mergers referred to phase 2",
        DigestColor.PHASE_2_REFERRAL, { it.deals_referred_to_phase_2 },
    ),
    TO_PUBLIC_BENEFIT(
        "mergers-to-public-benefit", "To public benefit phase", "Mergers moved to the public benefit phase",
        DigestColor.PHASE_2, { it.deals_to_public_benefit },
    ),
    DECLINED("mergers-declined", "Deals declined", "Mergers declined", DigestColor.DECLINED, { it.deals_declined }),
    APPEALED("mergers-appealed", "Appealed to tribunal", "Appealed to tribunal", DigestColor.TRIBUNAL_APPEAL, { it.deals_appealed_to_tribunal }),
    ONGOING_PHASE_1(
        "ongoing-phase-1", "Ongoing phase 1", "Ongoing · phase 1 · initial assessment",
        DigestColor.PHASE_1, { it.ongoing_phase_1 },
    ),
    ONGOING_PHASE_2(
        "ongoing-phase-2", "Ongoing phase 2", "Ongoing · phase 2 · detailed assessment",
        DigestColor.PHASE_2, { it.ongoing_phase_2 },
    ),
    ONGOING_PUBLIC_BENEFIT(
        "ongoing-public-benefit", "Public benefit phase", "Ongoing · public benefit phase",
        DigestColor.PHASE_2, { it.ongoing_public_benefit },
    ),
    ONGOING_APPEALS(
        "ongoing-tribunal-appeals", "Ongoing ACT appeals", "Ongoing · tribunal appeals",
        DigestColor.TRIBUNAL_APPEAL, { it.ongoing_tribunal_appeals },
    ),
    ;

    companion object {
        fun byId(id: String?): DigestSection? = entries.firstOrNull { it.id == id }
    }
}

/** One merger's row in a section: the facts under its name, then what was decided (and its document), then its summary. */
data class DigestRow(
    val merger: DigestMerger,
    val facts: List<String>,
    val outcome: String? = null,
    val outcomeNote: String? = null,
    val document: String? = null,
    val summary: String? = null,
)

/** A run of rows under a sub-heading (approved and declined split by phase), or with none. */
data class DigestGroup(val label: String?, val rows: List<DigestRow>)

object Digests {
    private val month = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("en-AU"))

    /** The sections with something in them, in the page's order. */
    fun sections(d: Digest): List<Pair<DigestSection, List<DigestMerger>>> =
        DigestSection.entries.map { it to it.pick(d) }.filter { it.second.isNotEmpty() }

    /**
     * The week in a line, for the dashboard: what was filed and decided, not
     * what's still running. "11 notified · 33 cleared", or a quiet week.
     */
    fun headline(d: Digest): String {
        val words = listOf(
            DigestSection.NEW to "notified",
            DigestSection.CLEARED to "cleared",
            DigestSection.REFERRED to "to phase 2",
            DigestSection.TO_PUBLIC_BENEFIT to "to public benefit",
            DigestSection.DECLINED to "declined",
            DigestSection.CEASED to "ceased",
            DigestSection.APPEALED to "appealed",
        )
        return words.mapNotNull { (s, word) -> s.pick(d).size.takeIf { it > 0 }?.let { "$it $word" } }
            .joinToString(" · ")
            .ifEmpty { "A quiet week: nothing filed or decided" }
    }

    /** "26 October to 1 November 2026", "2–8 November 2026", as formatDateRange. */
    fun period(d: Digest): String? {
        val a = Dates.day(d.period_start) ?: return null
        val b = Dates.day(d.period_end) ?: return null
        return when {
            a == b -> "${a.dayOfMonth} ${month.format(a)} ${a.year}"
            a.month == b.month && a.year == b.year -> "${a.dayOfMonth}–${b.dayOfMonth} ${month.format(b)} ${b.year}"
            else -> "${a.dayOfMonth} ${month.format(a)} to ${b.dayOfMonth} ${month.format(b)} ${b.year}"
        }
    }

    /** The first paragraph of more than one word, as getFirstParagraph. */
    fun firstParagraph(description: String?): String? {
        val paras = description.orEmpty().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        return paras.firstOrNull { p ->
            p.replace("*", "").trim().split(Regex("\\s+")).size > 1
        } ?: paras.firstOrNull()
    }

    /** The newest determination document's mirror, as getDeterminationPdf. */
    fun determinationDocument(m: DigestMerger): String? =
        m.events
            .filter { it.display_title?.contains("determination") == true && !it.url_gh.isNullOrBlank() }
            .maxByOrNull { it.date.orEmpty() }
            ?.let { documentUrl(it.url_gh, null) }

    /** The determination a decided row shows: the ACCC's, else either phase's, else the section's own word. */
    private fun determination(m: DigestMerger, default: String): String =
        m.accc_determination ?: m.phase_1_determination ?: m.phase_2_determination ?: default

    /** appeal_type's label (constants/appeal.js APPEAL_TYPE_LABELS). */
    fun appealLabel(type: String?): String = when (type) {
        "party_denial" -> "Appeal against refusal"
        "party_conditional_clearance" -> "Appeal against conditional clearance"
        "third_party_clearance" -> "Third party appeal against clearance"
        else -> "Under review at the Competition Tribunal"
    }

    private fun appealNote(a: AppealSummary?): String? =
        listOfNotNull(a?.appellant?.let { "Lodged by $it" }, a?.tribunal_number).joinToString(" · ").ifEmpty { null }

    private fun on(label: String, date: String?, missing: String = "N/A") = "$label ${Dates.format(date) ?: missing}"

    fun row(section: DigestSection, m: DigestMerger): DigestRow = when (section) {
        DigestSection.NEW -> DigestRow(
            m, listOf(on("Notified", m.effective_notification_datetime)), summary = firstParagraph(m.merger_description),
        )
        DigestSection.CLEARED -> decided(m, Status.APPROVED)
        DigestSection.DECLINED -> decided(m, Status.NOT_APPROVED)
        DigestSection.CEASED -> DigestRow(m, listOf(on("Ceased", m.ceased_date), m.stage ?: "N/A"))
        DigestSection.REFERRED -> DigestRow(
            m, listOf(on("Referred", m.phase_1_determination_date)),
            outcome = determination(m, Status.REFERRED_TO_PHASE_2), document = determinationDocument(m),
        )
        // The determination the application seeks to overturn: the Phase 2
        // outcome, or a conditional Phase 1 clearance.
        DigestSection.TO_PUBLIC_BENEFIT -> DigestRow(
            m, listOf(on("Applied", m.public_benefit_application_date)),
            outcome = m.phase_2_determination ?: m.phase_1_determination ?: "N/A",
        )
        DigestSection.APPEALED -> DigestRow(
            m, listOf(on("Filed", m.appeal?.filed_date)),
            outcome = appealLabel(m.appeal?.appeal_type), outcomeNote = appealNote(m.appeal),
        )
        DigestSection.ONGOING_PHASE_1, DigestSection.ONGOING_PHASE_2, DigestSection.ONGOING_PUBLIC_BENEFIT -> DigestRow(
            m,
            listOf(on("Notified", m.effective_notification_datetime), on("Determination due", m.end_of_determination_period)),
            summary = firstParagraph(m.merger_description),
        )
        DigestSection.ONGOING_APPEALS -> DigestRow(
            m, listOf(on("Filed", m.appeal?.filed_date), on("Hearing", m.appeal?.hearing_date, missing = "TBC")),
            outcome = appealLabel(m.appeal?.appeal_type), outcomeNote = appealNote(m.appeal),
        )
    }

    private fun decided(m: DigestMerger, default: String) = DigestRow(
        m, listOf(on("Determined", m.determination_publication_date)),
        outcome = determination(m, default), document = determinationDocument(m),
    )

    /**
     * A section's rows. Approved and declined are split as GroupedDeterminationSection
     * does: decided at Phase 2, at Phase 1, and the rest (waivers).
     */
    fun groups(section: DigestSection, mergers: List<DigestMerger>): List<DigestGroup> {
        val match = when (section) {
            DigestSection.CLEARED -> setOf(Status.APPROVED)
            DigestSection.DECLINED -> setOf(Status.NOT_APPROVED, Status.DECLINED)
            else -> return listOf(DigestGroup(null, mergers.map { row(section, it) }))
        }
        val phase2 = mergers.filter { it.phase_2_determination in match }
        val phase1 = mergers.filter { it.phase_1_determination in match && it.phase_2_determination !in match }
        val rest = mergers.filter { it.phase_2_determination !in match && it.phase_1_determination !in match }
        return listOf(
            "Phase 2 · detailed assessment" to phase2,
            "Phase 1 · initial assessment" to phase1,
            "Waiver" to rest,
        ).filter { it.second.isNotEmpty() }.map { (label, ms) -> DigestGroup(label, ms.map { row(section, it) }) }
    }
}
