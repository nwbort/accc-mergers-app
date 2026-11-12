package fyi.mergers.app.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import fyi.mergers.app.tracking.TrackedEvents

private data class Tab(val route: Route, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Route.Dashboard, "Dashboard", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
    Tab(Route.Mergers, "Mergers", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Filled.List),
    Tab(Route.Following, "Following", Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks),
)

/**
 * Material's adaptive navigation: the short bar along the foot on a phone; on
 * anything wider (a foldable opened, a tablet, a desktop window, or a phone on
 * its side) the rail down the side, collapsed to icons until there's room for
 * it to open out with its labels. From Expanded up, a merger opens beside the
 * list it came from ([ListDetailStrategy]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergersApp(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val unread = TrackedEvents.inbox(sync?.events.orEmpty(), tracking).count { !it.upcoming && it.key !in tracking.seen }
    val width = windowWidthClass()
    val rail = width != WidthClass.Compact
    val twoPane = width >= WidthClass.Expanded
    SideEffect { vm.listDetail = twoPane }

    if (!rail) {
        Scaffold(
            bottomBar = {
                // Expressive's short bar: a pill behind the chosen tab that springs across.
                ShortNavigationBar {
                    TABS.forEach { tab ->
                        val selected = vm.tab == tab.route
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { vm.tapTab(tab.route) },
                            label = { Text(tab.label) },
                            icon = { TabIcon(tab, selected, unread) },
                        )
                    }
                }
            },
        ) { padding ->
            // The bar's height is spoken for: a screen's imePadding() below takes
            // only what the keyboard covers beyond it.
            Screens(
                vm, twoPane = false,
                Modifier
                    .padding(bottom = padding.calculateBottomPadding())
                    .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding())),
            )
        }
        return
    }

    // Open (labels beside the icons) by default only where there's room to
    // spare; the menu button at its head opens and closes it, and that sticks.
    var railOpen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val open = railOpen ?: (width == WidthClass.Large)
    val railState = rememberWideNavigationRailState(if (open) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    LaunchedEffect(open) { if (open) railState.expand() else railState.collapse() }
    val expanded = railState.targetValue == WideNavigationRailValue.Expanded

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WideNavigationRail(
            state = railState,
            modifier = Modifier.testTag("rail"),
            header = {
                Column(Modifier.padding(start = 4.dp)) {
                    // On Medium the rail stays collapsed: an open one would
                    // leave too little for the screen beside it.
                    if (width >= WidthClass.Expanded) {
                        IconButton(onClick = { railOpen = !open }, Modifier.padding(start = 16.dp)) {
                            Icon(
                                if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                contentDescription = if (expanded) "Collapse navigation" else "Expand navigation",
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    // The rail's FAB: search, the dashboard's top action, from every screen.
                    ExtendedFloatingActionButton(
                        onClick = vm::openSearch,
                        expanded = expanded,
                        // Named by its icon whether or not the label shows, so a screen
                        // reader has it either way, and doesn't read it twice.
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        text = { Text("Search", Modifier.clearAndSetSemantics {}) },
                        modifier = Modifier.padding(start = 16.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            },
        ) {
            TABS.forEach { tab ->
                val selected = vm.tab == tab.route
                WideNavigationRailItem(
                    selected = selected,
                    onClick = { vm.tapTab(tab.route) },
                    icon = { TabIcon(tab, selected, unread) },
                    label = { Text(tab.label) },
                    railExpanded = expanded,
                )
            }
        }
        // Screens draw their own top and bottom insets; the side the rail
        // covers is the rail's.
        Screens(
            vm, twoPane = twoPane,
            Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            listWidth = if (width == WidthClass.Large) 412.dp else 360.dp,
        )
    }
}

@Composable
private fun TabIcon(tab: Tab, selected: Boolean, unread: Int) {
    val icon = if (selected) tab.selectedIcon else tab.icon
    if (tab.route == Route.Following && unread > 0) {
        BadgedBox(
            badge = {
                // Read as "Following, 3 new", not a bare number.
                Badge(Modifier.clearAndSetSemantics { contentDescription = "$unread new" }) {
                    Text(if (unread > 99) "99+" else "$unread")
                }
            },
        ) {
            Icon(icon, contentDescription = null)
        }
    } else {
        Icon(icon, contentDescription = null)
    }
}

/**
 * Everything but the bar or rail: the tabs at the root, and a merger, an
 * industry or search stacked on top of whichever is showing.
 */
@Composable
private fun Screens(vm: AppViewModel, twoPane: Boolean, modifier: Modifier, listWidth: Dp = 360.dp) {
    val activity = LocalActivity.current
    // From the root of another tab, back goes to the dashboard first.
    BackHandler(enabled = vm.backStack.size == 1 && vm.tab != Route.Dashboard) { vm.back() }

    // Beside nothing yet, only the lists you pick a merger from keep the
    // empty detail pane: the dashboard and the digest have the window.
    val tab = vm.tab
    val listDetail = remember(twoPane, listWidth, tab) {
        ListDetailStrategy(twoPane, listWidth) { e ->
            when (ListDetailStrategy.listName(e)) {
                "tabs" -> tab == Route.Mergers || tab == Route.Following
                "digest" -> false
                else -> true
            }
        }
    }

    // One shared-transition scope over every screen, so a merger's card in
    // a list can grow into the header of its page (Modifier.sharedMerger).
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Pane {
                NavDisplay(
                    backStack = listOf<Route>(Route.Tabs) + vm.backStack.drop(1),
                    onBack = { if (!vm.back()) activity?.finish() },
                    sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
                    entryProvider = entryProvider {
                        entry<Route.Tabs>(metadata = ListDetailStrategy.list("tabs")) { Shared { Tabs(vm) } }
                        entry<Route.Merger>(metadata = ListDetailStrategy.detail()) { key -> Shared { MergerDetailScreen(vm, key.id) } }
                        entry<Route.Industries> { IndustriesScreen(vm) }
                        entry<Route.Industry>(metadata = ListDetailStrategy.list("industry")) { key -> Shared { IndustryScreen(vm, key.code) } }
                        entry<Route.Search>(metadata = ListDetailStrategy.list("search")) { Shared { SearchScreen(vm) } }
                        entry<Route.Settings> { SettingsScreen(vm) }
                        entry<Route.Digest>(metadata = ListDetailStrategy.list("digest")) { DigestScreen(vm) }
                    },
                )
            }
        }
    }
}

/** Hands an entry's own enter/exit animation to [Modifier.sharedMerger] inside it. */
@Composable
private fun Shared(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current, content = content)
}

/**
 * The three tabs, one at a time, changed only from the bar or the rail: Material
 * reserves swiping for things within a screen, not for moving between
 * destinations. Each keeps its own scroll and state while another shows, and
 * they change with a fade through.
 */
@Composable
private fun Tabs(vm: AppViewModel) {
    val saved = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = vm.tab,
        transitionSpec = {
            fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90))
        },
        label = "tabs",
    ) { tab ->
        saved.SaveableStateProvider(tab.toString()) {
            when (tab) {
                Route.Mergers -> MergersScreen(vm)
                Route.Following -> FollowingScreen(vm)
                else -> DashboardScreen(vm)
            }
        }
    }
}
