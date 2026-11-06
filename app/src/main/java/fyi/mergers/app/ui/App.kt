package fyi.mergers.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

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

        NavDisplay(
            backStack = vm.backStack,
            onBack = { if (!vm.back()) activity?.finish() },
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            entryProvider = entryProvider {
                entry<Route.Dashboard> { DashboardScreen(vm) }
                entry<Route.Mergers> { MergersScreen(vm) }
                entry<Route.Following> { FollowingScreen(vm) }
                entry<Route.Merger> { key -> MergerDetailScreen(vm, key.id) }
            },
        )
    }
}
