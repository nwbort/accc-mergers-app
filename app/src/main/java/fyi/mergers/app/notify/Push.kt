package fyi.mergers.app.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fyi.mergers.app.Graph
import fyi.mergers.app.data.IndustryDivisions
import fyi.mergers.app.tracking.TrackingState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Push: mergers.fyi tells the phone when something it follows has changed,
 * rather than the phone asking every couple of hours.
 *
 * Firebase Cloud Messaging topics, one per thing that can be followed. The
 * phone subscribes to the topics for what it follows, so the follow list stays
 * on the phone: there's no server holding it, only Google's topic lists. After
 * each deploy, the site's push-notifications workflow (scripts/push/fcm.py in
 * nwbort/accc-mergers) compares data/manifest.json with the one it last saw and
 * sends a message to the topic of every file that changed.
 *
 * A message carries no news, only "check now": it runs the same [CheckWorker]
 * as the schedule, so what counts as news is still decided in one place
 * (TrackedEvents). A change that isn't news costs a check that finds nothing.
 *
 * The periodic check stays as the fallback, every 12 hours once push is known
 * to work ([ready]) and every 2 until then, or forever on a phone without
 * Google Play services, or in a build without a Firebase project.
 */
object Push {
    /**
     * The topic names, shared with scripts/push/fcm.py: change both or neither.
     * FCM allows [A-Za-z0-9-_.~%] in a name; merger ids (MN-01016) and
     * industry file stems (G, _orphans) are already within that.
     */
    object Topics {
        /** Anything at all: sent instead of the rest when a deploy changes too much to list. */
        const val ALL = "all"

        /** New merger ids, or a change to which matters are in phase 2. */
        const val LIST = "list"

        fun merger(id: String) = "merger-$id"

        /** The topic of industries/{stem}.json, which every code under that division shares. */
        fun industry(code: String) = "industry-${IndustryDivisions.stem(code)}"
    }

    /** The topics a phone in [state] should be subscribed to. */
    fun topics(state: TrackingState): Set<String> {
        val wantsList = state.autoTrackPhase2 || state.notifyNewFilings
        if (state.followsNothing && !wantsList) return emptySet()
        return buildSet {
            add(Topics.ALL)
            if (wantsList) add(Topics.LIST)
            state.tracked.forEach { add(Topics.merger(it)) }
            state.industries.forEach { add(Topics.industry(it.code)) }
        }
    }

    /** A Firebase project is configured in this build (see app/build.gradle.kts). */
    fun configured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Push has worked on this phone: a token was issued and the topics subscribed. */
    fun ready(context: Context): Boolean = prefs(context).getBoolean(READY, false)

    /** Keep the topic subscriptions in step with the follow list, from now on. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (!configured(app)) return
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        Graph.scope.launch {
            Graph.store.state.map(::topics).distinctUntilChanged().collect { TopicSyncWorker.enqueue(app) }
        }
    }

    internal fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    internal const val SUBSCRIBED = "subscribed_topics"
    internal const val READY = "ready"
}

/**
 * Brings the phone's topic subscriptions in line with [Push.topics]. Run after
 * every change to the follow list, with a network and retries, since a follow
 * made offline must still end up subscribed. What's subscribed is recorded
 * topic by topic, so an interrupted run picks up where it stopped.
 */
class TopicSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Push.configured(applicationContext)) return Result.success()
        val prefs = Push.prefs(applicationContext)
        val want = Push.topics(Graph.store.current())
        val have = prefs.getStringSet(Push.SUBSCRIBED, null).orEmpty().toMutableSet()
        if (want == have && (want.isEmpty() || Push.ready(applicationContext))) return Result.success()

        val messaging = FirebaseMessaging.getInstance()
        return try {
            if (want.isNotEmpty()) messaging.token.await()
            for (topic in want - have) {
                messaging.subscribeToTopic(topic).await()
                have += topic
                prefs.edit().putStringSet(Push.SUBSCRIBED, have.toSet()).apply()
            }
            for (topic in have - want) {
                messaging.unsubscribeFromTopic(topic).await()
                have -= topic
                prefs.edit().putStringSet(Push.SUBSCRIBED, have.toSet()).apply()
            }
            if (want.isNotEmpty() && !Push.ready(applicationContext)) {
                prefs.edit().putBoolean(Push.READY, true).apply()
                // Push works: the schedule can drop back to a fallback.
                CheckWorker.schedule(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            // No Play services, or no network to Google: the schedule carries on.
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "push-topics"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TopicSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Receives the site's messages, and a new token's need to subscribe again. */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        CheckWorker.checkNow(applicationContext)
    }

    /** Topic subscriptions belong to a token: a new one starts with none. */
    override fun onNewToken(token: String) {
        Push.prefs(this).edit().remove(Push.SUBSCRIBED).apply()
        TopicSyncWorker.enqueue(applicationContext)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
