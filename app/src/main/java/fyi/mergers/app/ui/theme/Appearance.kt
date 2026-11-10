package fyi.mergers.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import androidx.core.content.edit

/** Light or dark: as the phone is set, or always one of them. */
enum class ThemeChoice(val label: String, internal val nightMode: Int) {
    SYSTEM("System", UiModeManager.MODE_NIGHT_AUTO),
    LIGHT("Light", UiModeManager.MODE_NIGHT_NO),
    DARK("Dark", UiModeManager.MODE_NIGHT_YES),
}

/**
 * The app's appearance settings. Plain preferences (backed up with the follow
 * list, see res/xml), read synchronously so the first frame is already right.
 *
 * The choice is also handed to the system ([UiModeManager.setApplicationNightMode]),
 * so the window, splash screen and status bar icons follow it, not only what
 * Compose draws; the activity is recreated when it changes.
 */
object Appearance {
    private const val KEY = "theme"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun theme(context: Context): ThemeChoice =
        prefs(context).getString(KEY, null)
            ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } }
            ?: ThemeChoice.SYSTEM

    fun setTheme(context: Context, choice: ThemeChoice) {
        prefs(context).edit { putString(KEY, choice.name) }
        apply(context, choice)
    }

    /** Hand the saved choice to the system again: after a restore, it only has the preference. */
    fun apply(context: Context, choice: ThemeChoice = theme(context)) {
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(choice.nightMode)
    }
}
