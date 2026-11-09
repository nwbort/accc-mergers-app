package fyi.mergers.app.widget

import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.UpcomingEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The kinds of date a Coming up widget can leave out: the site's event types
 * (EVENT_TYPES in UpcomingEventsTimeline.jsx), grouped as their tiles are
 * coloured. A type the app doesn't know yet is [OTHER], which is always shown.
 */
enum class EventKind(val label: String, val types: List<String>) {
    DETERMINATION("Determinations due", listOf("determination_due")),
    CONSULTATION("Consultation responses due", listOf("consultation_due")),
    CONCERNS("Notices of competition concerns", listOf("notice_of_competition_concerns")),
    PUBLIC_BENEFIT(
        "Public benefit assessments",
        listOf("public_benefit_assessment", "public_benefit_response_due"),
    ),
    HEARING("Tribunal and court hearings", listOf("tribunal_hearing", "federal_court_hearing")),
    OTHER("Other dates", emptyList());

    companion object {
        /** The kinds there's a choice about. */
        val choices: List<EventKind> = entries - OTHER

        fun of(type: String?): EventKind = entries.firstOrNull { type in it.types } ?: OTHER
    }
}

/** One Coming up widget's settings. Kinds are stored by what's hidden, so a new kind shows. */
data class UpcomingConfig(val followedOnly: Boolean = false, val hidden: Set<EventKind> = emptySet()) {
    /**
     * What the widget says under its title: only that it's keeping to the
     * followed mergers. Which kinds of date it leaves out goes unsaid.
     */
    val summary: String? get() = if (followedOnly) "Mergers you follow" else null
}

/** A line of the widget's list: a day, then what falls on it. */
sealed interface UpcomingRow {
    data class Day(val label: String) : UpcomingRow
    data class Event(val event: UpcomingEvent, val detail: String) : UpcomingRow
}

object Upcoming {
    /** As many dates as a widget holds: a scroll or two, not the whole 60-day feed. */
    const val MAX = 40

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-AU"))

    /** The day an event is listed under: a hearing already under way counts as today. */
    private fun day(e: UpcomingEvent): LocalDate? = Dates.day(e.date)?.let { maxOf(it, Dates.today()) }

    /** What [config] shows of the site's upcoming events, soonest first. */
    fun events(all: List<UpcomingEvent>, config: UpcomingConfig, followed: Set<String>): List<UpcomingEvent> =
        all.filter { e ->
            val left = Dates.daysUntil(e.end_date ?: e.date) ?: return@filter false
            left >= 0 && EventKind.of(e.type) !in config.hidden && (!config.followedOnly || e.merger_id in followed)
        }.sortedBy { it.date }.take(MAX)

    fun rows(events: List<UpcomingEvent>): List<UpcomingRow> = buildList {
        events.groupBy(::day).forEach { (day, onDay) ->
            add(UpcomingRow.Day(dayLabel(day)))
            onDay.forEach { e ->
                val until = e.end_date?.takeIf { Dates.day(it) != Dates.day(e.date) }
                    ?.let { Dates.format(it, compact = true) }
                val what = e.event_type_display ?: EventKind.of(e.type).label
                add(UpcomingRow.Event(e, if (until != null) "$what · until $until" else what))
            }
        }
    }

    fun dayLabel(day: LocalDate?): String = when (day) {
        null -> "Undated"
        Dates.today() -> "Today"
        Dates.today().plusDays(1) -> "Tomorrow"
        else -> dayFormat.format(day)
    }

    /** What the widget says when its list is empty. */
    fun emptyText(loaded: Boolean, config: UpcomingConfig, followed: Set<String>): String = when {
        !loaded -> "The dates will appear once the phone has been online."
        config.followedOnly && followed.isEmpty() -> "You aren't following any mergers yet."
        config.followedOnly -> "Nothing coming up for the mergers you follow."
        else -> "Nothing coming up."
    }
}
