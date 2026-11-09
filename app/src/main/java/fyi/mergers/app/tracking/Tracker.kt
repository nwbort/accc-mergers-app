package fyi.mergers.app.tracking

import fyi.mergers.app.data.IndustryDivisions
import fyi.mergers.app.data.Manifest
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.Repository
import fyi.mergers.app.data.node
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a check found. */
data class SyncResult(
    /** Every event on every followed merger and in every followed industry, newest first. */
    val events: List<TrackedEvent>,
    /** Events nobody has been told about yet: these become notifications. */
    val fresh: List<TrackedEvent>,
    /** Mergers filed since the last check (only with new-filing alerts on). */
    val newFilings: List<MergerSummary>,
    /** Followed mergers whose file couldn't be read this time. */
    val failed: List<String>,
)

/**
 * Brings the follow list up to date: the one routine behind both the
 * Following screen and the background check, so they can't disagree.
 * Mirrors the effects in the site's TrackingContext.jsx.
 */
class Tracker(private val repo: Repository, private val store: TrackingStore) {

    private val mutex = Mutex()

    private val _last = MutableStateFlow<SyncResult?>(null)
    /** The latest result, for the Following screen. */
    val last: StateFlow<SyncResult?> = _last.asStateFlow()

    /**
     * Follow [id], with its history marked as already seen. An id with no file
     * to read is taken to be one not on the register yet (see
     * [TrackingState.awaiting]); the next check corrects that if it was only
     * offline.
     */
    suspend fun track(id: String) {
        val detail = repo.detail(id).value.value ?: repo.fetchDetail(id)
        store.track(id, detail?.let(TrackedEvents::of).orEmpty())
        if (detail == null) store.updateAwaiting(add = listOf(id)) else store.updateAwaiting(remove = listOf(id))
        _last.value = _last.value?.let { r ->
            val added = detail?.let(TrackedEvents::of).orEmpty()
            r.copy(events = TrackedEvents.inboxOrder(r.events + added))
        }
    }

    suspend fun untrack(id: String) {
        store.untrack(id)
        _last.value = _last.value?.let { r ->
            r.copy(events = r.events.filter { it.mergerId != id || it.industryCode != null })
        }
    }

    /** Follow an industry, with what's already in it marked as seen. */
    suspend fun trackIndustry(code: String, name: String) {
        val file = repo.division(code).value.value ?: repo.fetchDivision(code)
        // A file without the node is a code with nothing in it: nothing to mark.
        val existing = file?.let { f -> f.node(code)?.let(TrackedEvents::industry).orEmpty() }
        store.trackIndustry(code, name, existing)
        _last.value = _last.value?.let { r -> r.copy(events = TrackedEvents.inboxOrder(r.events + existing.orEmpty())) }
    }

    suspend fun untrackIndustry(code: String) {
        store.untrackIndustry(code)
        _last.value = _last.value?.let { r -> r.copy(events = r.events.filter { it.industryCode != code }) }
    }

    suspend fun sync(): SyncResult = mutex.withLock {
        val start = store.current()

        // One conditional request that says which files have changed, so an
        // unchanged site costs a single 304. Null (no manifest yet, or one this
        // version can't read) falls back to fetching everything.
        val manifest = repo.fetchManifest()

        // Opted in to following every phase 2 matter: pick up any current one
        // not acted on before. Recorded once each, so one the follower later
        // drops stays dropped; its history is marked seen like a manual follow.
        if (start.autoTrackPhase2) {
            (manifest?.phase2_current ?: repo.fetchPhase2Current())?.let { current ->
                val done = store.autoTrackedPhase2Ids()
                val toRecord = current.filter { it !in done }
                toRecord.filter { !start.isTracked(it) }.forEach { track(it) }
                store.recordAutoTrackedPhase2(toRecord)
            }
        }

        // Fetch every followed merger, then follow any re-filing they point
        // forward to (once per link), and fetch those too, down the chain.
        // A followed id the manifest doesn't have isn't on the register yet:
        // there's no file to ask for, and its arrival is a new id (see Push).
        // Without a manifest, one already known to be waiting that still has
        // no file is taken to be waiting still, not a failure to retry.
        val details = LinkedHashMap<String, MergerDetail>()
        val failed = mutableListOf<String>()
        val wasAwaiting = store.current().awaiting
        val awaiting = mutableSetOf<String>()
        var pending = store.current().tracked
        var rounds = 0
        while (pending.isNotEmpty() && rounds++ < 5) {
            val (unpublished, published) =
                if (manifest == null) emptyList<String>() to pending else pending.partition { it !in manifest.mergers }
            awaiting += unpublished
            val fetched = fetchAll(published, manifest)
            published.forEach { id ->
                val m = fetched[id]
                when {
                    m != null -> details[id] = m
                    manifest == null && id in wasAwaiting -> awaiting += id
                    else -> failed += id
                }
            }
            val links = store.autoTrackLinks()
            val tracked = store.current().trackedSet
            val newLinks = mutableListOf<String>()
            val newIds = mutableListOf<String>()
            fetched.values.forEach { m ->
                val to = TrackedEvents.forwardRefile(m) ?: return@forEach
                val link = "${m.merger_id}->$to"
                if (link in links) return@forEach
                newLinks += link
                if (to !in tracked && to !in newIds) newIds += to
            }
            store.recordAutoTrackLinks(newLinks)
            // Not marked seen: being told the matter was re-filed is the point.
            store.trackQuietly(newIds)
            pending = newIds
        }

        // Arrived (or found to be waiting): the arrival's history is all news,
        // since nothing of it was marked seen when it was followed.
        store.updateAwaiting(add = awaiting, remove = details.keys)
        // And the list, so it shows on the merger list and in the Following screen by name.
        if (details.keys.any { it in wasAwaiting }) repo.fetchList()

        val fromIndustries = industryEvents(manifest)

        val state = store.current()
        val events = TrackedEvents.inboxOrder(
            details.values.filter { state.isTracked(it.merger_id) }.flatMap(TrackedEvents::of) + fromIndustries,
        )
        val fresh = events.filter { it.key !in state.notified && it.key !in state.seen }

        val newFilings = if (state.notifyNewFilings) newFilings(state, manifest) else emptyList()

        SyncResult(events, fresh, newFilings, failed).also { _last.value = it }
    }

    /**
     * Filings and determinations in every followed industry. Industries under
     * the one division share a file, so several cost one fetch.
     */
    private suspend fun industryEvents(manifest: Manifest?): List<TrackedEvent> {
        val followed = store.current().industries
        if (followed.isEmpty()) return emptyList()
        val files = coroutineScope {
            followed.groupBy { IndustryDivisions.stem(it.code) }.values
                .map { group -> async { IndustryDivisions.stem(group.first().code) to repo.divisionFor(group.first().code, manifest) } }
                .awaitAll().toMap()
        }
        val pending = store.pendingIndustryBaselines()
        return followed.flatMap { ind ->
            val file = files[IndustryDivisions.stem(ind.code)] ?: return@flatMap emptyList()
            val events = file.node(ind.code)?.let(TrackedEvents::industry).orEmpty()
            // Followed while offline: what was there already isn't news.
            if (ind.code in pending) store.settleIndustryBaseline(ind.code, events.map { it.key })
            events
        }
    }

    private suspend fun fetchAll(ids: List<String>, manifest: Manifest?): Map<String, MergerDetail> = coroutineScope {
        ids.map { id -> async { id to repo.detailFor(id, manifest) } }.awaitAll()
            .mapNotNull { (id, m) -> m?.let { id to it } }.toMap()
    }

    /**
     * Mergers in the list that weren't there last time. The first check after
     * switching alerts on only records what's there, so it doesn't announce
     * the whole register.
     *
     * With a manifest, its ids say whether anything is new, and the list
     * (~115 KB) is fetched only when something is, for the names.
     */
    private suspend fun newFilings(state: TrackingState, manifest: Manifest?): List<MergerSummary> {
        if (manifest != null) {
            val ids = manifest.mergers.keys
            val known = store.knownIds()
            val fresh = ids - known
            if (known.isEmpty() || fresh.isEmpty()) {
                if (ids != known) store.setKnownIds(ids)
                return emptyList()
            }
            // Not recorded until the names are in hand, so a failed fetch tries again next time.
            val list = repo.fetchList() ?: return emptyList()
            store.setKnownIds(ids)
            return list.filter { it.merger_id in fresh && !state.isTracked(it.merger_id) }
        }
        val list = repo.fetchList() ?: return emptyList()
        val known = store.knownIds()
        store.setKnownIds(list.map { it.merger_id }.toSet())
        if (known.isEmpty()) return emptyList()
        return list.filter { it.merger_id !in known && !state.isTracked(it.merger_id) }
    }
}
