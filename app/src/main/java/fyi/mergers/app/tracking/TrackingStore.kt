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

private val Context.trackingStore by preferencesDataStore(name = "tracking")

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
) {
    val trackedSet: Set<String> by lazy { tracked.toSet() }
    fun isTracked(id: String) = id in trackedSet
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
    }

    val state: Flow<TrackingState> = store.data.map { it.toState() }

    suspend fun current(): TrackingState = state.first()

    private fun Preferences.toState() = TrackingState(
        tracked = tracked(),
        seen = this[K.SEEN].orEmpty(),
        notified = this[K.NOTIFIED].orEmpty(),
        autoTrackPhase2 = this[K.AUTO_TRACK_PHASE2] ?: false,
        notifyNewFilings = this[K.NOTIFY_NEW_FILINGS] ?: false,
    )

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
        store.edit { p -> p.setTracked(p.tracked() - id) }
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
