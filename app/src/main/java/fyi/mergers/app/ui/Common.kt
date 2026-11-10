package fyi.mergers.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.headerStatus
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.outcomeText
import fyi.mergers.app.ui.theme.AppealIndigo
import fyi.mergers.app.ui.theme.RefiledAmber
import fyi.mergers.app.ui.theme.StatusColors
import fyi.mergers.app.ui.theme.WaiverAmber
import fyi.mergers.app.ui.theme.statusColors
import fyi.mergers.app.ui.theme.statusGlyphColors

/**
 * Open a link in a Custom Tab (documents, the site, the ACCC's page), or
 * failing that in the browser. Always in a browser, never back in this app:
 * the app is the phone's handler for mergers.fyi links, so a "view on the
 * site" left to the system would land right back here.
 */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    val browser = browserPackage(context)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build()
            .apply { browser?.let { intent.setPackage(it) } }
            .launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        openInBrowser(context, url)
    }
}

/** Hand [url] to the browser in its own task. False when there's no browser to take it. */
fun openInBrowser(context: Context, url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    browserPackage(context)?.let { view.setPackage(it) }
    return runCatching { context.startActivity(view) }.isSuccess
}

/**
 * The browser to send links to: the default one if it's been chosen, else
 * the first installed, and never this app. Asked about a page on another
 * site, so the answer is the browser and not whoever handles mergers.fyi.
 */
private fun browserPackage(context: Context): String? {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
    val ours = context.packageName
    // "android" is the chooser, which is what resolves when no default is set.
    pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        ?.takeIf { it != "android" && it != ours }
        ?.let { return it }
    return pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName }
        .firstOrNull { it != ours }
}

fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The scope a merger's card and its page header share their bounds in (MergersApp). */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of the screen being drawn, when it takes part in a shared transition. */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A merger's card and its page's header are one thing moving: opening a
 * merger grows the card into the header, and back shrinks it again. Outside a
 * shared transition (a widget's settings, say) it does nothing.
 */
@Composable
fun Modifier.sharedMerger(id: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val clip = MaterialTheme.shapes.large
    return with(shared) {
        this@sharedMerger.sharedBounds(
            rememberSharedContentState("merger-$id"),
            visibility,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(clip),
        )
    }
}

/** A tick under the thumb as a follow goes on or off: the toggle's own haptic. */
@Composable
fun rememberFollowHaptics(): (Boolean) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { on -> haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }
    }
}

/** The site's solid status badge: an uppercase label on the outcome's colour. */
@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier, colorKey: String = label) =
    SolidBadge(label, statusColors(colorKey), "Status: $label", modifier)

/**
 * The solid badge shape every chip on a merger card shares, so a waiver or
 * appeal reads at the same size and weight as the status beside it.
 */
@Composable
fun SolidBadge(label: String, c: StatusColors, description: String = label, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        color = c.onFill,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.fill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { role = Role.Image; contentDescription = description },
    )
}

/**
 * Each outcome's own shape from the Material 3 Expressive set, so an outcome
 * reads by its outline as well as its colour and glyph: a sunny seal for
 * cleared, a hard-edged cookie for blocked, a many-sided clock face while it
 * runs, a square for paused, a gem for ceased.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun outcomeShape(label: String?): RoundedPolygon = when (label) {
    Status.APPROVED, Status.NOT_OPPOSED, Status.APPROVED_WITH_CONDITIONS -> MaterialShapes.Sunny
    Status.NOT_APPROVED, Status.DECLINED -> MaterialShapes.Cookie4Sided
    Status.UNDER_ASSESSMENT, Status.REFERRED_TO_PHASE_2 -> MaterialShapes.Cookie12Sided
    Status.ASSESSMENT_SUSPENDED -> MaterialShapes.Square
    Status.ASSESSMENT_CEASED -> MaterialShapes.Gem
    else -> MaterialShapes.Circle
}

/**
 * An outcome as a symbol alone: the status colour in the outcome's shape
 * ([outcomeShape]) with the site's glyph for it (constants/outcomeIcons.js,
 * and StatusBadge's live glyphs). The words go to the accessible name, so
 * colour isn't the only signal.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusGlyph(label: String?, modifier: Modifier = Modifier, conditions: Boolean = false, size: Dp = 32.dp) {
    val c = statusGlyphColors(label)
    val icon = when (label) {
        Status.APPROVED, Status.NOT_OPPOSED, Status.APPROVED_WITH_CONDITIONS -> Icons.Filled.Check
        Status.NOT_APPROVED, Status.DECLINED -> Icons.Filled.Close
        Status.ASSESSMENT_CEASED -> Icons.Filled.Block
        Status.UNDER_ASSESSMENT, Status.REFERRED_TO_PHASE_2 -> Icons.Outlined.Schedule
        Status.ASSESSMENT_SUSPENDED -> Icons.Filled.Pause
        else -> null
    }
    val words = listOfNotNull(
        label ?: "Unknown",
        "with conditions".takeIf { conditions && label == Status.APPROVED },
    ).joinToString(" ")
    Box(
        modifier
            .size(size)
            .clip(outcomeShape(label).toShape())
            .background(c.fill)
            .semantics { role = Role.Image; contentDescription = words },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.onFill, modifier = Modifier.size(size * 0.55f))
        } else {
            Box(Modifier.size(size / 4).clip(CircleShape).background(c.onFill))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * One merger in a list: the outcome's glyph (its [outcomeShape], colour and
 * symbol) leading the outcome's words, then the name. A press squishes the
 * card on a spring.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergerCard(
    m: MergerSummary,
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val input = m.outcomeInput()
    val header = input.headerStatus()
    val key = header?.label ?: m.status
    val label = input.outcomeText() ?: m.status ?: "Unknown"

    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val squish by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "squish",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .sharedMerger(m.merger_id)
            .graphicsLayer { scaleX = squish; scaleY = squish },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            Modifier.combinedClickable(
                interactionSource = presses,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (followed) "Stop following" else "Follow",
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusGlyph(key, conditions = label.contains("conditions", ignoreCase = true), size = 30.dp)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    if (followed) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "Following",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                val flags = listOfNotNull(
                    ("Waiver" to WaiverAmber).takeIf { m.is_waiver },
                    ("Under appeal" to AppealIndigo).takeIf { m.under_appeal },
                    ("Re-filed" to RefiledAmber).takeIf { m.is_refiled },
                )
                if (flags.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        flags.forEach { (text, c) -> FlagPill(text, c) }
                    }
                }
                Text(
                    m.merger_name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val dates = listOfNotNull(
                    m.merger_id,
                    Dates.format(m.effective_notification_datetime)?.let { "Notified $it" },
                    Dates.format(m.determination_publication_date)?.let { "Determined $it" },
                ).joinToString(" · ")
                Text(
                    dates,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A merger's flag beside its outcome (waiver, under appeal, re-filed): a small pill in the site's solid colours. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlagPill(text: String, c: StatusColors) {
    Text(
        text,
        color = c.onFill,
        style = MaterialTheme.typography.labelMediumEmphasized,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(c.fill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A short list row: a title, a line under it and a trailing date. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    trailing: String?,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    emphasis: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The register's prose: paragraphs split on blank lines, "* " and "- " lines
 * as bullets. Enough for merger descriptions and commentary, which are
 * markdown at their richest.
 */
@Composable
fun Prose(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.trim().split(Regex("\n\\s*\n")).forEach { para ->
            val lines = para.lines()
            if (lines.all { it.trimStart().startsWith("* ") || it.trimStart().startsWith("- ") || it.isBlank() }) {
                lines.filter { it.isNotBlank() }.forEach { line ->
                    Row {
                        Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(clean(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(clean(para.replace("\n", " ")), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Drop markdown emphasis and turn [text](link) into its text. */
private fun clean(s: String): String =
    s.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("**", "").replace("__", "")

@Composable
fun Centered(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
