package fyi.mergers.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fyi.mergers.app.data.IndustryPhase
import fyi.mergers.app.data.RecentEventType
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

/** Whether the app is drawing dark: the setting, or the phone's when it's [ThemeChoice.SYSTEM]. */
private val LocalDark = staticCompositionLocalOf { false }

/** Use this, not [isSystemInDarkTheme], for colours that differ between light and dark. */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = LocalDark.current

/**
 * Material 3 Expressive's rounder corners: cards (medium) at 20, sheets and
 * hero blocks (extraLarge) at 32.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Material 3 Expressive: springy motion (MotionScheme.expressive, which every
 * M3 component and the app's own [androidx.compose.material3.MaterialTheme.motionScheme]
 * animations follow), [AppShapes], and the emphasized type styles alongside the usual ones.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergersTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) Dark else Light,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            content = content,
        )
    }
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

/** The site's solid WaiverBadge and RefiledBadge. */
val WaiverAmber = StatusColors(Color(0xFFFBBF24), Color(0xFF451A03))
val RefiledAmber = StatusColors(Color(0xFFB45309), Color.White)

/**
 * An upcoming event's tile: each kind of deadline has its own accent, as the
 * site's Coming up list (EVENT_TYPES in UpcomingEventsTimeline.jsx). Tailwind's
 * 50 tint under its 600/700 shade; in the dark, the 300 shade on a faint wash.
 */
@Composable
fun eventTypeColors(type: String?): StatusColors = when (type) {
    "consultation_due" -> tile(Tint.BLUE)
    "notice_of_competition_concerns" -> tile(Tint.AMBER)
    "public_benefit_assessment", "public_benefit_response_due" -> tile(Tint.TEAL)
    "determination_due" -> tile(Tint.PURPLE)
    "tribunal_hearing", "federal_court_hearing" -> tile(Tint.ROSE)
    else -> tile(Tint.GRAY)
}

/**
 * A recent event's tile, as the site's Recent events list (`tile` in
 * constants/recentEventTypes.js), in the same families as [eventTypeColors].
 */
@Composable
fun recentEventColors(type: RecentEventType): StatusColors = tile(
    when (type) {
        RecentEventType.TRIBUNAL, RecentEventType.FEDERAL_COURT -> Tint.ROSE
        RecentEventType.PHASE_2_DETERMINATION, RecentEventType.REASONS,
        RecentEventType.PHASE_1_DETERMINATION -> Tint.PURPLE
        RecentEventType.UNDERTAKING_ACCEPTED, RecentEventType.REMEDY_OFFER,
        RecentEventType.REMEDY_QUESTIONNAIRE -> Tint.EMERALD
        RecentEventType.PHASE_2_REFERRAL, RecentEventType.COMPETITION_CONCERNS -> Tint.AMBER
        RecentEventType.PUBLIC_BENEFIT -> Tint.TEAL
        RecentEventType.PHASE_2_CONSULTATION, RecentEventType.QUESTIONNAIRE -> Tint.BLUE
        RecentEventType.MATERIALLY_INCOMPLETE -> Tint.ORANGE
        RecentEventType.NOTIFICATION_COMPLETED, RecentEventType.NOTIFIED -> Tint.PRIMARY
        RecentEventType.TIMELINE_EXTENSION -> Tint.SKY
        RecentEventType.CEASED, RecentEventType.WAIVER_DETERMINATION, RecentEventType.OTHER -> Tint.GRAY
    },
)

/** Tailwind's 50 tint under its 600/700 shade; in the dark, the 300 shade on a faint wash. */
private enum class Tint(val tint: Long, val ink: Long, val darkInk: Long) {
    BLUE(0xFFEFF6FF, 0xFF2563EB, 0xFF93C5FD),
    SKY(0xFFF0F9FF, 0xFF0369A1, 0xFF7DD3FC),
    AMBER(0xFFFFFBEB, 0xFFB45309, 0xFFFCD34D),
    ORANGE(0xFFFFF7ED, 0xFFC2410C, 0xFFFDBA74),
    TEAL(0xFFF0FDFA, 0xFF0F766E, 0xFF5EEAD4),
    EMERALD(0xFFECFDF5, 0xFF047857, 0xFF6EE7B7),
    PURPLE(0xFFFAF5FF, 0xFF9333EA, 0xFFD8B4FE),
    ROSE(0xFFFFF1F2, 0xFFE11D48, 0xFFFDA4AF),
    PRIMARY(0xFFEBEEED, 0xFF335145, 0xFF9FD3B8), // the site's primary/10 on white
    GRAY(0xFFF3F4F6, 0xFF4B5563, 0xFFD1D5DB),
}

@Composable
private fun tile(t: Tint): StatusColors =
    if (isDarkTheme()) StatusColors(Color(t.darkInk).copy(alpha = 0.16f), Color(t.darkInk))
    else StatusColors(Color(t.tint), Color(t.ink))

/** The industry pages' group accents: the site's phase-2, phase-1 and waiver fills. */
fun phaseAccent(phase: IndustryPhase): Color = when (phase) {
    IndustryPhase.PHASE_2 -> Color(0xFF52489C)
    IndustryPhase.PHASE_1 -> Color(0xFFB8935C)
    IndustryPhase.WAIVER -> Color(0xFFFBBF24)
}
