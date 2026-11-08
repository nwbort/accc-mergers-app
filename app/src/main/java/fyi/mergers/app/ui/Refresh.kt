package fyi.mergers.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** How long a pull keeps the spinner up, however quickly the data comes back. */
internal const val MIN_SPIN_MS = 700L

/**
 * [PullToRefreshBox] that answers a pull. Most refreshes are one 304, so
 * [refreshing] can go true and false again before the indicator settles (or
 * before it is set at all), and the circle would snap away the moment it is let
 * go. Here a pull holds the spinner from release for [MIN_SPIN_MS], then for as
 * long as [refreshing] stays true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var pulled by remember { mutableStateOf(false) }
    val loading by rememberUpdatedState(refreshing)
    LaunchedEffect(pulled) {
        if (pulled) {
            delay(MIN_SPIN_MS)
            snapshotFlow { loading }.first { !it }
            pulled = false
        }
    }
    PullToRefreshBox(
        isRefreshing = pulled || refreshing,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        content = content,
    )
}
