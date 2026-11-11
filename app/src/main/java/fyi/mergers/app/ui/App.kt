package fyi.mergers.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import fyi.mergers.app.tracking.TrackedEvents
import kotlinx.coroutines.flow.drop

private data class Tab(val route: Route, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Route.Dashboard, "Dashboard", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
    Tab(Route.Mergers, "Mergers", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Filled.List),
    Tab(Route.Following, "Following", Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks),
)

@Composable
fun MergersApp(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val unread = TrackedEvents.inbox(sync?.events.orEmpty(), tracking).count { !it.upcoming && it.key !in tracking.seen }
    val activity = LocalActivity.current

    // The tabs' pager lives here, beside the bar, so the bar can follow a swipe.
    val pager = rememberPagerState(initialPage = TABS.indexOfFirst { it.route == vm.tab }.coerceAtLeast(0)) { TABS.size }
    val shown = swipedTab(vm, pager)

    Scaffold(
        bottomBar = {
            // Expressive's short bar: a pill behind the chosen tab that springs across.
            ShortNavigationBar {
                TABS.forEach { tab ->
                    val selected = shown == tab.route
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(tab.route) },
                        label = { Text(tab.label) },
                        icon = {
                            val icon = if (selected) tab.selectedIcon else tab.icon
                            if (tab.route == Route.Following && unread > 0) {
                                BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else {
                                Icon(icon, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        // From the root of another tab, back goes to the dashboard first.
        BackHandler(enabled = vm.backStack.size == 1 && vm.tab != Route.Dashboard) { vm.back() }

        // The tabs share one root entry, side by side in a pager, so you can
        // swipe between them; a merger, an industry or search stacks on top of whichever is showing.
        // One shared-transition scope over every screen, so a merger's card in
        // a list can grow into the header of its page (Modifier.sharedMerger).
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavDisplay(
                    backStack = listOf<Route>(Route.Tabs) + vm.backStack.drop(1),
                    onBack = { if (!vm.back()) activity?.finish() },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    entryProvider = entryProvider {
                        entry<Route.Tabs> { Shared { TabPager(vm, pager) } }
                        entry<Route.Merger> { key -> Shared { MergerDetailScreen(vm, key.id) } }
                        entry<Route.Industries> { IndustriesScreen(vm) }
                        entry<Route.Industry> { key -> Shared { IndustryScreen(vm, key.code) } }
                        entry<Route.Search> { Shared { SearchScreen(vm) } }
                        entry<Route.Settings> { SettingsScreen(vm) }
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
 * The tab the bar shows as chosen. Mid-swipe it's where the swipe is heading
 * (the page past halfway while the finger is down, then the page the fling
 * will rest on), so the pill slides across with the swipe rather than
 * jumping once it has settled. The tab itself still changes on settling
 * ([TabPager]); a tap on the bar, which also scrolls the pager, isn't a swipe
 * and shows the tapped tab throughout.
 */
@Composable
private fun swipedTab(vm: AppViewModel, pager: PagerState): Route {
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var swiping by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) swiping = true }
    LaunchedEffect(pager.isScrollInProgress, dragged) {
        if (!pager.isScrollInProgress && !dragged) swiping = false
    }
    if (!swiping || vm.backStack.size != 1) return vm.tab
    return TABS[if (dragged) pager.currentPage else pager.targetPage].route
}

/** The three tabs, swiped between left and right, kept in step with the bottom bar. */
@Composable
private fun TabPager(vm: AppViewModel, pager: PagerState) {
    val index = TABS.indexOfFirst { it.route == vm.tab }.coerceAtLeast(0)

    // A tap on the bottom bar (or back to the dashboard) moves the pager...
    LaunchedEffect(index) {
        if (pager.currentPage != index) pager.animateScrollToPage(index)
    }
    // ...and a swipe that comes to rest on a tab selects it. Only a change of
    // page counts: coming back from a merger, the pager can be restored on the
    // tab you left while the bar has since moved to another.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { vm.selectTab(TABS[it].route) }
    }

    HorizontalPager(state = pager, key = { TABS[it].label }) { page ->
        when (TABS[page].route) {
            Route.Dashboard -> DashboardScreen(vm)
            Route.Mergers -> MergersScreen(vm)
            else -> FollowingScreen(vm)
        }
    }
}
