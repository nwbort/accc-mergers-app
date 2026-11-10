package fyi.mergers.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.mergers.app.Graph
import fyi.mergers.app.tracking.TrackedEvents
import java.util.concurrent.TimeUnit

/**
 * The background check: with a network, bring the follow list up to date and
 * notify what's new. Run when mergers.fyi pushes word that something followed
 * has changed ([checkNow], from [PushService]), and on a schedule as the
 * fallback: every couple of hours until push is known to work on this phone,
 * twice a day after. The manifest's ETag makes an unchanged site nearly free.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val state = Graph.store.current()
        if (state.followsNothing && !state.autoTrackPhase2 && !state.notifyNewFilings) return Result.success()
        val result = Graph.tracker.sync()
        // Only mark what was actually shown: with notifications off, the
        // events wait in the Following inbox instead. An industry's line about
        // a merger that's followed itself counts as shown by the merger's own.
        val toPost = TrackedEvents.toNotify(result.fresh, Graph.store.current().trackedSet)
        if (toPost.isEmpty() || Notifier.postTracked(applicationContext, toPost)) {
            Graph.store.markNotified(result.fresh.map { it.key })
        }
        Notifier.postNewFilings(applicationContext, result.newFilings)
        return if (result.failed.isNotEmpty() && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "check"
        private const val NOW = "check-now"

        /** Hours between scheduled checks while push can't be relied on, and once it can. */
        private const val POLL_HOURS = 2L
        private const val FALLBACK_HOURS = 12L

        fun schedule(context: Context) {
            val hours = if (Push.ready(context)) FALLBACK_HOURS else POLL_HOURS
            val request = PeriodicWorkRequestBuilder<CheckWorker>(hours, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Check as soon as there's a network. A deploy that changes several
         * followed files sends one message for each; a check already waiting
         * covers them all, since every message goes out after the deploy is live.
         */
        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
