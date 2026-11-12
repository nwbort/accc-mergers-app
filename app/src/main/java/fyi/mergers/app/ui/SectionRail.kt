package fyi.mergers.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A section on a [SectionRail]: where its heading is in the list, and how it looks. */
class RailMark(
    val index: Int,
    val label: String,
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val ink: Color,
    val onInk: Color,
    val wash: Color,
)

private val TRACK_INSET = 20.dp // above and below the track
private val GRIP = 28.dp // how far in from the end edge a drag takes the rail

/**
 * A list with a scrubber down its end edge: a mark for each section (not
 * each card), in its shape and colour, spaced as the sections are long, and
 * the one showing stands out. A drag that starts at the edge and goes up or
 * down takes the list with the finger: a light tick for each card passed, a
 * firmer tap for each section crossed, and the section's name beside the
 * finger. Faint at rest; it comes up while the list moves.
 *
 * The rail watches the edge as the list's parent, ahead of the list, rather
 * than as a strip over it: a sideways swipe from a card's edge (to follow or
 * unfollow) still reaches the card, and only a drag that turns out vertical
 * is the rail's. The marks run from the first heading to [end] (exclusive).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionRail(
    listState: LazyListState,
    marks: List<RailMark>,
    end: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentMarks by rememberUpdatedState(marks)
    val start = marks.firstOrNull()?.index ?: 0
    val span = (end - start).coerceAtLeast(1)

    var trackHeight by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableIntStateOf(-1) }

    fun markOf(index: Int): RailMark? = currentMarks.lastOrNull { it.index <= index }
    val showing by remember(marks) {
        derivedStateOf { markOf(if (dragging) target else listState.firstVisibleItemIndex) }
    }

    Box(
        modifier.pointerInput(start, span, marks.isEmpty()) {
            if (marks.isEmpty()) return@pointerInput
            val inset = TRACK_INSET.toPx()
            val grip = GRIP.toPx()
            fun follow(y: Float) {
                fingerY = (y - inset).coerceIn(0f, trackHeight.toFloat())
                val f = if (trackHeight == 0) 0f else fingerY / trackHeight
                val next = (start + (f * span).toInt()).coerceIn(start, end - 1)
                if (next == target) return
                val crossed = markOf(next) != markOf(target)
                target = next
                haptics.performHapticFeedback(
                    if (crossed) HapticFeedbackType.GestureThresholdActivate
                    else HapticFeedbackType.SegmentFrequentTick,
                )
                scope.launch { listState.scrollToItem(next) }
            }
            awaitEachGesture {
                // Ahead of the list (the Initial pass), so a drag taken here doesn't scroll it too.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x < size.width - grip) return@awaitEachGesture
                if (down.position.y < inset || down.position.y > size.height - inset) return@awaitEachGesture
                var moved = Offset.Zero
                var change = down
                while (true) {
                    change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                    moved += change.positionChange()
                    if (moved.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(moved.y) <= abs(moved.x)) return@awaitEachGesture // a swipe: the card's
                        break
                    }
                }
                change.consume()
                dragging = true
                target = -1
                try {
                    follow(change.position.y)
                    while (true) {
                        change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        follow(change.position.y)
                    }
                } finally {
                    dragging = false
                }
            }
        },
    ) {
        content()
        if (marks.isEmpty()) return@Box

        // Up while the list moves or the rail is held; settles back a moment after.
        var awake by remember { mutableStateOf(false) }
        val moving = listState.isScrollInProgress || dragging
        LaunchedEffect(moving) {
            if (moving) awake = true else { delay(1200); awake = false }
        }
        val alpha by animateFloatAsState(
            if (awake) 1f else 0.45f,
            MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "rail",
        )

        // Drawn only: the touches are the parent's, above.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(vertical = TRACK_INSET)
                .fillMaxHeight()
                .width(16.dp)
                .onSizeChanged { trackHeight = it.height }
                .testTag("section-rail"),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .width(2.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            marks.forEach { m ->
                val on = m === showing
                val size by animateDpAsState(
                    if (on) 14.dp else 9.dp,
                    MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "mark",
                )
                val turn by animateFloatAsState(
                    if (on) 0f else -45f,
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "markTurn",
                )
                val f = (m.index - start).toFloat() / span
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, (f * trackHeight - size.toPx() / 2).roundToInt()) }
                        .size(size)
                        .graphicsLayer {
                            this.alpha = if (on) 1f else alpha
                            rotationZ = turn
                        }
                        .clip(m.shape.toShape())
                        .background(m.ink),
                )
            }
        }

        // While held: the section under the finger, beside it.
        val shown by animateFloatAsState(
            if (dragging) 1f else 0f,
            MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "bubble",
        )
        val m = showing
        if (shown > 0.01f && m != null) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = m.wash,
                contentColor = m.ink,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(-GRIP.roundToPx(), (TRACK_INSET.toPx() + fingerY - 20.dp.toPx()).roundToInt())
                    }
                    .graphicsLayer {
                        this.alpha = shown.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * shown
                        scaleY = 0.6f + 0.4f * shown
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .testTag("section-rail-label"),
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(24.dp).clip(m.shape.toShape()).background(m.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(m.icon, contentDescription = null, tint = m.onInk, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        m.label,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
