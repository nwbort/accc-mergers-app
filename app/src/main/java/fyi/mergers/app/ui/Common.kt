package fyi.mergers.app.ui

import androidx.compose.ui.semantics.heading
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyListState
import fyi.mergers.app.ui.theme.isDarkTheme
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** Key prefix of a section's sticky heading, which [SectionHeadingHaptics] ticks for. */
const val SectionHeaderPrefix = "h-"

/**
 * A faint tick as a section heading sticks to the top of [state]'s list. Only
 * headings keyed with [SectionHeaderPrefix] count, so a dashboard's day headers
 * under a section stay quiet.
 */
@Composable
fun SectionHeadingHaptics(
    state: androidx.compose.foundation.lazy.LazyListState,
    isSection: (androidx.compose.foundation.lazy.LazyListItemInfo) -> Boolean = { (it.key as? String)?.startsWith(SectionHeaderPrefix) == true },
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state, haptics) {
        var last: Any? = null
        snapshotFlow {
            // Stuck: scrolled past its natural place (not merely sitting at the top).
            val first = state.firstVisibleItemIndex
            val scrolled = state.firstVisibleItemScrollOffset > 0
            state.layoutInfo.visibleItemsInfo
                .lastOrNull { isSection(it) && (it.index < first || (it.index == first && scrolled)) }
                ?.index
        }.collect { stuck ->
            if (stuck != null && stuck != last) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            last = stuck
        }
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
            // So TalkBack's headings navigation can jump section to section.
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * One merger in a list, the card tinted with its status colour (the site's
 * fill as a pale wash; in the dark, the lighter glyph shade), the outcome's
 * glyph (its [outcomeShape], colour and symbol) leading the outcome's words,
 * then the name. Flat: the colour sets it off, not a shadow. A press
 * squishes the card on a spring.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergerCard(
    m: MergerSummary,
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onSwipe: (() -> Unit)? = null,
) {
    if (onSwipe == null) {
        MergerCardBody(m, followed, onClick, modifier, onLongClick, Modifier)
        return
    }
    SwipeToFollow(followed, onSwipe, modifier, MaterialTheme.shapes.large) { actions ->
        MergerCardBody(m, followed, onClick, Modifier, onLongClick, actions)
    }
}

/** How far across a card a swipe must go to follow or unfollow, as a fraction of its width. */
internal const val SWIPE_THRESHOLD = 0.25f

/** A flick this fast (dp a second) towards a side follows or unfollows however short it was. */
private const val SWIPE_FLING_DP = 1000

/**
 * A card that follows or unfollows its merger when swiped either way past a
 * quarter of its width ([SWIPE_THRESHOLD]), or flicked, then springs back: it
 * isn't dismissed. Behind it as it moves, what the swipe will do, in the
 * card's [shape]; a screen reader gets the same as one of the card's actions.
 * [content] puts the modifier it's handed on its clickable node: an action
 * on this wrapper would sit on a node TalkBack never lands on.
 *
 * Not Material's SwipeToDismissBox: its threshold is only consulted while the
 * finger is still moving at release. Let go after a pause (the usual end of a
 * deliberate drag) and it settles on the nearer end, so in practice it took
 * half the card; and any drag over 125 dp/s counted as a flick.
 */
@Composable
fun SwipeToFollow(
    followed: Boolean,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable (actions: Modifier) -> Unit,
) {
    val act by rememberUpdatedState(onSwipe)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val back = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var width by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val drag = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat()) }
    val armed = width > 0 && abs(offset) >= width * SWIPE_THRESHOLD

    val actions = Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(if (followed) "Unfollow" else "Follow") { act(); true })
    }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .draggable(
                drag,
                Orientation.Horizontal,
                onDragStarted = { settling?.cancel() },
                onDragStopped = { velocity ->
                    val flung = abs(velocity) >= with(density) { SWIPE_FLING_DP.dp.toPx() } &&
                        sign(velocity) == sign(offset)
                    if (armed || flung) act()
                    settling = scope.launch {
                        animate(offset, 0f, initialVelocity = velocity, animationSpec = back) { v, _ -> offset = v }
                    }
                },
            ),
        // Stretched (a card beside a taller one, on a wide dashboard), the card stretches with it.
        propagateMinConstraints = true,
    ) {
        if (offset != 0f) SwipeBacking(fromStart = offset > 0, armed = armed, followed = followed, shape = shape)
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }, propagateMinConstraints = true) { content(actions) }
    }
}

/** What a swipe on a card will do, shown behind it as it moves: follow, or unfollow. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.SwipeBacking(fromStart: Boolean, armed: Boolean, followed: Boolean, shape: Shape) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    val grow by animateFloatAsState(
        if (armed) 1.25f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "swipeIcon",
    )
    val (fill, ink) = if (followed) {
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    // Only for the eye: a screen reader has the card's Follow or Unfollow action instead.
    Row(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics {}
            .clip(shape)
            .background(fill)
            .padding(horizontal = 24.dp),
        horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (followed) Icons.Outlined.BookmarkRemove else Icons.Filled.BookmarkAdd,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.graphicsLayer { scaleX = grow; scaleY = grow },
        )
        Text(
            if (followed) "Unfollow" else "Follow",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = ink,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MergerCardBody(
    m: MergerSummary,
    followed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    actions: Modifier,
) {
    val input = m.outcomeInput()
    val header = input.headerStatus()
    val key = header?.label ?: m.status
    val label = input.outcomeText() ?: m.status ?: "Unknown"
    val tint = if (isDarkTheme()) {
        statusGlyphColors(key).fill.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    } else {
        statusColors(key).fill.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
    }

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
        colors = CardDefaults.cardColors(containerColor = tint),
    ) {
        Box(
            actions.combinedClickable(
                interactionSource = presses,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (followed) "Stop following" else "Follow",
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                val flags = listOfNotNull(
                    ("Waiver" to WaiverAmber).takeIf { m.is_waiver },
                    ("Under appeal" to AppealIndigo).takeIf { m.under_appeal },
                    ("Re-filed" to RefiledAmber).takeIf { m.is_refiled },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusGlyph(key, conditions = label.contains("conditions", ignoreCase = true), size = 30.dp)
                    FlowRow(
                        Modifier.padding(start = 10.dp).weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLargeEmphasized)
                        // Flags ride on the status line, so a flagged card is no taller.
                        flags.forEach { (text, c) -> FlagPill(text, c) }
                    }
                    if (followed) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "Following",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
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

/** A merger's flag on its status line (waiver, under appeal, re-filed): a small pill in the site's solid colours. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlagPill(text: String, c: StatusColors) {
    Text(
        text,
        color = c.onFill,
        style = MaterialTheme.typography.labelSmallEmphasized,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(c.fill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
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
                        Text(stripMarkdown(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(stripMarkdown(para.replace("\n", " ")), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Drop markdown emphasis and turn [text](link) into its text. */
internal fun stripMarkdown(s: String): String =
    s.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("**", "").replace("__", "")

@Composable
fun Centered(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A tap on [tab] in the bottom bar while it's already showing scrolls [list]
 * back to the top, and brings back the top bar ([bar]) if it had slid away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopOnReselect(vm: AppViewModel, tab: Route, list: LazyListState, bar: TopAppBarState? = null) {
    LaunchedEffect(vm, tab, list) {
        vm.reselected.filter { it == tab }.collect {
            bar?.heightOffset = 0f
            list.animateScrollToItem(0)
        }
    }
}

/**
 * Follow or unfollow a merger from a list (a swipe on its card, or the
 * Mergers tab's hold menu): the toggle's haptic, the notification permission
 * on a first follow, and a snackbar on [snackbar] to undo it.
 */
@Composable
fun rememberMergerFollowToggle(vm: AppViewModel, snackbar: SnackbarHostState): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberFollowHaptics()
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return { id ->
        val now = vm.toggleFollow(id)
        haptic(now)
        if (now && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val undo = snackbar.showSnackbar(
                if (now) "Following: you'll be notified of updates" else "No longer following",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (undo == SnackbarResult.ActionPerformed) vm.toggleFollow(id)
        }
    }
}
