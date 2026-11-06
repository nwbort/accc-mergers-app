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

    private val _stats = MutableStateFlow(Loaded<Stats>())
    val stats: StateFlow<Loaded<Stats>> = _stats.asStateFlow()

    private val _upcoming = MutableStateFlow(Loaded<List<UpcomingEvent>>())
    val upcoming: StateFlow<Loaded<List<UpcomingEvent>>> = _upcoming.asStateFlow()

    private val _list = MutableStateFlow(Loaded<List<MergerSummary>>())
    val list: StateFlow<Loaded<List<MergerSummary>>> = _list.asStateFlow()

    private val details = ConcurrentHashMap<String, MutableStateFlow<Loaded<MergerDetail>>>()

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
        val b = async { load(_upcoming, "upcoming-events.json", UpcomingEvents.serializer()) { it.events } }
        a.await(); b.await()
    }

    suspend fun refreshList() = load(_list, "mergers/list.json", MergerList.serializer()) { it.mergers }

    suspend fun refreshDetail(id: String) =
        load(detailFlow(id), "mergers/$id.json", MergerDetail.serializer()) { it }

    /** A merger's detail file straight from the network (or the last copy), for the background check. */
    suspend fun fetchDetail(id: String): MergerDetail? = runCatching {
        parse(source.fetch("mergers/$id.json"), MergerDetail.serializer())
    }.getOrNull()?.also { v -> detailFlow(id).value = Loaded(v, loading = false) }

    suspend fun fetchList(): List<MergerSummary>? = runCatching {
        parse(source.fetch("mergers/list.json"), MergerList.serializer()).mergers
    }.getOrNull()?.also { v -> _list.value = Loaded(v, loading = false) }

    suspend fun fetchPhase2Current(): List<String>? = runCatching {
        parse(source.fetch("phase2.json"), Phase2Feed.serializer()).current.mapNotNull { it.merger_id }
    }.getOrNull()
}
