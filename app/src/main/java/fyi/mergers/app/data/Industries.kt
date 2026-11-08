package fyi.mergers.app.data

import kotlinx.serialization.Serializable

/** The site's own page for an industry, for sharing and opening in a browser. */
fun industryPageUrl(code: String) = "$SITE/industries/$code"

// ---- industries.json --------------------------------------------------------

/** Every ANZSIC code a merger has been tagged with, and how many it has. */
@Serializable
data class IndustryIndex(
    val industries: List<IndustryEntry> = emptyList(),
    /** Distinct mergers: a merger tagged with two codes counts once here, twice in the entries. */
    val total_mergers: Int = 0,
)

@Serializable
data class IndustryEntry(val code: String = "", val name: String = "", val merger_count: Int = 0)

// ---- industries/{division}.json ---------------------------------------------

/**
 * One ANZSIC division's file: every node under it, with each merger summary
 * stored once and referenced by id. See the site's utils/industryNode.js.
 */
@Serializable
data class IndustryDivisionFile(
    val division: String? = null,
    val nodes: Map<String, IndustryNodeEntry> = emptyMap(),
    val mergers: Map<String, IndustryMerger> = emptyMap(),
)

@Serializable
data class IndustryNodeEntry(
    val name: String? = null,
    val level: String? = null,
    val parent: String? = null,
    val children: List<String> = emptyList(),
    /** In display order: the summaries carry no sort keys. */
    val mergers: List<String> = emptyList(),
    val count: Int = 0,
    val phase_1_count: Int = 0,
    val phase_2_count: Int = 0,
    val waiver_count: Int = 0,
    val active_count: Int = 0,
)

/** The short summary a division file keeps of each merger. */
@Serializable
data class IndustryMerger(
    val merger_id: String,
    val merger_name: String = "",
    val is_waiver: Boolean = false,
    val status: String? = null,
    val determination: String? = null,
    val has_conditions: Boolean = false,
    val under_appeal: Boolean = false,
    /** "Phase 1", "Phase 2" or "Waiver". */
    val phase: String? = null,
    val notification_date: String? = null,
    val determination_date: String? = null,
) {
    /** As a list entry, for a card when list.json hasn't got (or hasn't loaded) the fuller one. */
    fun toSummary() = MergerSummary(
        merger_id = merger_id,
        merger_name = merger_name,
        status = status,
        accc_determination = determination,
        has_conditions = has_conditions,
        is_waiver = is_waiver,
        under_appeal = under_appeal,
        reached_phase_2 = phase == IndustryPhase.PHASE_2.key,
        effective_notification_datetime = notification_date,
        determination_publication_date = determination_date,
    )
}

/** The industry pages' groups, most scrutiny first (the site's utils/industryGroups.js). */
enum class IndustryPhase(val key: String, val label: String) {
    PHASE_2("Phase 2", "Phase 2 – detailed assessment"),
    PHASE_1("Phase 1", "Phase 1 – initial assessment"),
    WAIVER("Waiver", "Waivers"),
    ;

    companion object {
        fun of(m: IndustryMerger): IndustryPhase =
            entries.firstOrNull { it.key == m.phase } ?: if (m.is_waiver) WAIVER else PHASE_1
    }
}

/** A node as a link: to an ancestor (breadcrumb) or a child (with its merger count). */
data class IndustryRef(val code: String, val name: String, val level: String?, val count: Int = 0)

/** One ANZSIC node, unpacked from its division file into what the page shows. */
data class IndustryNode(
    val code: String,
    val name: String,
    val level: String?,
    /** Division first, down to the parent. */
    val ancestors: List<IndustryRef>,
    val children: List<IndustryRef>,
    val mergers: List<IndustryMerger>,
    val count: Int,
    val phase2Count: Int,
    val waiverCount: Int,
    val activeCount: Int,
) {
    /** "Class", "Group", … as the site labels each level. */
    val levelLabel: String? get() = LEVEL_LABELS[level]

    companion object {
        val LEVEL_LABELS = mapOf(
            "division" to "Division",
            "subdivision" to "Subdivision",
            "group" to "Group",
            "class" to "Class",
        )
    }
}

/**
 * Which file under industries/ holds a code's node. A port of the site's
 * utils/industryDivision.js (and scripts/industry_division.py, which writes
 * the files): if this disagrees, the app asks for the wrong file and the page
 * comes up empty. The test suite checks it against the site's golden fixture.
 */
object IndustryDivisions {
    /** Division letter, first and last subdivision number, inclusive. ANZSIC 2006 is frozen. */
    private val RANGES = listOf(
        Triple('A', 1, 5), Triple('B', 6, 10), Triple('C', 11, 25), Triple('D', 26, 29),
        Triple('E', 30, 32), Triple('F', 33, 38), Triple('G', 39, 43), Triple('H', 44, 45),
        Triple('I', 46, 53), Triple('J', 54, 60), Triple('K', 62, 64), Triple('L', 66, 67),
        Triple('M', 69, 70), Triple('N', 72, 73), Triple('O', 75, 77), Triple('P', 80, 82),
        Triple('Q', 84, 87), Triple('R', 89, 92), Triple('S', 94, 96),
    )

    /** Where a code no division can be derived for lives. Always published, even empty. */
    const val ORPHAN_STEM = "_orphans"

    /** The division letter a code sits under, or null if it isn't in the tree. */
    fun divisionFor(code: String): String? {
        val c = code.trim()
        if (c.length == 1) return c.takeIf { RANGES.any { r -> r.first == c[0] } }
        if (c.length !in 2..4 || !c.all { it in '0'..'9' }) return null
        val sub = c.take(2).toInt()
        return RANGES.firstOrNull { sub in it.second..it.third }?.first?.toString()
    }

    fun stem(code: String): String = divisionFor(code) ?: ORPHAN_STEM

    /** Path under /data/, e.g. "industries/G.json". */
    fun path(code: String): String = "industries/${stem(code)}.json"
}

/**
 * Unpack [code] from its division file, or null if the file has no such node
 * (a code that isn't an industry). Rebuilds what the file leaves out, as the
 * site's readIndustryNode does: the breadcrumb, walked up the parents, and each
 * child's count, read off the child's own entry.
 */
fun IndustryDivisionFile.node(code: String): IndustryNode? {
    val n = nodes[code] ?: return null
    val ancestors = ArrayList<IndustryRef>()
    val seen = hashSetOf(code)
    var parent = n.parent
    // The seen-set guards against a malformed parent cycle.
    while (parent != null && seen.add(parent)) {
        val p = nodes[parent] ?: break
        ancestors.add(0, IndustryRef(parent, p.name ?: parent, p.level))
        parent = p.parent
    }
    return IndustryNode(
        code = code,
        name = n.name ?: code,
        level = n.level,
        ancestors = ancestors,
        children = n.children.mapNotNull { c -> nodes[c]?.let { IndustryRef(c, it.name ?: c, it.level, it.count) } },
        // Ids without a summary are dropped rather than shown blank.
        mergers = n.mergers.mapNotNull { mergers[it] },
        count = n.count,
        phase2Count = n.phase_2_count,
        waiverCount = n.waiver_count,
        activeCount = n.active_count,
    )
}
