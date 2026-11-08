package fyi.mergers.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.mergers.app.Graph
import fyi.mergers.app.data.IndustryDivisionFile
import fyi.mergers.app.data.IndustryEntry
import fyi.mergers.app.data.IndustryIndex
import fyi.mergers.app.data.Loaded
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.PhaseFilter
import fyi.mergers.app.data.SortOrder
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.node
import fyi.mergers.app.data.sortedBy
import fyi.mergers.app.notify.Notifier
import fyi.mergers.app.tracking.SyncResult
import fyi.mergers.app.tracking.TrackedEvents
import fyi.mergers.app.tracking.TrackingState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app can be. The tabs are the roots; a merger or an industry stacks on top. */
sealed interface Route {
    data object Dashboard : Route
    data object Mergers : Route
    data object Following : Route
    data class Merger(val id: String) : Route
    data object Industries : Route
    data class Industry(val code: String) : Route

    /** Only on screen, never in [AppViewModel.backStack]: the root that holds all three tabs. */
    data object Tabs : Route
}

/** The merger list's search and filters, kept while you go in and out of mergers. */
data class ListFilter(
    val query: String = "",
    val phase: PhaseFilter? = null,
    val status: String? = null,
    val followedOnly: Boolean = false,
    val sort: SortOrder = SortOrder.UPDATED,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo get() = Graph.repo
    private val store get() = Graph.store
    private val tracker get() = Graph.tracker

    val backStack = mutableStateListOf<Route>(Route.Dashboard)

    val tracking: StateFlow<TrackingState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, TrackingState())
    val stats get() = repo.stats
    val upcoming get() = repo.upcoming
    val list get() = repo.list
    val industries: StateFlow<Loaded<IndustryIndex>> get() = repo.industries
    val sync: StateFlow<SyncResult?> get() = tracker.last

    var filter by mutableStateOf(ListFilter())

    var syncing by mutableStateOf(false)
        private set

    init {
        refreshDashboard()
        refreshList()
        refreshFollowing()
    }

    // ---- navigation ---------------------------------------------------------

    val tab: Route get() = backStack.first()

    fun selectTab(tab: Route) {
        if (backStack.size == 1 && backStack.first() == tab) return
        backStack.clear()
        backStack.add(tab)
        if (tab == Route.Following) refreshFollowing()
    }

    fun openMerger(id: String) {
        if (backStack.lastOrNull() == Route.Merger(id)) return
        backStack.add(Route.Merger(id))
    }

    fun openIndustries() = goTo(Route.Industries)

    fun openIndustry(code: String) = goTo(Route.Industry(code))

    /**
     * Back to [route] if it's already open further down (a breadcrumb up to an
     * industry you came through), else on top. Walking the tree up and down
     * then doesn't pile up a stack that takes as many presses of back.
     */
    private fun goTo(route: Route) {
        val at = backStack.indexOf(route)
        if (at > 0) {
            while (backStack.size > at + 1) backStack.removeAt(backStack.lastIndex)
        } else if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    /** Back: down the stack, then from another tab to the dashboard. False when there's nowhere left. */
    fun back(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        if (backStack.first() != Route.Dashboard) {
            selectTab(Route.Dashboard)
            return true
        }
        return false
    }

    fun showMergersWithStatus(status: String) {
        filter = ListFilter(status = status)
        selectTab(Route.Mergers)
    }

    // ---- data -----------------------------------------------------------------

    fun refreshDashboard() {
        viewModelScope.launch { repo.refreshDashboard() }
    }

    fun refreshList() {
        viewModelScope.launch { repo.refreshList() }
    }

    fun detail(id: String): StateFlow<Loaded<MergerDetail>> = repo.detail(id)

    fun loadDetail(id: String) {
        viewModelScope.launch {
            repo.refreshDetail(id)
            // Reading a followed merger reads its news.
            val m = repo.detail(id).value.value ?: return@launch
            if (tracking.value.isTracked(id)) {
                store.markSeen(TrackedEvents.of(m).map { it.key })
                Notifier.cancel(getApplication(), id)
            }
            // So does reading a merger a followed industry flagged.
            store.markSeen(sync.value?.events.orEmpty().filter { it.industryCode != null && it.mergerId == id }.map { it.key })
        }
    }

    // ---- industries -----------------------------------------------------------

    fun loadIndustries() {
        viewModelScope.launch { repo.refreshIndustries() }
    }

    /** The division file [code]'s node is in. */
    fun division(code: String): StateFlow<Loaded<IndustryDivisionFile>> = repo.division(code)

    fun loadIndustry(code: String) {
        viewModelScope.launch {
            repo.refreshDivision(code)
            // Reading a followed industry reads its news.
            val node = repo.division(code).value.value?.node(code) ?: return@launch
            if (tracking.value.isIndustryTracked(code)) {
                store.markSeen(TrackedEvents.industry(node).map { it.key })
                Notifier.cancelIndustry(getApplication(), code)
            }
        }
    }

    /** Follow or unfollow an industry. Returns true if it is now followed. */
    fun toggleIndustryFollow(code: String, name: String): Boolean {
        val now = !tracking.value.isIndustryTracked(code)
        viewModelScope.launch { if (now) tracker.trackIndustry(code, name) else tracker.untrackIndustry(code) }
        return now
    }

    /** The industry list's search: name or code, every word. */
    fun filterIndustries(all: List<IndustryEntry>, query: String): List<IndustryEntry> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter { i ->
            val text = "${i.name} ${i.code}".lowercase()
            tokens.all { it in text }
        }.sortedByDescending { it.merger_count }
    }

    fun refreshFollowing() {
        viewModelScope.launch {
            syncing = true
            try {
                tracker.sync()
            } finally {
                syncing = false
            }
        }
    }

    /** Follow or unfollow. Returns true if this is now followed. */
    fun toggleFollow(id: String): Boolean {
        val now = !tracking.value.isTracked(id)
        viewModelScope.launch { if (now) tracker.track(id) else tracker.untrack(id) }
        return now
    }

    fun markAllRead() {
        val keys = sync.value?.events?.map { it.key }.orEmpty()
        viewModelScope.launch { store.markSeen(keys) }
    }

    /** The Following screen has shown these, so the background check needn't. */
    fun markShown(keys: List<String>) {
        viewModelScope.launch { store.markNotified(keys) }
    }

    fun markSeen(keys: List<String>) {
        viewModelScope.launch { store.markSeen(keys) }
    }

    fun setAutoTrackPhase2(on: Boolean) {
        viewModelScope.launch {
            store.setAutoTrackPhase2(on)
            if (on) refreshFollowing()
        }
    }

    fun setNotifyNewFilings(on: Boolean) {
        viewModelScope.launch {
            // Start from a clean slate, so the first check records the register
            // as it stands rather than announcing all of it.
            if (on) store.setKnownIds(emptySet())
            store.setNotifyNewFilings(on)
            if (on) refreshFollowing()
        }
    }

    // ---- the merger list ------------------------------------------------------

    /** Every status the list's badges show, in the site's order of interest. */
    val statusOptions = listOf(
        Status.UNDER_ASSESSMENT,
        Status.APPROVED,
        Status.REFERRED_TO_PHASE_2,
        Status.NOT_APPROVED,
        Status.ASSESSMENT_SUSPENDED,
        Status.ASSESSMENT_CEASED,
    )

    fun applyFilter(all: List<MergerSummary>, f: ListFilter, tracked: Set<String>): List<MergerSummary> {
        val tokens = f.query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.asSequence()
            .filter { f.phase == null || f.phase.test(it) }
            .filter { f.status == null || Status.displayed(it.accc_determination, it.status) == f.status }
            .filter { !f.followedOnly || it.merger_id in tracked }
            .filter { m -> tokens.isEmpty() || searchText(m).let { text -> tokens.all { it in text } } }
            .toList()
            .sortedBy(f.sort)
    }

    private val searchCache = HashMap<String, String>()

    private fun searchText(m: MergerSummary): String = searchCache.getOrPut(m.merger_id + m.page_modified_datetime) {
        buildString {
            append(m.merger_name).append(' ').append(m.merger_id)
            (m.acquirers + m.targets + m.other_parties).forEach { append(' ').append(it.name) }
            m.anzsic_codes.forEach { append(' ').append(it.name) }
        }.lowercase()
    }
}
