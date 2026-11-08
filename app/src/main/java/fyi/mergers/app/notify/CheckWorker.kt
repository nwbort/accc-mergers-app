package fyi.mergers.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.mergers.app.Graph
import java.util.concurrent.TimeUnit

/**
 * The background check: every couple of hours, with a network, bring the
 * follow list up to date and notify what's new. mergers.fyi refreshes several
 * times a weekday, and the ETags make an unchanged file nearly free.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val state = Graph.store.current()
        if (state.followsNothing && !state.autoTrackPhase2 && !state.notifyNewFilings) return Result.success()
        val result = Graph.tracker.sync()
        // Only mark what was actually shown: with notifications off, the
        // events wait in the Following inbox instead.
        if (Notifier.postTracked(applicationContext, result.fresh)) {
            Graph.store.markNotified(result.fresh.map { it.key })
        }
        Notifier.postNewFilings(applicationContext, result.newFilings)
        return if (result.failed.isNotEmpty() && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(2, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
