package fyi.mergers.app.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates as mergers.fyi publishes them. The pipeline stores a register date as
 * midday UTC ("2025-09-05T12:00:00Z") so it is the same calendar day in every
 * Australian zone; Tribunal dates are plain "2026-11-09". Either way the first
 * ten characters are the day, which is all the register ever means.
 */
object Dates {
    val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")

    /** Overridable so tests can pin "today". */
    var clock: Clock = Clock.system(SYDNEY)

    fun today(): LocalDate = LocalDate.now(clock.withZone(SYDNEY))

    fun day(value: String?): LocalDate? {
        if (value == null || value.length < 10) return null
        return runCatching { LocalDate.parse(value.substring(0, 10)) }.getOrNull()
    }

    private val long = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-AU"))
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("en-AU"))

    /** "5 Sep 2025", or "5 Sep" within the current year when [compact]. */
    fun format(value: String?, compact: Boolean = false): String? {
        val d = day(value) ?: return null
        return if (compact && d.year == today().year) short.format(d) else long.format(d)
    }

    fun daysUntil(value: String?): Long? = day(value)?.let { ChronoUnit.DAYS.between(today(), it) }

    /** Whether something spread over days (a hearing) began before today and hasn't ended. */
    fun underWay(start: String?, end: String?): Boolean =
        (daysUntil(start) ?: 0) < 0 && (daysUntil(end) ?: -1) >= 0

    /**
     * When a coming date is, for a list: "in 3 days", or for one already
     * under way "under way, until 19 Nov".
     */
    fun upcoming(start: String?, end: String?): String? =
        if (underWay(start, end)) "under way, until ${format(end, compact = true)}" else relative(start)

    /** "9 Nov", or a span over days "9 – 19 Nov 2026" (compact within this year). */
    fun span(start: String?, end: String?, compact: Boolean = false): String? {
        val a = day(start) ?: return null
        val b = day(end)?.takeIf { it > a } ?: return format(start, compact)
        val to = format(end, compact)
        return if (a.year == b.year && a.month == b.month) "${a.dayOfMonth} – $to" else "${format(start, compact = true)} – $to"
    }

    /** "today", "tomorrow", "in 5 days", "yesterday", "3 days ago". */
    fun relative(value: String?): String? {
        val n = daysUntil(value) ?: return null
        return when {
            n == 0L -> "today"
            n == 1L -> "tomorrow"
            n == -1L -> "yesterday"
            n > 1 -> "in $n days"
            else -> "${-n} days ago"
        }
    }
}
