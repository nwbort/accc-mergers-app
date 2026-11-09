package fyi.mergers.app.widget

import android.content.Context
import androidx.core.content.edit

/**
 * Each Coming up widget's settings, by its widget id. Plain preferences, and
 * not backed up: widget ids don't survive a restore.
 */
object WidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    private fun followedKey(id: Int) = "upcoming.$id.followed"
    private fun hiddenKey(id: Int) = "upcoming.$id.hidden"

    fun upcoming(context: Context, id: Int): UpcomingConfig {
        val p = prefs(context)
        return UpcomingConfig(
            followedOnly = p.getBoolean(followedKey(id), false),
            hidden = p.getStringSet(hiddenKey(id), null).orEmpty()
                .mapNotNull { name -> EventKind.choices.firstOrNull { it.name == name } }.toSet(),
        )
    }

    fun setUpcoming(context: Context, id: Int, config: UpcomingConfig) = prefs(context).edit {
        putBoolean(followedKey(id), config.followedOnly)
        putStringSet(hiddenKey(id), config.hidden.map { it.name }.toSet())
    }

    fun forget(context: Context, ids: IntArray) = prefs(context).edit {
        ids.forEach { remove(followedKey(it)); remove(hiddenKey(it)) }
    }

    /** True at most once per [everyMs] for [what]: keeps a widget's own upkeep from repeating. */
    fun due(context: Context, what: String, everyMs: Long): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong(what, 0) in 0 until everyMs) return false
        p.edit { putLong(what, now) }
        return true
    }
}
