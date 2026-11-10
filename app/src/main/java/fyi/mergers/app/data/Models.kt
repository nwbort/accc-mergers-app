package fyi.mergers.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The JSON mergers.fyi publishes under /data/, field for field as the
 * generator (accc-mergers: scripts/generate/static_data/) writes it. Only what
 * the app shows is declared; everything else is ignored, so a new field on the
 * site never breaks the app.
 */
val MergersJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class Party(
    val name: String = "",
    val identifier_type: String? = null,
    val identifier: String? = null,
    val party_page: PartyPage? = null,
) {
    /** The site's tidied name where it has one: the register shouts in capitals. */
    val displayName: String get() = party_page?.name ?: name
}

@Serializable
data class PartyPage(val id: String = "", val name: String = "")

@Serializable
data class AnzsicCode(val code: String = "", val name: String = "")

@Serializable
data class AppealSummary(
    val tribunal_number: String? = null,
    val tribunal_url: String? = null,
    val appeal_type: String? = null,
    val appellant: String? = null,
    val status: String? = null,
    val outcome: String? = null,
    val effective_determination: String? = null,
    val filed_date: String? = null,
    val hearing_date: String? = null,
    val hearing_end_date: String? = null,
    val concluded_date: String? = null,
)

// ---- mergers/list.json ------------------------------------------------------

@Serializable
data class MergerList(val mergers: List<MergerSummary> = emptyList())

@Serializable
data class MergerSummary(
    val merger_id: String,
    val merger_name: String = "",
    val status: String? = null,
    val accc_determination: String? = null,
    val has_conditions: Boolean = false,
    val is_waiver: Boolean = false,
    val under_appeal: Boolean = false,
    val is_refiled: Boolean = false,
    val reached_phase_2: Boolean = false,
    val effective_notification_datetime: String? = null,
    val determination_publication_date: String? = null,
    val end_of_determination_period: String? = null,
    val latest_event_date: String? = null,
    val page_modified_datetime: String? = null,
    val stage: String? = null,
    val acquirers: List<Party> = emptyList(),
    val targets: List<Party> = emptyList(),
    val other_parties: List<Party> = emptyList(),
    val anzsic_codes: List<AnzsicCode> = emptyList(),
    val appeal: AppealSummary? = null,
)

// ---- mergers/{id}.json ------------------------------------------------------

@Serializable
data class MergerDetail(
    val merger_id: String,
    val merger_name: String = "",
    val url: String? = null,
    val status: String? = null,
    val stage: String? = null,
    val accc_determination: String? = null,
    val has_conditions: Boolean = false,
    val is_waiver: Boolean = false,
    val effective_notification_datetime: String? = null,
    val original_notification_datetime: String? = null,
    val end_of_determination_period: String? = null,
    val determination_publication_date: String? = null,
    val consultation_response_due_date: String? = null,
    val competition_concerns_notice_date: String? = null,
    val ceased_date: String? = null,
    val assessment_recommenced_date: String? = null,
    val phase_1_determination: String? = null,
    val phase_1_determination_date: String? = null,
    val phase_2_determination: String? = null,
    val phase_2_determination_date: String? = null,
    val public_benefit_application_date: String? = null,
    val public_benefit_in_progress: Boolean = false,
    val public_benefit_assessment_date: String? = null,
    val public_benefit_response_date: String? = null,
    val public_benefits_determination: String? = null,
    val public_benefits_determination_date: String? = null,
    val acquirers: List<Party> = emptyList(),
    val targets: List<Party> = emptyList(),
    val other_parties: List<Party> = emptyList(),
    val anzsic_codes: List<AnzsicCode> = emptyList(),
    val merger_description: String? = null,
    val events: List<MergerEvent> = emptyList(),
    val similar_mergers: List<SimilarMerger> = emptyList(),
    val related_merger: RelatedMerger? = null,
    val comments: List<Comment> = emptyList(),
    val phase_1_estimate: Phase1Estimate? = null,
    val under_appeal: Boolean = false,
    val appeal: AppealSummary? = null,
    val appeals: List<AppealSummary> = emptyList(),
    val under_judicial_review: Boolean = false,
    val judicial_reviews: List<JudicialReview> = emptyList(),
)

@Serializable
data class MergerEvent(
    val date: String? = null,
    val title: String? = null,
    val display_title: String? = null,
    val url: String? = null,
    val url_gh: String? = null,
    val phase: String? = null,
    val status: String? = null,
    val is_determination_event: Boolean = false,
    val is_questionnaire_event: Boolean = false,
    val is_appeal: Boolean = false,
    val appeal_base_title: String? = null,
    val determination_table_content: List<TableItem> = emptyList(),
    val determination_statement_of_reasons: List<StatementBlock> = emptyList(),
)

@Serializable
data class TableItem(val item: String? = null, val details: String? = null)

/**
 * One block of a detailed determination's "Statement of reasons", as the
 * site's parser (scripts/parse/parse_determination.py) classifies it:
 * `heading`, `paragraph` (with an optional `number`), `bullet_list` (`items`
 * are strings, `sub_items` aligned with them) or `lettered_list` (`items` are
 * `{letter, text}` objects).
 */
@Serializable
data class StatementBlock(
    val type: String? = null,
    val text: String? = null,
    val number: String? = null,
    val items: List<JsonElement> = emptyList(),
    val sub_items: List<List<String>> = emptyList(),
)

@Serializable
data class SimilarMerger(
    val merger_id: String,
    val merger_name: String = "",
    val accc_determination: String? = null,
    val status: String? = null,
)

@Serializable
data class RelatedMerger(
    val merger_id: String? = null,
    val relationship: String? = null,
    val merger_name: String? = null,
)

@Serializable
data class Comment(val commentary: String = "", val date: String? = null, val author: String? = null)

@Serializable
data class Phase1Estimate(
    val expected_business_days: Int? = null,
    val range_business_days: List<Int> = emptyList(),
    val sample_size: Int? = null,
)

@Serializable
data class JudicialReview(
    val case_number: String? = null,
    val case_url: String? = null,
    val applicant: String? = null,
    val filed_date: String? = null,
    val status: String? = null,
    val outcome: String? = null,
    val concluded_date: String? = null,
    val court: String? = null,
    val listings: List<CourtListing> = emptyList(),
)

@Serializable
data class CourtListing(
    val date: String? = null,
    val time: String? = null,
    val reason: String? = null,
    val location: String? = null,
    val outcome: String? = null,
    val hearing: Boolean = false,
)

// ---- stats.json -------------------------------------------------------------

@Serializable
data class Stats(
    val total_mergers: Int = 0,
    val total_waivers: Int = 0,
    val by_status: Map<String, Int> = emptyMap(),
    val by_determination: Map<String, Int> = emptyMap(),
    val by_phase_2_determination: Map<String, Int> = emptyMap(),
    val by_waiver_determination: Map<String, Int> = emptyMap(),
    val phase_duration: Durations = Durations(),
    val waiver_duration: Durations = Durations(),
    val recent_mergers: List<RecentMerger> = emptyList(),
    val recent_determinations: List<RecentDetermination> = emptyList(),
)

@Serializable
data class Durations(
    val average_days: Double? = null,
    val median_days: Double? = null,
    val average_business_days: Double? = null,
    val median_business_days: Double? = null,
    val percentiles: Map<String, Percentile> = emptyMap(),
)

@Serializable
data class Percentile(val count: Int = 0, val percentage: Double = 0.0)

@Serializable
data class RecentMerger(
    val merger_id: String,
    val merger_name: String = "",
    val status: String? = null,
    val accc_determination: String? = null,
    val effective_notification_datetime: String? = null,
    val is_waiver: Boolean = false,
)

@Serializable
data class RecentDetermination(
    val merger_id: String,
    val merger_name: String = "",
    val determination: String? = null,
    val determination_date: String? = null,
    val is_waiver: Boolean = false,
    val stage: String? = null,
    val has_conditions: Boolean = false,
)

// ---- analysis.json (only current_status, the /current-status page) --------

@Serializable
data class Analysis(val current_status: CurrentStatus? = null)

@Serializable
data class CurrentStatus(
    val as_at: String? = null,
    val windows: List<StatusWindow> = emptyList(),
)

/** Decision times for the matters decided in the last [days] days. */
@Serializable
data class StatusWindow(
    val days: Int = 0,
    val notifications: WindowDurations = WindowDurations(),
    val waivers: WindowDurations = WindowDurations(),
)

/** Business days; [median_delta] is against the all-time median (positive is slower). */
@Serializable
data class WindowDurations(
    val median: Double? = null,
    val p90: Int? = null,
    val count: Int = 0,
    val median_delta: Double? = null,
)

// ---- upcoming-events.json ---------------------------------------------------

@Serializable
data class UpcomingEvents(val events: List<UpcomingEvent> = emptyList())

@Serializable
data class UpcomingEvent(
    val type: String? = null,
    val event_type_display: String? = null,
    val date: String? = null,
    val end_date: String? = null,
    val merger_id: String = "",
    val merger_name: String = "",
    val stage: String? = null,
    val is_waiver: Boolean = false,
)

// ---- recent-events.json --------------------------------------------------

@Serializable
data class RecentEvents(val events: List<RecentEvent> = emptyList())

/** One register event from the last two weeks; [type] is the site's classify() (recent_events.py). */
@Serializable
data class RecentEvent(
    val type: String? = null,
    val title: String? = null,
    val date: String? = null,
    val merger_id: String = "",
    val merger_name: String = "",
    val stage: String? = null,
    val is_waiver: Boolean = false,
)

// ---- phase2.json (only which matters are current) ---------------------------

@Serializable
data class Phase2Feed(val current: List<Phase2Matter> = emptyList())

@Serializable
data class Phase2Matter(val merger_id: String? = null)
