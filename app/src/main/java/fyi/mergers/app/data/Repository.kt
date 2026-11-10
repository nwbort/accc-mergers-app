package fyi.mergers.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.DeserializationStrategy
import java.util.concurrent.ConcurrentHashMap

/** A file on its way in: the last copy (if any) while the fresh one loads. */
data class Loaded<out T>(val value: T? = null, val loading: Boolean = true, val error: String? = null)

/**
 * The site's data, as the screens and the background check read it. Every
 * screen opens on the copy kept from last time and then refreshes, so the app
 * is instant and still works offline.
 */
class Repository(private val source: DataSource) {

    private companion object {
        const val UPCOMING = "upcoming-events.json"
    }

    private val _stats = MutableStateFlow(Loaded<Stats>())
    val stats: StateFlow<Loaded<Stats>> = _stats.asStateFlow()

    private val _upcoming = MutableStateFlow(Loaded<List<UpcomingEvent>>())
    val upcoming: StateFlow<Loaded<List<UpcomingEvent>>> = _upcoming.asStateFlow()

    private val _recent = MutableStateFlow(Loaded<List<RecentEvent>>())

    /** recent-events.json: what happened on the register over the last two weeks. */
    val recent: StateFlow<Loaded<List<RecentEvent>>> = _recent.asStateFlow()

    private val _currentStatus = MutableStateFlow(Loaded<StatusWindow>())

    /** The site's /current-status headline: the last 30 days' decision times. */
    val currentStatus: StateFlow<Loaded<StatusWindow>> = _currentStatus.asStateFlow()

    private val _list = MutableStateFlow(Loaded<List<MergerSummary>>())
    val list: StateFlow<Loaded<List<MergerSummary>>> = _list.asStateFlow()

    private val details = ConcurrentHashMap<String, MutableStateFlow<Loaded<MergerDetail>>>()

    private val _industries = MutableStateFlow(Loaded<IndustryIndex>())
    val industries: StateFlow<Loaded<IndustryIndex>> = _industries.asStateFlow()

    /** One per division file, shared by every node in it: a page and its breadcrumb cost one fetch. */
    private val divisions = ConcurrentHashMap<String, MutableStateFlow<Loaded<IndustryDivisionFile>>>()

    /** The file holding [code]'s node. */
    fun division(code: String): StateFlow<Loaded<IndustryDivisionFile>> = divisionFlow(code).asStateFlow()

    private fun divisionFlow(code: String) =
        divisions.getOrPut(IndustryDivisions.stem(code)) { MutableStateFlow(Loaded()) }

    fun detail(id: String): StateFlow<Loaded<MergerDetail>> = detailFlow(id).asStateFlow()

    private fun detailFlow(id: String) = details.getOrPut(id) { MutableStateFlow(Loaded()) }

    private fun <T> parse(text: String, strategy: DeserializationStrategy<T>): T =
        MergersJson.decodeFromString(strategy, text)

    /** Cached copy first (if there is one), then the network. */
    private suspend fun <S, T> load(
        flow: MutableStateFlow<Loaded<T>>,
        path: String,
        strategy: DeserializationStrategy<S>,
        map: (S) -> T,
    ) {
        if (flow.value.value == null) {
            source.cached(path)?.let { text ->
                runCatching { map(parse(text, strategy)) }.getOrNull()?.let { v -> flow.update { it.copy(value = v) } }
            }
        }
        flow.update { it.copy(loading = true, error = null) }
        try {
            val v = map(parse(source.fetch(path), strategy))
            flow.value = Loaded(v, loading = false)
        } catch (e: Exception) {
            flow.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    suspend fun refreshDashboard() = coroutineScope {
        val a = async { load(_stats, "stats.json", Stats.serializer()) { it } }
        val b = async { load(_upcoming, UPCOMING, UpcomingEvents.serializer()) { it.events } }
        val c = async {
            load(_currentStatus, "analysis.json", Analysis.serializer()) { analysis ->
                val windows = analysis.current_status?.windows.orEmpty()
                windows.firstOrNull { it.days == 30 } ?: windows.firstOrNull() ?: error("No current_status in analysis.json")
            }
        }
        val d = async { load(_recent, "recent-events.json", RecentEvents.serializer()) { it.events } }
        a.await(); b.await(); c.await(); d.await()
    }

    /** upcoming-events.json from the network (or the last copy), for the Coming up widget. */
    suspend fun fetchUpcoming(): List<UpcomingEvent>? = runCatching {
        parse(source.fetch(UPCOMING), UpcomingEvents.serializer()).events
    }.getOrNull()?.also { v -> _upcoming.value = Loaded(v, loading = false) }

    /** The upcoming events as last loaded or kept on the phone, without touching the network. */
    suspend fun keptUpcoming(): List<UpcomingEvent>? = _upcoming.value.value
        ?: source.cached(UPCOMING)?.let { text ->
            runCatching { parse(text, UpcomingEvents.serializer()).events }.getOrNull()
        }

    suspend fun refreshList() = load(_list, "mergers/list.json", MergerList.serializer()) { it.mergers }

    suspend fun refreshDetail(id: String) =
        load(detailFlow(id), "mergers/$id.json", MergerDetail.serializer()) { it }

    /** A merger's detail file straight from the network (or the last copy), for the background check. */
    suspend fun fetchDetail(id: String): MergerDetail? = runCatching {
        parse(source.fetch("mergers/$id.json"), MergerDetail.serializer())
    }.getOrNull()?.also { v -> detailFlow(id).value = Loaded(v, loading = false) }

    /**
     * The site's manifest, or null when there isn't a usable one: not deployed
     * yet, a format this app doesn't know, or the SPA's index.html served in
     * place of a missing file. Null means fetch everything, as before.
     */
    suspend fun fetchManifest(): Manifest? = runCatching {
        parse(source.fetch(Manifest.PATH), Manifest.serializer())
    }.getOrNull()?.takeIf { it.format == Manifest.FORMAT && it.mergers.isNotEmpty() }

    /**
     * A followed merger for the background check: the copy kept on the phone
     * when [manifest] says it is still current, otherwise [fetchDetail].
     */
    suspend fun detailFor(id: String, manifest: Manifest?): MergerDetail? {
        val want = manifest?.mergers?.get(id)
        if (want != null) {
            val kept = source.cached("mergers/$id.json")
            if (kept != null && ManifestHash.of(kept) == want) {
                runCatching { parse(kept, MergerDetail.serializer()) }.getOrNull()?.let { v ->
                    if (detailFlow(id).value.value == null) detailFlow(id).value = Loaded(v, loading = false)
                    return v
                }
            }
        }
        return fetchDetail(id)
    }

    /**
     * The division file holding [code], for the background check: the kept
     * copy when [manifest] says it is still current, otherwise [fetchDivision].
     */
    suspend fun divisionFor(code: String, manifest: Manifest?): IndustryDivisionFile? {
        val want = manifest?.industries?.get(IndustryDivisions.stem(code))
        if (want != null) {
            val kept = source.cached(IndustryDivisions.path(code))
            if (kept != null && ManifestHash.of(kept) == want) {
                runCatching { parse(kept, IndustryDivisionFile.serializer()) }.getOrNull()?.let { v ->
                    if (divisionFlow(code).value.value == null) divisionFlow(code).value = Loaded(v, loading = false)
                    return v
                }
            }
        }
        return fetchDivision(code)
    }

    suspend fun fetchList(): List<MergerSummary>? = runCatching {
        parse(source.fetch("mergers/list.json"), MergerList.serializer()).mergers
    }.getOrNull()?.also { v -> _list.value = Loaded(v, loading = false) }

    suspend fun refreshIndustries() = load(_industries, "industries.json", IndustryIndex.serializer()) { it }

    suspend fun refreshDivision(code: String) =
        load(divisionFlow(code), IndustryDivisions.path(code), IndustryDivisionFile.serializer()) { it }

    /** [code]'s division file straight from the network (or the last copy), for the background check. */
    suspend fun fetchDivision(code: String): IndustryDivisionFile? = runCatching {
        parse(source.fetch(IndustryDivisions.path(code)), IndustryDivisionFile.serializer())
    }.getOrNull()?.also { v -> divisionFlow(code).value = Loaded(v, loading = false) }

    suspend fun fetchPhase2Current(): List<String>? = runCatching {
        parse(source.fetch("phase2.json"), Phase2Feed.serializer()).current.mapNotNull { it.merger_id }
    }.getOrNull()
}
