package fyi.mergers.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fyi.mergers.app.MainActivity
import fyi.mergers.app.R

/** The Search widget: a bar on the home screen that opens the app's search, keyboard up. */
class SearchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_search).apply {
                setOnClickPendingIntent(android.R.id.background, search(context))
            }

        private fun search(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(MainActivity.ACTION_SEARCH, null, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
