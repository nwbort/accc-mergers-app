package fyi.mergers.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.SITE
import fyi.mergers.app.data.Stats
import fyi.mergers.app.data.StatusWindow
import fyi.mergers.app.data.WindowDurations
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.UpcomingEvent
import fyi.mergers.app.ui.theme.eventTypeColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val statsState by vm.stats.collectAsStateWithLifecycle()
    val upcomingState by vm.upcoming.collectAsStateWithLifecycle()
    val recentState by vm.currentStatus.collectAsStateWithLifecycle()
    val stats = statsState.value

    // The name bar slides away as you scroll down the dashboard and comes
    // back as soon as you scroll up.
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                scrollBehavior = bar,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Column {
                        // The site's wordmark: the dot in the brand green.
                        val dot = MaterialTheme.colorScheme.primary
                        Text(
                            buildAnnotatedString {
                                append("mergers")
                                withStyle(SpanStyle(color = dot)) { append(".") }
                                append("fyi")
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Australian merger tracker",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::openSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = statsState.loading && stats != null,
            onRefresh = vm::refreshDashboard,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                stats == null && statsState.loading -> Loading()
                stats == null -> Centered("Couldn't load the dashboard. Pull down to try again.")
                else -> DashboardContent(vm, stats, upcomingState.value.orEmpty(), recentState.value)
            }
        }
    }
}

@Composable
fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardContent(vm: AppViewModel, stats: Stats, upcoming: List<UpcomingEvent>, recent: StatusWindow?) {
    val soon = upcoming.filter { e ->
        val n = Dates.daysUntil(e.end_date ?: e.date) ?: return@filter false
        n >= 0 && (Dates.daysUntil(e.date) ?: 0) <= 14
    }.sortedBy { it.date }
    val days = soon.groupBy { it.date?.take(10) }

    // Which day each Coming up item belongs to, by key, so a day header can
    // tell when it's the one pinned at the top (it then carries the section's
    // title too, since it has pushed the title itself off).
    val dayOfKey = remember(days) {
        buildMap {
            days.forEach { (day, events) ->
                put(dayKey(day), day)
                events.forEach { put(eventKey(it), day) }
            }
        }
    }
    val listState = rememberLazyListState()
    val pinnedDay by remember(dayOfKey) {
        derivedStateOf {
            val top = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == listState.firstVisibleItemIndex }
            top?.let { dayOfKey[it.key] }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("dashboard"),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "Mergers",
                    value = "${stats.by_status[Status.UNDER_ASSESSMENT] ?: 0} under assessment",
                    subtitle = "${stats.total_mergers} notified and ${stats.total_waivers} waivers",
                    onClick = { vm.showMergersWithStatus(Status.UNDER_ASSESSMENT) },
                )
                recent?.let { w ->
                    val context = LocalContext.current
                    val open = { openUrl(context, "$SITE/current-status") }
                    Text(
                        "Decision times · last ${w.days} days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        DecisionTimeCard("Waiver", w.waivers, open, Modifier.weight(1f).fillMaxHeight())
                        DecisionTimeCard("Notification – phase 1", w.notifications, open, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }

        if (stats.recent_determinations.isNotEmpty()) {
            stickyHeader(key = "h-Recent determinations") { StickyTitle("Recent determinations") }
            items(stats.recent_determinations.take(8), key = { "d-" + it.merger_id }) { d ->
                ListRow(
                    title = d.merger_name,
                    subtitle = listOfNotNull(
                        if (d.is_waiver) "Waiver" else d.stage?.split(" - ")?.first(),
                        d.merger_id,
                    ).joinToString(" · "),
                    trailing = Dates.format(d.determination_date, compact = true),
                    onClick = { vm.openMerger(d.merger_id) },
                    leading = { StatusGlyph(d.determination, conditions = d.has_conditions) },
                )
            }
        }

        if (stats.recent_mergers.isNotEmpty()) {
            stickyHeader(key = "h-Recently notified") { StickyTitle("Recently notified") }
            items(stats.recent_mergers.take(8), key = { "n-" + it.merger_id }) { m ->
                ListRow(
                    title = m.merger_name,
                    subtitle = listOfNotNull(if (m.is_waiver) "Waiver" else "Notification", m.merger_id).joinToString(" · "),
                    trailing = Dates.format(m.effective_notification_datetime, compact = true),
                    onClick = { vm.openMerger(m.merger_id) },
                )
            }
        }

        if (soon.isNotEmpty()) {
            stickyHeader(key = "h-Coming up") { StickyTitle("Coming up") }
            days.forEach { (day, events) ->
                stickyHeader(key = dayKey(day)) { DayHeader(day, pinned = pinnedDay == day) }
                items(events, key = ::eventKey) { e ->
                    ListRow(
                        title = e.merger_name,
                        subtitle = e.event_type_display,
                        trailing = null,
                        onClick = { vm.openMerger(e.merger_id) },
                        leading = { EventTypeTile(e.type, e.event_type_display) },
                    )
                }
            }
        }

    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("en-AU"))

private fun dayKey(day: String?) = "u-$day"

private fun eventKey(e: UpcomingEvent) = "u-${e.merger_id}-${e.type}-${e.date}"

/**
 * A day in Coming up. Sticky like the section titles: while its events
 * scroll under it, it's pinned at the top, and as it has pushed "Coming up"
 * off by then, it says so itself.
 */
@Composable
private fun DayHeader(day: String?, pinned: Boolean) {
    val d = Dates.day(day)
    val label = when {
        d == null -> "Undated"
        else -> when (Dates.daysUntil(day)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> dayFormat.format(d)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) {
            Text(
                "Coming up · ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * What kind of deadline an upcoming event is, as the site's icon tiles
 * (UpcomingEventsTimeline). The Coming up widget draws the same ones from
 * res/drawable (widget/UpcomingWidget.kt): keep the two in step.
 */
private fun eventTypeIcon(type: String?): ImageVector = when (type) {
    "consultation_due", "public_benefit_response_due" -> Icons.Outlined.Forum
    "notice_of_competition_concerns" -> Icons.Outlined.WarningAmber
    "public_benefit_assessment", "tribunal_hearing", "federal_court_hearing" -> Icons.Outlined.Balance
    else -> Icons.Outlined.Gavel
}

@Composable
internal fun EventTypeTile(type: String?, description: String?) {
    val c = eventTypeColors(type)
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.fill)
            .then(
                // Beside its own label (the widget's settings) it's decoration.
                if (description == null) Modifier
                else Modifier.semantics { role = Role.Image; contentDescription = description },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(eventTypeIcon(type), contentDescription = null, tint = c.onFill, modifier = Modifier.size(18.dp))
    }
}

private fun median(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

/**
 * One of the site's /current-status headlines: the median business days for
 * what was decided in the window, coloured by how it sits against the all-time
 * median (slower in the declined red, faster in the cleared green).
 */
@Composable
private fun DecisionTimeCard(title: String, d: WindowDurations, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    val delta = d.median_delta
    val tone = when {
        delta == null || delta == 0.0 || d.median == null -> MaterialTheme.colorScheme.onSurface
        delta > 0 -> if (darkTheme) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
        else -> if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF047857)
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val m = d.median
            if (m == null) {
                Text(
                    "Nothing decided in this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    median(m),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    " business days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (delta != null) {
                val lead = if (delta == 0.0) "About the same as usual" else {
                    val mag = kotlin.math.abs(delta)
                    "${median(mag)} business ${if (mag == 1.0) "day" else "days"} ${if (delta > 0) "slower" else "faster"}"
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Medium)) { append(lead) }
                        if (delta != 0.0) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" than usual") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            d.p90?.let {
                Text(
                    "90% within $it BD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, subtitle: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors, elevation = elevation) { content() }
    else Card(modifier = modifier, colors = colors, elevation = elevation) { content() }
}

/**
 * A section title that stays at the top while its section scrolls under it,
 * until the next section's title pushes it off.
 */
@Composable
fun StickyTitle(text: String) {
    SectionTitle(text, Modifier.background(MaterialTheme.colorScheme.surface))
}
