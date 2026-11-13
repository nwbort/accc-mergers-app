package fyi.mergers.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.Dates
import fyi.mergers.app.notify.Notifier
import fyi.mergers.app.tracking.TrackedEvent
import fyi.mergers.app.tracking.TrackedEvents

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FollowingScreen(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val listState by vm.list.collectAsStateWithLifecycle()

    val events = TrackedEvents.inbox(sync?.events.orEmpty(), tracking)
    val unread = events.filter { !it.upcoming && it.key !in tracking.seen }
    val earlier = events.filter { !it.upcoming && it.key in tracking.seen }.take(30)
    val upcoming = events.filter { it.upcoming }.sortedBy { it.date }
    val summaries = listState.value.orEmpty().associateBy { it.merger_id }

    // Listed here, so the background check needn't announce them as well.
    LaunchedEffect(unread.map { it.key }) {
        val notShown = unread.filter { it.key !in tracking.notified }.map { it.key }
        if (notShown.isNotEmpty()) vm.markShown(notShown)
    }

    // Like the dashboard's, the bar slides away as you scroll down and comes
    // back as soon as you scroll up.
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val toggleFollow = rememberMergerFollowToggle(vm, snackbar)
    ScrollToTopOnReselect(vm, Route.Following, list, bar.state)
    // Item positions of the section headings, refilled as the list is built.
    val sections = remember { mutableSetOf<Int>() }
    SectionHeadingHaptics(list) { it.index in sections }
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = bar,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = { Text("Following") },
                actions = {
                    if (unread.isNotEmpty()) TextButton(onClick = vm::markAllRead) { Text("Mark all read") }
                    // Settings (where the alert switches live), with the dashboard's cog.
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = vm.syncing,
            onRefresh = vm::pullFollowing,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("following"), state = list, contentPadding = PaddingValues(bottom = 24.dp).readable()) {
                sections.clear()
                var count = 0
                fun section() { sections.add(count); count++ }
                item { NotificationsBanner() }
                count++

                if (tracking.followsNothing) {
                    count++
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("You're not following anything yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Follow a merger to be notified when the ACCC publishes a document, " +
                                    "a determination or a new deadline for it. Follow an industry to hear " +
                                    "of each new merger filed and determination published in it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.selectTab(Route.Mergers) }) { Text("Browse mergers") }
                                OutlinedButton(onClick = vm::openIndustries) { Text("Industries") }
                            }
                        }
                    }
                }

                if (unread.isNotEmpty()) {
                    section()
                    stickyHeader { StickyTitle("New (${unread.size})") }
                    count += unread.size
                    items(unread, key = { "new-" + it.key }) { e -> EventItem(e, unread = true) { open(vm, e) } }
                }

                if (upcoming.isNotEmpty()) {
                    section()
                    stickyHeader { StickyTitle("Coming up") }
                    count += upcoming.size
                    items(upcoming, key = { "up-" + it.key }) { e ->
                        ListRow(
                            title = e.mergerName,
                            subtitle = "${e.title} · ${Dates.upcoming(e.date, e.endDate)}",
                            trailing = Dates.span(e.date, e.endDate, compact = true),
                            onClick = { vm.openMerger(e.mergerId) },
                        )
                    }
                }

                if (tracking.tracked.isNotEmpty()) {
                    section()
                    stickyHeader { StickyTitle("Mergers you follow (${tracking.tracked.size})") }
                    count += tracking.tracked.size
                    items(tracking.tracked.reversed(), key = { "f-$it" }) { id ->
                        val m = summaries[id]
                        if (m != null) {
                            MergerCard(
                                m, followed = true,
                                onClick = { vm.openMerger(id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                onSwipe = { toggleFollow(id) },
                            )
                        } else {
                            // Not in the list (yet): a number followed before the
                            // register has it, or one that's only just arrived.
                            val name = sync?.events?.firstOrNull { it.mergerId == id && it.industryCode == null }?.mergerName
                            ListRow(
                                title = name ?: id,
                                subtitle = if (tracking.isAwaiting(id)) "Not on the register yet" else name?.let { id },
                                trailing = null,
                                onClick = { vm.openMerger(id) },
                            )
                        }
                    }
                }

                if (tracking.industries.isNotEmpty()) {
                    section()
                    stickyHeader { StickyTitle("Industries you follow (${tracking.industries.size})") }
                    count += tracking.industries.size
                    items(tracking.industries.reversed(), key = { "fi-" + it.code }) { ind ->
                        ListRow(
                            title = ind.name,
                            subtitle = "ANZSIC ${ind.code}",
                            trailing = null,
                            onClick = { vm.openIndustry(ind.code) },
                        )
                    }
                }

                if (earlier.isNotEmpty()) {
                    section()
                    stickyHeader { StickyTitle("Earlier") }
                    count += earlier.size
                    items(earlier, key = { "old-" + it.key }) { e -> EventItem(e, unread = false) { open(vm, e) } }
                }

                item(key = "alerts") { AlertsLink(vm) }
            }
        }
    }
}

private fun open(vm: AppViewModel, e: TrackedEvent) {
    vm.markSeen(listOf(e.key))
    vm.openMerger(e.mergerId)
}

@Composable
private fun EventItem(e: TrackedEvent, unread: Boolean, onClick: () -> Unit) {
    ListRow(
        title = e.mergerName,
        subtitle = Notifier.line(e),
        trailing = Dates.format(e.date, compact = true),
        onClick = onClick,
        emphasis = unread,
        leading = {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(
                    if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        },
    )
}

/** Shown while notifications are off, since then nothing reaches the phone. Here and in Settings. */
@Composable
internal fun NotificationsBanner() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifier.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifier.canPost(context)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Notifier.canPost(context)
    }
    if (allowed) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Notifications are off", fontWeight = FontWeight.SemiBold)
                Text("Updates will wait here instead.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                val denied = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (denied) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) { Text("Turn on") }
        }
    }
}

/**
 * The way to the alert settings, which live in Settings: one row saying how
 * they're set, so the switches aren't in two places.
 */
@Composable
private fun AlertsLink(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    ListRow(
        title = "Alerts",
        subtitle = listOf(
            if (tracking.autoTrackPhase2) "Phase 2 matters followed" else "Phase 2 matters not followed",
            if (tracking.notifyNewFilings) "new filings on" else "new filings off",
        ).joinToString(" · "),
        trailing = null,
        onClick = vm::openSettings,
        leading = {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}
