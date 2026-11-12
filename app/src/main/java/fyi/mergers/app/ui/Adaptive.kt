package fyi.mergers.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Material's window size classes, by width: a phone held upright is Compact,
 * an unfolded foldable or a small tablet upright is Medium, a tablet held
 * sideways is Expanded, and a desktop window or a large tablet is Large.
 */
enum class WidthClass { Compact, Medium, Expanded, Large }

fun widthClassOf(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    width < 1200.dp -> WidthClass.Expanded
    else -> WidthClass.Large
}

/** The window's width class: what decides between the bar and the rail, and one pane or two. */
@Composable
fun windowWidthClass(): WidthClass {
    val px = LocalWindowInfo.current.containerSize.width
    return widthClassOf(with(LocalDensity.current) { px.toDp() })
}

/** How wide the pane a screen is drawn in is: the window, less the rail, or one side of two panes. */
val LocalPaneWidth = staticCompositionLocalOf { Dp.Unspecified }

/**
 * How wide a screen's content may grow. Past it, lines of text and rows of
 * cards get too long to read across, so the rest is left as margin either side
 * (Material's guidance on large screens) rather than stretching them.
 */
val ReadableWidth = 840.dp

/** Hands [content] the width it's drawn at, for [readableInset] below. */
@Composable
fun Pane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalPaneWidth provides maxWidth, content = content)
    }
}

/** The margin either side that keeps this pane's content to [ReadableWidth]: none on a phone. */
@Composable
fun readableInset(): Dp {
    val width = LocalPaneWidth.current
    if (width == Dp.Unspecified) return 0.dp
    return max(0.dp, (width - ReadableWidth) / 2)
}

/**
 * A list's content padding with [readableInset] added to each side. As
 * padding rather than a narrower list, the margins still scroll it (a mouse
 * wheel or a trackpad over them, on a tablet or a desktop).
 */
@Composable
fun PaddingValues.readable(): PaddingValues {
    val inset = readableInset()
    if (inset == 0.dp) return this
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir) + inset,
        end = calculateEndPadding(dir) + inset,
        top = calculateTopPadding(),
        bottom = calculateBottomPadding(),
    )
}

/** [Modifier.padding] by [readableInset], for what sits outside a list (a search field, a row of filters). */
@Composable
fun Modifier.readableWidth(): Modifier {
    val inset = readableInset()
    return if (inset == 0.dp) this else padding(horizontal = inset)
}

// ---- list-detail -------------------------------------------------------------

/**
 * Material's list-detail layout, for windows Expanded and wider: a merger opens
 * beside the list it was picked from (a tab, an industry, search, the digest)
 * rather than over it, so you can go down a list without losing your place.
 * Mergers opened from that merger replace it in the detail pane, and back
 * goes through them as it would on a phone.
 *
 * Until a merger is picked, the Mergers and Following tabs (and the other
 * lists) keep the detail pane with a word on what goes there, so the list
 * doesn't jump from full width to a column the moment you tap something. The
 * dashboard isn't a list to pick from, so it has the whole window until it is.
 */
class ListDetailStrategy(
    private val enabled: Boolean,
    private val listWidth: Dp,
    /** Whether a list that has nothing picked yet keeps the empty detail pane beside it. */
    private val placeholderBeside: (NavEntry<Route>) -> Boolean,
) : SceneStrategy<Route> {
    override fun SceneStrategyScope<Route>.calculateScene(entries: List<NavEntry<Route>>): Scene<Route>? {
        if (!enabled || entries.isEmpty()) return null
        val top = entries.last()
        val detail = top.takeIf { it.metadata[PANE] == DETAIL }
        // The list is the nearest screen under the merger(s) that isn't one.
        val list = entries.lastOrNull { it.metadata[PANE] != DETAIL } ?: return null
        if (list.metadata[PANE] != LIST) return null
        if (detail == null && !placeholderBeside(list)) return null
        return ListDetailScene(
            list = list,
            detail = detail,
            entries = listOfNotNull(list, detail),
            previousEntries = entries.dropLast(1),
            listWidth = listWidth,
        )
    }

    companion object {
        private const val PANE = "fyi.mergers.pane"
        private const val LIST = "list"
        private const val DETAIL = "detail"

        private const val NAME = "fyi.mergers.list"

        /** An entry's metadata: a screen a merger can be picked from, which takes the list pane. */
        fun list(name: String): Map<String, Any> = mapOf(PANE to LIST, NAME to name)

        /** The name [list] gave an entry. */
        fun listName(entry: NavEntry<Route>): String? = entry.metadata[NAME] as? String

        /** An entry's metadata: a merger, which takes the detail pane. */
        fun detail(): Map<String, Any> = mapOf(PANE to DETAIL)
    }
}

private class ListDetailScene(
    private val list: NavEntry<Route>,
    private val detail: NavEntry<Route>?,
    override val entries: List<NavEntry<Route>>,
    override val previousEntries: List<NavEntry<Route>>,
    private val listWidth: Dp,
) : Scene<Route> {
    // Keyed on the list alone: picking another merger, or going back to the
    // one before, changes the detail pane only (below), not the whole scene.
    override val key: Any = "list-detail" to list.contentKey

    override val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            // A card can't grow into a header beside it, both on screen at
            // once: the detail pane crossfades instead.
            CompositionLocalProvider(LocalSharedTransitionScope provides null) {
                Pane(Modifier.width(listWidth).fillMaxHeight()) { list.Content() }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = detail,
                        contentKey = { it?.contentKey },
                        transitionSpec = { fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                        label = "detail",
                    ) { shown ->
                        Pane(Modifier.fillMaxSize()) {
                            if (shown == null) NothingPicked() else shown.Content()
                        }
                    }
                }
            }
        }
    }
}

/** The empty detail pane: what will show there. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NothingPicked() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(Modifier.fillMaxSize(), shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.secondaryContainer) {}
            Icon(Icons.Outlined.Description, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text("Pick a merger", style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Its timeline, documents and determination open here, beside the list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
