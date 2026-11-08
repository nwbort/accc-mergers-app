package fyi.mergers.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
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
    val unread = sync?.events?.count { !it.upcoming && it.key !in tracking.seen } ?: 0
    val activity = LocalActivity.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    val selected = vm.tab == tab.route
                    NavigationBarItem(
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
        NavDisplay(
            backStack = listOf<Route>(Route.Tabs) + vm.backStack.drop(1),
            onBack = { if (!vm.back()) activity?.finish() },
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            entryProvider = entryProvider {
                entry<Route.Tabs> { TabPager(vm) }
                entry<Route.Merger> { key -> MergerDetailScreen(vm, key.id) }
                entry<Route.Industries> { IndustriesScreen(vm) }
                entry<Route.Industry> { key -> IndustryScreen(vm, key.code) }
                entry<Route.Search> { SearchScreen(vm) }
            },
        )
    }
}

/** The three tabs, swiped between left and right, kept in step with the bottom bar. */
@Composable
private fun TabPager(vm: AppViewModel) {
    val index = TABS.indexOfFirst { it.route == vm.tab }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index) { TABS.size }

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
