package fyi.mergers.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fyi.mergers.app.MainActivity
import fyi.mergers.app.R
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.mergerPageUrl
import fyi.mergers.app.tracking.TrackedEvent

/** Posts what a check found: one notification per followed merger, one per new filing. */
object Notifier {
    const val CHANNEL_TRACKED = "tracked"
    const val CHANNEL_NEW = "new_filings"
    private const val GROUP_TRACKED = "fyi.mergers.app.TRACKED"
    private const val GROUP_NEW = "fyi.mergers.app.NEW"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRACKED, "Followed mergers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New documents, determinations and deadlines on mergers you follow"
                },
                NotificationChannel(CHANNEL_NEW, "New filings", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Every new merger or waiver on the ACCC register"
                },
            ),
        )
    }

    fun canPost(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the merger in the app (the activity handles mergers.fyi links). */
    private fun open(context: Context, id: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mergerPageUrl(id)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun notificationId(mergerId: String) = mergerId.hashCode()

    /** A line for one event: "Phase 1 determination: Approved" or "Determination due 12 Dec". */
    fun line(e: TrackedEvent): String =
        if (e.upcoming) "${e.title} ${Dates.format(e.date, compact = true) ?: ""}".trim() else e.title

    /** Returns false when notifications are off, so the caller knows nothing was shown. */
    fun postTracked(context: Context, fresh: List<TrackedEvent>): Boolean {
        if (fresh.isEmpty() || !canPost(context)) return false
        val nm = NotificationManagerCompat.from(context)
        val byMerger = fresh.groupBy { it.mergerId }
        byMerger.forEach { (id, events) ->
            val name = events.first().mergerName
            val lines = events.map(::line)
            val style = NotificationCompat.InboxStyle().setBigContentTitle(name)
            lines.take(6).forEach { style.addLine(it) }
            if (lines.size > 6) style.setSummaryText("+${lines.size - 6} more")
            val n = NotificationCompat.Builder(context, CHANNEL_TRACKED)
                .setSmallIcon(R.drawable.ic_stat_merger)
                .setContentTitle(name)
                .setContentText(if (lines.size == 1) lines.first() else "${lines.size} updates: ${lines.first()}")
                .setStyle(style)
                .setSubText(id)
                .setContentIntent(open(context, id))
                .setAutoCancel(true)
                .setGroup(GROUP_TRACKED)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .build()
            @Suppress("MissingPermission")
            nm.notify(notificationId(id), n)
        }
        if (byMerger.size > 1) summary(context, GROUP_TRACKED, CHANNEL_TRACKED, "${byMerger.size} followed mergers updated")
        return true
    }

    fun postNewFilings(context: Context, filings: List<MergerSummary>): Boolean {
        if (filings.isEmpty() || !canPost(context)) return false
        val nm = NotificationManagerCompat.from(context)
        filings.take(10).forEach { m ->
            val kind = if (m.is_waiver) "New waiver application" else "New merger notified"
            val n = NotificationCompat.Builder(context, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_stat_merger)
                .setContentTitle(m.merger_name)
                .setContentText(kind)
                .setSubText(m.merger_id)
                .setContentIntent(open(context, m.merger_id))
                .setAutoCancel(true)
                .setGroup(GROUP_NEW)
                .build()
            @Suppress("MissingPermission")
            nm.notify("new", notificationId(m.merger_id), n)
        }
        if (filings.size > 1) summary(context, GROUP_NEW, CHANNEL_NEW, "${filings.size} new filings")
        return true
    }

    private fun summary(context: Context, group: String, channel: String, text: String) {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_merger)
            .setContentTitle(text)
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(group, 0, n)
    }

    /** Clear a merger's notification once it has been opened in the app. */
    fun cancel(context: Context, mergerId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(mergerId))
    }
}
