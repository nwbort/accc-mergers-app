package fyi.mergers.app.ui

import android.content.Context
import androidx.core.content.edit
import fyi.mergers.app.data.Dates
import java.time.Duration

/**
 * Whether the digest leads with its email signup. Dismissed, it stays away
 * for [QUIET]; once an address has been sent, for good. Kept in the plain
 * settings preferences, beside the theme.
 */
object SignupPrompt {
    val QUIET: Duration = Duration.ofDays(60)

    internal const val DISMISSED_AT = "digestSignup.dismissedAt"
    internal const val SIGNED_UP = "digestSignup.signedUp"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun shows(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(SIGNED_UP, false)) return false
        if (!p.contains(DISMISSED_AT)) return true
        return Dates.clock.millis() - p.getLong(DISMISSED_AT, 0) >= QUIET.toMillis()
    }

    fun dismiss(context: Context) = prefs(context).edit { putLong(DISMISSED_AT, Dates.clock.millis()) }

    fun signedUp(context: Context) = prefs(context).edit { putBoolean(SIGNED_UP, true) }
}
