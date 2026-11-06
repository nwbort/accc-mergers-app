package fyi.mergers.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fyi.mergers.app.data.Status

/** The site's own colours (tailwind.config.js): primary #335145, accent #10b981. */
val Brand = Color(0xFF335145)
val Accent = Color(0xFF10B981)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E8DC),
    onPrimaryContainer = Color(0xFF0E2A1E),
    secondary = Color(0xFF047857),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFF4338CA),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFF8FAF9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5F3),
    surfaceContainer = Color(0xFFECF0EE),
    surfaceContainerHigh = Color(0xFFE6EBE8),
    surfaceContainerHighest = Color(0xFFE0E6E3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FD3B8),
    onPrimary = Color(0xFF053824),
    primaryContainer = Brand,
    onPrimaryContainer = Color(0xFFD4E8DC),
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFA5B4FC),
    background = Color(0xFF101614),
    surface = Color(0xFF101614),
    surfaceContainerLowest = Color(0xFF0B100E),
    surfaceContainerLow = Color(0xFF181F1C),
    surfaceContainer = Color(0xFF1C2420),
    surfaceContainerHigh = Color(0xFF262E2A),
    surfaceContainerHighest = Color(0xFF313935),
)

@Composable
fun MergersTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** A status's fill and the text on it, as the site's solid badges (SOLID_STATUS_COLORS). */
data class StatusColors(val fill: Color, val onFill: Color)

private val emerald = StatusColors(Color(0xFF047857), Color.White)
private val red = StatusColors(Color(0xFFB91C1C), Color.White)

fun statusColors(label: String?): StatusColors = when (label) {
    Status.APPROVED, Status.NOT_OPPOSED, Status.APPROVED_WITH_CONDITIONS -> emerald
    Status.NOT_APPROVED, Status.DECLINED -> red
    Status.REFERRED_TO_PHASE_2 -> StatusColors(Color(0xFFFBBF24), Color(0xFF451A03))
    Status.UNDER_ASSESSMENT -> StatusColors(Brand, Color.White)
    Status.ASSESSMENT_SUSPENDED -> StatusColors(Color(0xFFC2410C), Color.White)
    Status.ASSESSMENT_CEASED -> StatusColors(Color(0xFF7E22CE), Color.White)
    else -> StatusColors(Color(0xFF4B5563), Color.White)
}

/** The wash a matter under appeal or judicial review takes, as on the site. */
val AppealIndigo = StatusColors(Color(0xFF4338CA), Color.White)
