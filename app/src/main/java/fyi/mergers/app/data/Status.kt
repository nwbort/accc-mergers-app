package fyi.mergers.app.data

/**
 * The register's status, determination and phase labels, and how a matter's
 * outcome is read out of them. A port of the site's
 * frontend/src/constants/mergerStatus.js, constants/appeal.js and
 * utils/mergerOutcome.js, so the app and the site never disagree about what a
 * matter's outcome is.
 */
object Status {
    const val UNDER_ASSESSMENT = "Under assessment"
    const val ASSESSMENT_SUSPENDED = "Assessment suspended"
    const val ASSESSMENT_COMPLETED = "Assessment completed"
    const val ASSESSMENT_CEASED = "Assessment ceased"
    const val APPROVED = "Approved"
    const val NOT_APPROVED = "Not approved"
    const val DECLINED = "Declined"
    const val NOT_OPPOSED = "Not opposed"
    const val REFERRED_TO_PHASE_2 = "Referred to phase 2"
    const val APPROVED_WITH_CONDITIONS = "Approved with conditions"

    private val DECIDED = setOf(APPROVED, NOT_APPROVED, NOT_OPPOSED, DECLINED)

    private val APPEAL_SUFFIX = mapOf(
        "affirmed" to "confirmed on appeal",
        "dismissed" to "confirmed on appeal",
        "withdrawn" to "appeal withdrawn",
        "set_aside" to "on appeal",
        "varied" to "varied on appeal",
    )

    /** The determination that now stands, after any concluded Tribunal appeal. */
    fun effectiveDetermination(determination: String?, appeal: AppealSummary?): Pair<String?, String?> {
        val concluded = appeal?.takeIf { it.status == "concluded" }
        return (concluded?.effective_determination ?: determination) to concluded?.outcome?.let { APPEAL_SUFFIX[it] }
    }

    /** What the status badge shows on a list entry, and what the status filter matches. */
    fun displayed(determination: String?, status: String?): String = determination ?: status ?: "Unknown"

    fun isPublicBenefitStage(stage: String?) = stage?.lowercase()?.contains("public benefit") == true
}

/** The fields the outcome logic reads, common to a list entry and a detail file. */
data class OutcomeInput(
    val status: String?,
    val determination: String?,
    val hasConditions: Boolean,
    val isWaiver: Boolean,
    val stage: String?,
    val appeal: AppealSummary?,
    val underAppeal: Boolean,
    val underJudicialReview: Boolean = false,
    val publicBenefitsDetermination: String? = null,
)

fun MergerDetail.outcomeInput() = OutcomeInput(
    status, accc_determination, has_conditions, is_waiver, stage, appeal, under_appeal,
    under_judicial_review, public_benefits_determination,
)

fun MergerSummary.outcomeInput() = OutcomeInput(
    status, accc_determination, has_conditions, is_waiver, stage, appeal, under_appeal,
)

/** The header line: what the matter is carrying, and whether it has finished. */
data class HeaderStatus(val label: String, val appealSuffix: String?, val decided: Boolean)

fun OutcomeInput.headerStatus(): HeaderStatus? {
    val ceased = status == Status.ASSESSMENT_CEASED
    val (determination, suffix) = Status.effectiveDetermination(determination, appeal)
    if (status == Status.ASSESSMENT_COMPLETED || ceased) {
        if (determination != null && determination in setOf(
                Status.APPROVED, Status.NOT_APPROVED, Status.NOT_OPPOSED, Status.DECLINED,
            )
        ) return HeaderStatus(determination, suffix, true)
        if (ceased) return HeaderStatus(Status.ASSESSMENT_CEASED, suffix, true)
    }
    val label = determination ?: status ?: return null
    return HeaderStatus(label, null, false)
}

/** "Approved with conditions", "Not approved · confirmed on appeal". */
fun OutcomeInput.outcomeText(): String? {
    val h = headerStatus() ?: return null
    val conditions = hasConditions && determination == Status.APPROVED
    return buildString {
        append(h.label)
        if (conditions) append(" with conditions")
        if (publicBenefitsDetermination != null) append(" · after public benefit review")
        h.appealSuffix?.let { append(" · ").append(it) }
    }
}

/** "Phase 1", "Phase 2", "Public benefits", or null where the line already says it. */
fun OutcomeInput.stageLabel(): String? {
    val s = stage ?: return null
    if (isWaiver || s.startsWith("Waiver")) return null
    if (publicBenefitsDetermination != null) return null
    val short = if (Status.isPublicBenefitStage(s)) "Public benefits" else s.split(" - ").first().trim()
    val label = headerStatus()?.label.orEmpty()
    if (label.lowercase().contains(short.lowercase())) return null
    return short
}

/** Phase and status as one phrase, for notifications and sharing. */
fun OutcomeInput.phaseStatus(): String? {
    val outcome = outcomeText() ?: return null
    val phase = if (isWaiver) "Waiver" else stageLabel()
    val status = if (phase != null) "$phase ${outcome.replaceFirstChar { it.lowercase() }}" else outcome
    return listOfNotNull(
        status,
        "Under appeal".takeIf { underAppeal },
        "Under judicial review".takeIf { underJudicialReview },
    ).joinToString(" · ")
}

/** The merger list's phase filter, as on the site (utils/mergerFilters.js). */
enum class PhaseFilter(val label: String, val test: (MergerSummary) -> Boolean) {
    PHASE_1("Phase 1", { it.stage?.contains("Phase 1") == true }),
    PHASE_2("Phase 2", { it.reached_phase_2 || it.stage?.contains("Phase 2") == true }),
    PUBLIC_BENEFIT("Public benefit", { Status.isPublicBenefitStage(it.stage) }),
    WAIVERS("Waivers", { it.is_waiver }),
}

/** The merger list's sort orders, as on the site (utils/mergerSort.js). */
enum class SortOrder(val label: String) {
    UPDATED("Last updated"),
    NOTIFIED("Notification date"),
    DETERMINED("Determination date"),
    NAME("Merger name"),
}

fun List<MergerSummary>.sortedBy(order: SortOrder): List<MergerSummary> = when (order) {
    // The ACCC stamps every event at a nominal midday, so a day's updates tie
    // on date; the page's own modified time is the real order within a day.
    SortOrder.UPDATED -> sortedWith(
        compareByDescending<MergerSummary> { it.latest_event_date?.take(10) ?: "" }
            .thenByDescending { modifiedInstant(it.page_modified_datetime) },
    )
    SortOrder.NOTIFIED -> sortedByDescending { it.effective_notification_datetime ?: "" }
    SortOrder.DETERMINED -> sortedByDescending { it.determination_publication_date ?: "" }
    SortOrder.NAME -> sortedBy { it.merger_name.lowercase() }
}

private fun modifiedInstant(value: String?): Long =
    value?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
