package fyi.mergers.app.data

/**
 * The kinds of event recent-events.json carries, as the site's
 * frontend/src/constants/recentEventTypes.js: a label and whether the
 * dashboard's Recent events list shows it. Keep the two in step.
 *
 * The feed publishes every type; hidden here, as on the site, are
 * notifications, phase 1 and waiver determinations (the dashboard already
 * lists them) and the routine phase 1 consultation every notification gets.
 *
 * Declaration order is significance: within a day the most consequential
 * events come first ([rank]).
 */
enum class RecentEventType(val key: String, val label: String, val shown: Boolean = true) {
    TRIBUNAL("tribunal", "Tribunal"),
    FEDERAL_COURT("federal_court", "Federal Court"),
    PHASE_2_DETERMINATION("phase_2_determination", "Phase 2 determination"),
    REASONS("reasons", "Reasons"),
    UNDERTAKING_ACCEPTED("undertaking_accepted", "Undertaking accepted"),
    CEASED("ceased", "Ceased"),
    PHASE_2_REFERRAL("phase_2_referral", "Phase 2 referral"),
    COMPETITION_CONCERNS("competition_concerns", "Concerns notice"),
    PUBLIC_BENEFIT("public_benefit", "Public benefit"),
    REMEDY_OFFER("remedy_offer", "Remedy offer"),
    REMEDY_QUESTIONNAIRE("remedy_questionnaire", "Remedy consultation"),
    PHASE_2_CONSULTATION("phase_2_consultation", "Phase 2 consultation"),
    MATERIALLY_INCOMPLETE("materially_incomplete", "Materially incomplete"),
    NOTIFICATION_COMPLETED("notification_completed", "Notification re-completed"),
    TIMELINE_EXTENSION("timeline_extension", "Timeline extended"),
    OTHER("other", "Other"),
    PHASE_1_DETERMINATION("phase_1_determination", "Phase 1 determination", shown = false),
    WAIVER_DETERMINATION("waiver_determination", "Waiver determination", shown = false),
    NOTIFIED("notified", "Notified", shown = false),
    QUESTIONNAIRE("questionnaire", "Consultation", shown = false),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** A type this app doesn't know yet (the site's classifier grew one) reads as Other rather than vanishing. */
        fun of(key: String?): RecentEventType = byKey[key] ?: OTHER
    }
}

object RecentEventDays {
    /**
     * The events the dashboard lists, a day at a time (YYYY-MM-DD), newest
     * day first, and within a day the most significant type first, then by
     * matter so the order is stable (RecentEventsTimeline.jsx).
     */
    fun of(events: List<RecentEvent>): List<Pair<String, List<RecentEvent>>> =
        events
            .filter { RecentEventType.of(it.type).shown && Dates.day(it.date) != null }
            .sortedWith(
                compareByDescending<RecentEvent> { it.date!!.take(10) }
                    .thenBy { RecentEventType.of(it.type).ordinal }
                    .thenBy { it.merger_name },
            )
            .groupBy { it.date!!.take(10) }
            .toList()
}
