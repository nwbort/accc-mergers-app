package fyi.mergers.app.tracking

import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.IndustryMerger
import fyi.mergers.app.data.IndustryNode
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerEvent
import fyi.mergers.app.data.Status

/**
 * One thing that happened (or is due) on a followed merger: a row in the
 * Following inbox and, the first time it is seen, a notification.
 */
data class TrackedEvent(
    val key: String,
    val mergerId: String,
    val mergerName: String,
    val date: String?,
    val title: String,
    val upcoming: Boolean,
    val documentUrl: String? = null,
    val documentUrlGh: String? = null,
    /** Set on a filing or determination surfaced by a followed industry, rather than a followed merger. */
    val industryCode: String? = null,
    val industryName: String? = null,
    /** A notification's wording when the app writes its own (a followed industry's lines); not part of the key. */
    val short: String? = null,
)

/**
 * The tracking rules from the site's TrackingContext.jsx, as pure functions so
 * the background check and the screen can't drift apart, and so they can be
 * tested without a phone.
 */
object TrackedEvents {

    /**
     * Relationships where the followed merger is the earlier matter and its
     * related merger the newer re-filed one. Following a declined waiver also
     * follows the notification it was re-filed as; following the re-filing
     * doesn't pull in the matter it superseded.
     */
    val FORWARD_REFILE = setOf("refiled_as", "suspended_refiled_as")

    /**
     * An event's identity, the same string the site uses: merger, date and
     * title, except that a Tribunal document is keyed by its link, since its
     * title gains a hand-written comment after followers have been told.
     */
    fun key(mergerId: String, date: String?, title: String, appealDocUrl: String? = null): String =
        if (appealDocUrl != null) "${mergerId}_${date}_appeal_$appealDocUrl"
        else "${mergerId}_${date}_$title"

    private fun MergerEvent.label() = display_title ?: title ?: ""

    /** Everything on a merger worth telling a follower: its timeline, then what's due. */
    fun of(m: MergerDetail): List<TrackedEvent> = timeline(m) + upcoming(m)

    fun timeline(m: MergerDetail): List<TrackedEvent> = m.events.map { e ->
        val appealDoc = if (e.is_appeal) (e.url ?: e.url_gh) else null
        TrackedEvent(
            key = key(m.merger_id, e.date, e.label(), appealDoc),
            mergerId = m.merger_id,
            mergerName = m.merger_name,
            date = e.date,
            title = e.label(),
            upcoming = false,
            documentUrl = e.url,
            documentUrlGh = e.url_gh,
        )
    }

    /**
     * Deadlines still ahead, derived from the detail file as the site does
     * (rather than from upcoming-events.json, whose 60-day window would hide a
     * phase 2 determination months out). A deadline that moves gets a new key,
     * so an extension is news too.
     */
    fun upcoming(m: MergerDetail): List<TrackedEvent> {
        val out = mutableListOf<TrackedEvent>()
        fun add(date: String?, title: String) {
            val days = Dates.daysUntil(date) ?: return
            if (days < 0) return
            out += TrackedEvent(key(m.merger_id, date, title), m.merger_id, m.merger_name, date, title, upcoming = true)
        }

        // A Tribunal hearing on a live appeal: the matter is already decided, so
        // this comes before the "already determined" guard below. A hearing over
        // several days stays until its last day has passed.
        val appeal = m.appeal
        if (m.under_appeal && appeal?.hearing_date != null) {
            val end = appeal.hearing_end_date?.takeIf { it > appeal.hearing_date }
            if ((Dates.daysUntil(end ?: appeal.hearing_date) ?: -1) >= 0) {
                out += TrackedEvent(
                    key(m.merger_id, appeal.hearing_date, "Tribunal hearing"),
                    m.merger_id, m.merger_name, appeal.hearing_date, "Tribunal hearing", upcoming = true,
                )
            }
        }
        if (m.under_judicial_review) {
            m.judicial_reviews.filter { it.status != "concluded" }.forEach { review ->
                review.listings.filter { it.hearing && it.date != null }.forEach { listing ->
                    if ((Dates.daysUntil(listing.date) ?: -1) > 0) add(listing.date, "Federal Court hearing")
                }
            }
        }

        // The register sometimes publishes the outcome before the publication
        // date is scraped, so either one means there's nothing left due.
        if (m.determination_publication_date != null || m.accc_determination != null) return out
        if (m.is_waiver) return out
        if (m.status == Status.ASSESSMENT_SUSPENDED || m.status == Status.ASSESSMENT_CEASED) return out

        add(
            m.end_of_determination_period,
            if (m.public_benefit_in_progress) "Public benefit determination due" else "Determination due",
        )
        add(m.consultation_response_due_date, "Consultation responses due")
        add(m.public_benefit_assessment_date, "Public benefit assessment")
        add(m.public_benefit_response_date, "Public benefit responses due")
        add(m.competition_concerns_notice_date, "Notice of competition concerns")
        return out
    }

    const val INDUSTRY_NEW_MERGER = "New merger filed"
    const val INDUSTRY_DETERMINATION = "Determination published"

    /**
     * What a followed industry flags: each merger filed in it, and each
     * determination published. Keyed as the site keys them, scoped to the
     * industry ("ind_{code}_…"), so the same merger under two followed
     * industries, or followed itself as well, stays distinct.
     *
     * A waiver application only reaches the register once it has been
     * decided, so a decided waiver is flagged once, by its outcome ("Waiver
     * approved"), not as a new filing as well.
     */
    fun industry(node: IndustryNode): List<TrackedEvent> = node.mergers.flatMap { m ->
        val decidedWaiver = m.is_waiver && m.determination_date != null
        listOfNotNull(
            m.notification_date?.takeUnless { decidedWaiver }?.let { INDUSTRY_NEW_MERGER to it },
            m.determination_date?.let { INDUSTRY_DETERMINATION to it },
        ).map { (title, date) ->
            TrackedEvent(
                key = "ind_${node.code}_${key(m.merger_id, date, title)}",
                mergerId = m.merger_id,
                mergerName = m.merger_name,
                date = date,
                title = title,
                upcoming = false,
                industryCode = node.code,
                industryName = node.name,
                short = "${industryLabel(m, title)}: ${m.merger_name}",
            )
        }
    }

    /** "New merger", "New waiver", "Phase 1 approved", "Waiver not approved", "Phase 2 approved with conditions". */
    private fun industryLabel(m: IndustryMerger, title: String): String {
        if (title == INDUSTRY_NEW_MERGER) return if (m.is_waiver) "New waiver" else "New merger"
        val stage = m.phase ?: if (m.is_waiver) "Waiver" else "Determination"
        val outcome = m.determination?.lowercase() ?: return "$stage determined"
        return "$stage $outcome" + if (m.has_conditions && outcome == "approved") " with conditions" else ""
    }

    /** The newer matters a followed merger was re-filed as. */
    fun forwardRefile(m: MergerDetail): String? =
        m.related_merger?.takeIf { it.relationship in FORWARD_REFILE }?.merger_id

    /** Newest first, and each event once. */
    fun inboxOrder(events: List<TrackedEvent>): List<TrackedEvent> =
        events.distinctBy { it.key }.sortedByDescending { it.date ?: "" }
}
