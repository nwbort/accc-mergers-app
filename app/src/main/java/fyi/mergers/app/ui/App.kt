package fyi.mergers.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
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

@Composable
fun MergersApp(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val unread = TrackedEvents.inbox(sync?.events.orEmpty(), tracking).count { !it.upcoming && it.key !in tracking.seen }
    val activity = LocalActivity.current

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

        // The tabs share one root entry; a merger, an industry or search
        // stacks on top of whichever is showing.
        // One shared-transition scope over every screen, so a merger's card in
        // a list can grow into the header of its page (Modifier.sharedMerger).
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavDisplay(
                    backStack = listOf<Route>(Route.Tabs) + vm.backStack.drop(1),
                    onBack = { if (!vm.back()) activity?.finish() },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    entryProvider = entryProvider {
                        entry<Route.Tabs> { Shared { Tabs(vm) } }
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
 * The three tabs, one at a time, changed only from the bottom bar: Material
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
