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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import fyi.mergers.app.notify.Push
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
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                scrollBehavior = bar,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = { Text("Following") },
                actions = {
                    if (unread.isNotEmpty()) TextButton(onClick = vm::markAllRead) { Text("Mark all read") }
                },
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = vm.syncing,
            onRefresh = vm::refreshFollowing,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("following"), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { NotificationsBanner() }

                if (tracking.followsNothing) {
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
                    stickyHeader { StickyTitle("New (${unread.size})") }
                    items(unread, key = { "new-" + it.key }) { e -> EventItem(e, unread = true) { open(vm, e) } }
                }

                if (upcoming.isNotEmpty()) {
                    stickyHeader { StickyTitle("Coming up") }
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
                    stickyHeader { StickyTitle("Mergers you follow (${tracking.tracked.size})") }
                    items(tracking.tracked.reversed(), key = { "f-$it" }) { id ->
                        val m = summaries[id]
                        if (m != null) {
                            MergerCard(
                                m, followed = true,
                                onClick = { vm.openMerger(id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
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
                    stickyHeader { StickyTitle("Industries you follow (${tracking.industries.size})") }
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
                    stickyHeader { StickyTitle("Earlier") }
                    items(earlier, key = { "old-" + it.key }) { e -> EventItem(e, unread = false) { open(vm, e) } }
                }

                stickyHeader { StickyTitle("Alerts") }
                item { Settings(vm) }
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

/** Shown while notifications are off, since then nothing reaches the phone. */
@Composable
private fun NotificationsBanner() {
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

@Composable
private fun Settings(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ListItem(
            headlineContent = { Text("Follow every phase 2 matter") },
            supportingContent = { Text("Matters referred to phase 2 are followed automatically") },
            trailingContent = { Switch(checked = tracking.autoTrackPhase2, onCheckedChange = vm::setAutoTrackPhase2) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        ListItem(
            headlineContent = { Text("Every new filing") },
            supportingContent = { Text("A notification for each new merger or waiver on the register") },
            trailingContent = { Switch(checked = tracking.notifyNewFilings, onCheckedChange = vm::setNotifyNewFilings) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        val pushed = Push.ready(LocalContext.current)
        Text(
            if (pushed) {
                "Checked as soon as mergers.fyi publishes a change, which it does several times a weekday from the ACCC register."
            } else {
                "Checked about every two hours. mergers.fyi refreshes from the ACCC register several times a weekday."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
