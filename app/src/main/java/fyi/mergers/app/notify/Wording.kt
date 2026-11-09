package fyi.mergers.app.notify

import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.phaseStatus

/**
 * Short labels for a notification's collapsed line, which fits about 40
 * characters. A followed merger's events carry the ACCC register's own
 * document titles ("Phase 1 - initial assessment determination: Approved",
 * "Timeline extended by 15 business days – following request by parties"),
 * so this maps the common shapes to a few words and leaves the rest as they
 * are. The full title still shows in the expanded notification and the
 * Following inbox.
 */
object Wording {
    private fun Regex.at(s: String) = find(s)
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val determination = rx("""determination:\s*(.+)$""")
    private val extended = rx("""^timeline extended by (\d+) business days?""")
    private val court = rx("""^(Tribunal appeal|Federal Court)\s*[–-]\s*(.+)$""")
    private val effective = rx("""effective notification date is""")
    private val incomplete = rx("""not (to )?have an effective notification date""")

    fun short(title: String): String {
        val t = title.trim()
        determination.at(t)?.let { m ->
            val outcome = m.groupValues[1].trim()
            val stage = when {
                t.contains("waiver", true) -> "Waiver"
                t.contains("public benefit", true) -> "Public benefit"
                t.contains("phase 2", true) -> "Phase 2"
                t.contains("phase 1", true) -> "Phase 1"
                else -> "Determination"
            }
            return "$stage: $outcome"
        }
        extended.at(t)?.let { return "Extended by ${it.groupValues[1]} business days" }
        court.at(t)?.let { m ->
            val where = if (m.groupValues[1].startsWith("Tribunal", true)) "Tribunal" else "Federal Court"
            return "$where: ${m.groupValues[2].trim()}"
        }
        return when {
            t.equals("Merger notified to ACCC", true) -> "Merger notified"
            t.contains("extend the phase 1 determination period", true) -> "Timeline extended"
            t.contains("acquisition to be put into effect", true) -> "Time to complete extended"
            t.contains("ceased", true) && t.contains("consideration", true) -> "Assessment ceased"
            incomplete.containsMatchIn(t) -> "Notification incomplete"
            effective.containsMatchIn(t) -> "Notification now effective"
            t.contains("summary of reasons", true) -> "Summary of reasons"
            t.contains("statement of reasons", true) -> "Statement of reasons"
            t.contains("accepted s87b undertaking", true) -> "Undertaking accepted"
            // Questionnaire before remedy: "Remedy Offer Questionnaire" asks about an offer already notified.
            Regex("questionn?aire|quesionnaire", RegexOption.IGNORE_CASE).containsMatchIn(t) -> when {
                t.contains("remedy", true) -> "Remedy questionnaire"
                t.contains("public benefit", true) -> "Public benefit questionnaire"
                else -> "Questionnaire released"
            }
            t.contains("draft s87b undertaking", true) -> "Draft undertaking"
            t.contains("remedy offer", true) -> "Remedy offered"
            t.contains("phase 2 review", true) || t.contains("phase 2 notice", true) -> "Referred to Phase 2"
            t.contains("notice of competition concerns", true) -> "Notice of competition concerns"
            rx("""phase 1 consul?t?ation""").containsMatchIn(t) -> "Phase 1 consultation"
            else -> t
        }
    }

    /**
     * A new filing's line. A waiver application only reaches the register once
     * it has been decided, so it says how: "Waiver approved", "Waiver not
     * approved". A merger is just "New merger notified".
     */
    fun newFiling(m: MergerSummary): String =
        if (m.is_waiver) m.outcomeInput().phaseStatus() ?: "Waiver application"
        else "New merger notified"
}
