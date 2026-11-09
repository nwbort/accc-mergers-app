package fyi.mergers.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.mergers.app.Graph
import fyi.mergers.app.MainActivity
import fyi.mergers.app.R
import fyi.mergers.app.data.SITE
import fyi.mergers.app.data.UpcomingEvent
import fyi.mergers.app.data.mergerPageUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Coming up widget: the dashboard's list of dates (upcoming-events.json)
 * on the home screen, for every merger or only the followed ones, and only the
 * kinds of date each widget was set to show ([UpcomingConfig]). A date opens
 * its merger; the title opens the dashboard.
 *
 * It's drawn from the copy of the file kept on the phone, so it's never
 * waiting on the network: when the app refreshes the file or the follow list
 * changes ([watch]), and every hour ([onUpdate]), which also rolls "Today"
 * over and asks [UpcomingRefreshWorker] for a fresh copy.
 */
class UpcomingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Null when it isn't the system calling (the tests).
        val pending: PendingResult? = goAsync()
        Graph.scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending?.finish()
            }
        }
        UpcomingRefreshWorker.ask(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetPrefs.forget(context, ids)
    }

    companion object {
        /** Redraw every Coming up widget whenever the dates or the follow list change. */
        fun watch(context: Context, scope: CoroutineScope) = scope.launch {
            combine(Graph.repo.upcoming.map { it.value }, Graph.store.state.map { it.trackedSet }, ::Pair)
                .distinctUntilChanged()
                .collect { runCatching { updateAll(context) } }
        }

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, UpcomingWidget::class.java))
            val events = Graph.repo.keptUpcoming()
            if (ids.isNotEmpty()) render(context, manager, ids, events)
            if (events != null) preview(context, manager, events)
        }

        suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            events: List<UpcomingEvent>? = null,
        ) {
            val all = events ?: Graph.repo.keptUpcoming()
            val followed = Graph.store.current().trackedSet
            ids.forEach { id ->
                manager.updateAppWidget(id, views(context, WidgetPrefs.upcoming(context, id), all, followed))
            }
        }

        /**
         * The launcher's picker shows the widget with the real dates in it,
         * not an empty card. The system limits how often it can be told, and
         * once a day is plenty.
         */
        private fun preview(context: Context, manager: AppWidgetManager, events: List<UpcomingEvent>) {
            if (!WidgetPrefs.due(context, "upcoming.preview", 24 * 60 * 60 * 1000L)) return
            runCatching {
                manager.setWidgetPreview(
                    ComponentName(context, UpcomingWidget::class.java),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    views(context, UpcomingConfig(), events, emptySet()),
                )
            }
        }

        /** The widget as [config] shows [events] (null: nothing kept on the phone yet). */
        fun views(
            context: Context,
            config: UpcomingConfig,
            events: List<UpcomingEvent>?,
            followed: Set<String>,
        ): RemoteViews {
            val pkg = context.packageName
            val views = RemoteViews(pkg, R.layout.widget_upcoming)
            views.setOnClickPendingIntent(R.id.widget_header, open(context, "$SITE/"))
            val summary = config.summary
            views.setTextViewText(R.id.widget_subtitle, summary)
            views.setViewVisibility(R.id.widget_subtitle, if (summary == null) View.GONE else View.VISIBLE)

            val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(2)
            val rows = Upcoming.rows(Upcoming.events(events.orEmpty(), config, followed))
            rows.forEachIndexed { i, row ->
                val item = when (row) {
                    is UpcomingRow.Day -> RemoteViews(pkg, R.layout.widget_upcoming_day).apply {
                        setTextViewText(R.id.widget_day, row.label)
                    }
                    is UpcomingRow.Event -> RemoteViews(pkg, R.layout.widget_upcoming_event).apply {
                        val look = look(EventKind.of(row.event.type), row.event.type)
                        setImageViewResource(R.id.widget_tile, look.icon)
                        setColorStateList(R.id.widget_tile, "setBackgroundTintList", look.tint)
                        setColorStateList(R.id.widget_tile, "setImageTintList", look.ink)
                        setTextViewText(R.id.widget_name, row.event.merger_name)
                        setTextViewText(R.id.widget_kind, row.detail)
                        setOnClickFillInIntent(
                            R.id.widget_row,
                            Intent().setData(Uri.parse(mergerPageUrl(row.event.merger_id))),
                        )
                    }
                }
                items.addItem(i.toLong(), item)
            }
            views.setRemoteAdapter(R.id.widget_list, items.build())
            views.setPendingIntentTemplate(R.id.widget_list, openTemplate(context))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setTextViewText(R.id.widget_empty, Upcoming.emptyText(events != null, config, followed))
            return views
        }

        private class Look(val icon: Int, val tint: Int, val ink: Int)

        /** A kind's tile, as the dashboard's (eventTypeIcon, eventTypeColors). */
        private fun look(kind: EventKind, type: String?): Look = when (kind) {
            EventKind.CONSULTATION -> Look(R.drawable.ic_widget_forum, R.color.event_blue_tint, R.color.event_blue_ink)
            EventKind.CONCERNS ->
                Look(R.drawable.ic_widget_warning_amber, R.color.event_amber_tint, R.color.event_amber_ink)
            EventKind.PUBLIC_BENEFIT -> Look(
                if (type == "public_benefit_response_due") R.drawable.ic_widget_forum else R.drawable.ic_widget_balance,
                R.color.event_teal_tint, R.color.event_teal_ink,
            )
            EventKind.DETERMINATION ->
                Look(R.drawable.ic_widget_gavel, R.color.event_purple_tint, R.color.event_purple_ink)
            EventKind.HEARING -> Look(R.drawable.ic_widget_balance, R.color.event_rose_tint, R.color.event_rose_ink)
            EventKind.OTHER -> Look(R.drawable.ic_widget_gavel, R.color.event_gray_tint, R.color.event_gray_ink)
        }

        /** Opens a mergers.fyi page in the app, as a notification does. */
        private fun open(context: Context, url: String): PendingIntent = PendingIntent.getActivity(
            context, url.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** The list's rows share this; each fills in its own merger's link. */
        private fun openTemplate(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Fetches upcoming-events.json for the widget when there's a network (a 304
 * when nothing has changed) and redraws. Asked for by the widget's hourly
 * update, and no more often than every quarter of an hour, whatever asks.
 */
class UpcomingRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Graph.repo.fetchUpcoming()
        UpcomingWidget.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun ask(context: Context) {
            if (!WidgetPrefs.due(context, "upcoming.refresh", 15 * 60 * 1000L)) return
            val request = OneTimeWorkRequestBuilder<UpcomingRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("widget-upcoming", ExistingWorkPolicy.KEEP, request)
        }
    }
}
