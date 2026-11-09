package fyi.mergers.app.tracking

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fyi.mergers.app.data.MergersJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.trackingStore by preferencesDataStore(name = "tracking")

/** A followed industry: its ANZSIC code, and its name for showing before its file loads. */
@Serializable
data class FollowedIndustry(val code: String, val name: String)

/** What the follower has chosen, and what they've already been told about. */
data class TrackingState(
    /** Followed merger ids, oldest first. */
    val tracked: List<String> = emptyList(),
    /** Event keys read in the app. */
    val seen: Set<String> = emptySet(),
    /** Event keys already posted as a notification (or read before they could be). */
    val notified: Set<String> = emptySet(),
    /** Follow every matter that reaches phase 2. */
    val autoTrackPhase2: Boolean = false,
    /** Notify about every new merger filed, followed or not. */
    val notifyNewFilings: Boolean = false,
    /** Followed industries, oldest first: new filings and determinations in each are news. */
    val industries: List<FollowedIndustry> = emptyList(),
    /**
     * Followed ids that aren't on the register yet: typed in by someone who
     * knows the number before the ACCC publishes it (a waiver appears only
     * once it's decided). Their arrival is the news.
     */
    val awaiting: Set<String> = emptySet(),
) {
    val trackedSet: Set<String> by lazy { tracked.toSet() }
    fun isTracked(id: String) = id in trackedSet

    /** Followed, but not on the register yet. */
    fun isAwaiting(id: String) = id in awaiting && id in trackedSet

    val industryCodes: Set<String> by lazy { industries.map { it.code }.toSet() }
    fun isIndustryTracked(code: String) = code in industryCodes

    /** Nothing at all is followed, so there's nothing to check. */
    val followsNothing: Boolean get() = tracked.isEmpty() && industries.isEmpty()
}

/**
 * The follow state on the phone, in DataStore. The same shape as the site's
 * localStorage keys (TrackingContext.jsx), plus the set of events already
 * notified, which the site has no need for.
 */
class TrackingStore(context: Context) {
    private val store = context.applicationContext.trackingStore

    private object K {
        val TRACKED = stringPreferencesKey("tracked")
        val SEEN = stringSetPreferencesKey("seen_events")
        val NOTIFIED = stringSetPreferencesKey("notified_events")
        val AUTO_TRACK_LINKS = stringSetPreferencesKey("auto_track_links")
        val AUTO_TRACK_PHASE2 = booleanPreferencesKey("auto_track_phase2")
        val AUTO_TRACKED_PHASE2_IDS = stringSetPreferencesKey("auto_tracked_phase2_ids")
        val NOTIFY_NEW_FILINGS = booleanPreferencesKey("notify_new_filings")
        val KNOWN_IDS = stringSetPreferencesKey("known_merger_ids")
        val TRACKED_INDUSTRIES = stringPreferencesKey("tracked_industries")
        /** Followed while their file couldn't be read: their history is marked seen once it can. */
        val INDUSTRY_BASELINE = stringSetPreferencesKey("industry_baseline_pending")
        val AWAITING = stringSetPreferencesKey("awaiting_ids")
    }

    val state: Flow<TrackingState> = store.data.map { it.toState() }

    suspend fun current(): TrackingState = state.first()

    private fun Preferences.toState() = TrackingState(
        tracked = tracked(),
        seen = this[K.SEEN].orEmpty(),
        notified = this[K.NOTIFIED].orEmpty(),
        autoTrackPhase2 = this[K.AUTO_TRACK_PHASE2] ?: false,
        notifyNewFilings = this[K.NOTIFY_NEW_FILINGS] ?: false,
        industries = industries(),
        awaiting = this[K.AWAITING].orEmpty(),
    )

    private fun Preferences.industries(): List<FollowedIndustry> =
        this[K.TRACKED_INDUSTRIES]?.let {
            runCatching { MergersJson.decodeFromString<List<FollowedIndustry>>(it) }.getOrNull()
        }.orEmpty()

    private fun MutablePreferences.setIndustries(list: List<FollowedIndustry>) {
        this[K.TRACKED_INDUSTRIES] = MergersJson.encodeToString(list.distinctBy { it.code })
    }

    private fun Preferences.tracked(): List<String> =
        this[K.TRACKED]?.let { runCatching { MergersJson.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private fun MutablePreferences.setTracked(ids: List<String>) {
        this[K.TRACKED] = MergersJson.encodeToString(ids.distinct())
    }

    /**
     * Follow [id]. [existing] are the events it already has: they're marked
     * seen and notified, so following a matter doesn't announce its history.
     */
    suspend fun track(id: String, existing: List<TrackedEvent>) {
        store.edit { p ->
            val ids = p.tracked()
            if (id !in ids) p.setTracked(ids + id)
            val keys = existing.map { it.key }
            p[K.SEEN] = p[K.SEEN].orEmpty() + keys
            p[K.NOTIFIED] = p[K.NOTIFIED].orEmpty() + keys
        }
    }

    suspend fun untrack(id: String) {
        store.edit { p ->
            p.setTracked(p.tracked() - id)
            p[K.AWAITING] = p[K.AWAITING].orEmpty() - id
        }
    }

    /** Which followed ids aren't on the register yet: [add] are now known not to be, [remove] now are. */
    suspend fun updateAwaiting(add: Collection<String> = emptyList(), remove: Collection<String> = emptyList()) {
        if (add.isEmpty() && remove.isEmpty()) return
        store.edit { p ->
            val now = p[K.AWAITING].orEmpty() - remove.toSet() + add
            if (now != p[K.AWAITING].orEmpty()) p[K.AWAITING] = now
        }
    }

    /**
     * Follow an industry. [existing] are the filings and determinations it
     * already has, marked seen so following doesn't announce the back
     * catalogue; null when its file couldn't be read, in which case that's
     * done on the first check that can read it.
     */
    suspend fun trackIndustry(code: String, name: String, existing: List<TrackedEvent>?) {
        store.edit { p ->
            val list = p.industries()
            if (list.none { it.code == code }) p.setIndustries(list + FollowedIndustry(code, name))
            if (existing == null) {
                p[K.INDUSTRY_BASELINE] = p[K.INDUSTRY_BASELINE].orEmpty() + code
            } else {
                val keys = existing.map { it.key }
                p[K.SEEN] = p[K.SEEN].orEmpty() + keys
                p[K.NOTIFIED] = p[K.NOTIFIED].orEmpty() + keys
            }
        }
    }

    suspend fun untrackIndustry(code: String) {
        store.edit { p ->
            p.setIndustries(p.industries().filter { it.code != code })
            p[K.INDUSTRY_BASELINE] = p[K.INDUSTRY_BASELINE].orEmpty() - code
        }
    }

    suspend fun pendingIndustryBaselines(): Set<String> = store.data.first()[K.INDUSTRY_BASELINE].orEmpty()

    /** [code]'s file has now been read: its history so far is [keys], none of it news. */
    suspend fun settleIndustryBaseline(code: String, keys: Collection<String>) {
        store.edit { p ->
            p[K.SEEN] = p[K.SEEN].orEmpty() + keys
            p[K.NOTIFIED] = p[K.NOTIFIED].orEmpty() + keys
            p[K.INDUSTRY_BASELINE] = p[K.INDUSTRY_BASELINE].orEmpty() - code
        }
    }

    /** Follow [ids] without marking anything seen, so their history is news. */
    suspend fun trackQuietly(ids: List<String>) {
        if (ids.isEmpty()) return
        store.edit { p -> p.setTracked(p.tracked() + ids) }
    }

    suspend fun markSeen(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.edit { p ->
            p[K.SEEN] = p[K.SEEN].orEmpty() + keys
            p[K.NOTIFIED] = p[K.NOTIFIED].orEmpty() + keys
        }
    }

    suspend fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.edit { p -> p[K.NOTIFIED] = p[K.NOTIFIED].orEmpty() + keys }
    }

    suspend fun autoTrackLinks(): Set<String> = store.data.first()[K.AUTO_TRACK_LINKS].orEmpty()

    suspend fun recordAutoTrackLinks(links: Collection<String>) {
        if (links.isEmpty()) return
        store.edit { p -> p[K.AUTO_TRACK_LINKS] = p[K.AUTO_TRACK_LINKS].orEmpty() + links }
    }

    suspend fun autoTrackedPhase2Ids(): Set<String> = store.data.first()[K.AUTO_TRACKED_PHASE2_IDS].orEmpty()

    suspend fun recordAutoTrackedPhase2(ids: Collection<String>) {
        if (ids.isEmpty()) return
        store.edit { p -> p[K.AUTO_TRACKED_PHASE2_IDS] = p[K.AUTO_TRACKED_PHASE2_IDS].orEmpty() + ids }
    }

    suspend fun setAutoTrackPhase2(on: Boolean) {
        store.edit { it[K.AUTO_TRACK_PHASE2] = on }
    }

    suspend fun setNotifyNewFilings(on: Boolean) {
        store.edit { it[K.NOTIFY_NEW_FILINGS] = on }
    }

    /** Every merger id seen in the list so far; empty until the first check. */
    suspend fun knownIds(): Set<String> = store.data.first()[K.KNOWN_IDS].orEmpty()

    suspend fun setKnownIds(ids: Set<String>) {
        store.edit { it[K.KNOWN_IDS] = ids }
    }
}
