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
import fyi.mergers.app.data.industryPageUrl
import fyi.mergers.app.data.mergerPageUrl
import fyi.mergers.app.tracking.TrackedEvent

/**
 * Posts what a check found: one notification per followed merger, one per
 * followed industry, one per new filing.
 */
object Notifier {
    const val CHANNEL_TRACKED = "tracked"
    const val CHANNEL_INDUSTRIES = "industries"
    const val CHANNEL_NEW = "new_filings"
    private const val GROUP_TRACKED = "fyi.mergers.app.TRACKED"
    private const val GROUP_INDUSTRIES = "fyi.mergers.app.INDUSTRIES"
    private const val GROUP_NEW = "fyi.mergers.app.NEW"
    private const val TAG_INDUSTRY = "industry"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRACKED, "Followed mergers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New documents, determinations and deadlines on mergers you follow"
                },
                NotificationChannel(CHANNEL_INDUSTRIES, "Followed industries", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New mergers filed and determinations published in industries you follow"
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
    private fun open(context: Context, id: String): PendingIntent = openUrl(context, mergerPageUrl(id))

    private fun openUrl(context: Context, url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, url.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun industryNotificationId(code: String) = code.hashCode()

    fun notificationId(mergerId: String) = mergerId.hashCode()

    /**
     * A line for one event in the Following inbox: the register's full title
     * ("Phase 1 - initial assessment determination: Approved"), "Determination
     * due 12 Dec", or for a followed industry "New merger filed · Retail Trade".
     */
    fun line(e: TrackedEvent): String =
        if (e.industryCode != null) "${e.title} · ${e.industryName ?: e.industryCode}"
        else if (e.upcoming) due(e) else e.title

    /**
     * The same event in a notification, short enough for the collapsed line:
     * "Phase 1: Approved", "Determination due 12 Dec", or for a followed
     * industry "Phase 1 approved: Coles – Kalgoorlie".
     */
    fun short(e: TrackedEvent): String = e.short ?: if (e.upcoming) due(e) else Wording.short(e.title)

    private fun due(e: TrackedEvent) = "${e.title} ${Dates.format(e.date, compact = true) ?: ""}".trim()

    /** What happened, newest first, then what's due, soonest first. */
    private fun ordered(events: List<TrackedEvent>): List<TrackedEvent> {
        val (due, happened) = events.partition { it.upcoming }
        return happened.sortedByDescending { it.date ?: "" } + due.sortedBy { it.date ?: "" }
    }

    /** "Phase 1: Approved", or with more, "Phase 1: Approved +2 more". */
    private fun collapsed(lines: List<String>) =
        if (lines.size == 1) lines.first() else "${lines.first()} +${lines.size - 1} more"

    /**
     * One event: its line again, in full if it's too long for the collapsed
     * view. Several: a line each, up to six.
     */
    private fun style(title: String, lines: List<String>): NotificationCompat.Style {
        if (lines.size == 1) return NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(lines.first())
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText("+${lines.size - 6} more")
        return style
    }

    /** Returns false when notifications are off, so the caller knows nothing was shown. */
    fun postTracked(context: Context, fresh: List<TrackedEvent>): Boolean {
        if (fresh.isEmpty() || !canPost(context)) return false
        val (fromIndustries, fromMergers) = fresh.partition { it.industryCode != null }
        postMergers(context, fromMergers)
        postIndustries(context, fromIndustries)
        return true
    }

    private fun postMergers(context: Context, fresh: List<TrackedEvent>) {
        if (fresh.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        val byMerger = fresh.groupBy { it.mergerId }
        byMerger.forEach { (id, events) ->
            val name = events.first().mergerName
            val sorted = ordered(events)
            val lines = sorted.map(::short)
            val n = NotificationCompat.Builder(context, CHANNEL_TRACKED)
                .setSmallIcon(R.drawable.ic_stat_merger)
                .setContentTitle(name)
                .setContentText(collapsed(lines))
                .setStyle(style(name, lines))
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
    }

    /** One per industry, its filings and determinations as lines: "New merger: Coles – …". */
    private fun postIndustries(context: Context, fresh: List<TrackedEvent>) {
        if (fresh.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        val byIndustry = fresh.groupBy { it.industryCode!! }
        byIndustry.forEach { (code, events) ->
            val name = events.first().industryName ?: code
            val sorted = ordered(events)
            val lines = sorted.map(::short)
            val n = NotificationCompat.Builder(context, CHANNEL_INDUSTRIES)
                .setSmallIcon(R.drawable.ic_stat_merger)
                .setContentTitle(name)
                .setContentText(collapsed(lines))
                .setStyle(style(name, lines))
                .setSubText("ANZSIC $code")
                .setContentIntent(openUrl(context, industryPageUrl(code)))
                .setAutoCancel(true)
                .setGroup(GROUP_INDUSTRIES)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .build()
            @Suppress("MissingPermission")
            nm.notify(TAG_INDUSTRY, industryNotificationId(code), n)
        }
        if (byIndustry.size > 1) summary(context, GROUP_INDUSTRIES, CHANNEL_INDUSTRIES, "${byIndustry.size} followed industries updated")
    }

    fun postNewFilings(context: Context, filings: List<MergerSummary>): Boolean {
        if (filings.isEmpty() || !canPost(context)) return false
        val nm = NotificationManagerCompat.from(context)
        filings.take(10).forEach { m ->
            val kind = Wording.newFiling(m)
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

    /** Clear an industry's notification once it has been opened in the app. */
    fun cancelIndustry(context: Context, code: String) {
        NotificationManagerCompat.from(context).cancel(TAG_INDUSTRY, industryNotificationId(code))
    }

    /** Clear a merger's notification once it has been opened in the app. */
    fun cancel(context: Context, mergerId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(mergerId))
    }
}
