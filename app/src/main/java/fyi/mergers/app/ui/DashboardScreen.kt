package fyi.mergers.app.ui

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.TopAppBarState
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
import fyi.mergers.app.data.Digest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.RecentEvent
import fyi.mergers.app.data.RecentEventDays
import fyi.mergers.app.data.RecentEventType
import fyi.mergers.app.data.SITE
import fyi.mergers.app.data.Stats
import fyi.mergers.app.data.StatusWindow
import fyi.mergers.app.data.WindowDurations
import fyi.mergers.app.data.Status
import fyi.mergers.app.data.UpcomingEvent
import fyi.mergers.app.ui.theme.isDarkTheme
import fyi.mergers.app.ui.theme.eventTypeColors
import fyi.mergers.app.ui.theme.recentEventColors
import fyi.mergers.app.widget.Upcoming
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val statsState by vm.stats.collectAsStateWithLifecycle()
    val upcomingState by vm.upcoming.collectAsStateWithLifecycle()
    val recentState by vm.currentStatus.collectAsStateWithLifecycle()
    val recentEventsState by vm.recentEvents.collectAsStateWithLifecycle()
    val digestState by vm.digest.collectAsStateWithLifecycle()
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
                            style = MaterialTheme.typography.titleLargeEmphasized,
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
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
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
                else -> DashboardContent(
                    vm, stats, upcomingState.value.orEmpty(), recentState.value, recentEventsState.value.orEmpty(),
                    digestState.value, bar.state,
                )
            }
        }
    }
}

/** Expressive's loading indicator: a shape that morphs through the M3 shape set while it spins. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(64.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardContent(
    vm: AppViewModel,
    stats: Stats,
    upcoming: List<UpcomingEvent>,
    recent: StatusWindow?,
    recentEvents: List<RecentEvent>,
    digest: Digest?,
    barState: TopAppBarState,
) {
    val soon = upcoming.filter { e ->
        val n = Dates.daysUntil(e.end_date ?: e.date) ?: return@filter false
        n >= 0 && (Dates.daysUntil(e.date) ?: 0) <= 14
    }.sortedBy { it.date }
    // As the widget: a hearing already under way is listed under today, "until" its last day.
    val days = soon.groupBy { Upcoming.day(it)?.toString() }
    val pastDays = remember(recentEvents) { RecentEventDays.of(recentEvents) }

    // Which day header each Coming up and Recent events item sits under, by
    // key, so a day header can tell when it's the one pinned at the top (it
    // then carries the section's title too, since it has pushed the title
    // itself off).
    val dayOfKey = remember(days, pastDays) {
        buildMap {
            days.forEach { (day, events) ->
                put(dayKey(day), dayKey(day))
                events.forEach { put(eventKey(it), dayKey(day)) }
            }
            pastDays.forEach { (day, events) ->
                put(pastDayKey(day), pastDayKey(day))
                events.forEach { put(eventKey(it), pastDayKey(day)) }
            }
        }
    }
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(vm, Route.Dashboard, listState, barState)
    // The day pinned at the top, and the next one while it's pushing that one
    // off: both carry the section's title, so the title rides up with the old
    // day and the new day arrives with it, rather than arriving bare and
    // gaining the title only once it reaches the top.
    val pinnedDays by remember(dayOfKey) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            buildSet {
                visible.firstOrNull { it.index == listState.firstVisibleItemIndex }
                    ?.let { dayOfKey[it.key] }?.let(::add)
                visible.forEach { item ->
                    val key = item.key
                    if (dayOfKey[key] == key && item.offset < item.size) add(key)
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("dashboard"),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroCard(
                    count = stats.by_status[Status.UNDER_ASSESSMENT] ?: 0,
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
                // Last week's digest: the site's "Catch me up".
                digest?.let { DigestCard(it, onClick = { vm.openDigest() }) }
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
                stickyHeader(key = dayKey(day)) { DayHeader("Coming up", day, pinned = dayKey(day) in pinnedDays) }
                items(events, key = ::eventKey) { e ->
                    ListRow(
                        title = e.merger_name,
                        subtitle = Upcoming.detail(e),
                        trailing = null,
                        onClick = { vm.openMerger(e.merger_id) },
                        leading = { EventTypeTile(e.type, e.event_type_display) },
                    )
                }
            }
        }

        // What has just happened on the register, newest day first: the site's
        // Recent events, with the same types left out (RecentEventType.shown).
        if (pastDays.isNotEmpty()) {
            stickyHeader(key = "h-Recent events") { StickyTitle("Recent events") }
            pastDays.forEach { (day, events) ->
                stickyHeader(key = pastDayKey(day)) { DayHeader("Recent events", day, pinned = pastDayKey(day) in pinnedDays) }
                items(events, key = ::eventKey) { e ->
                    val type = RecentEventType.of(e.type)
                    ListRow(
                        title = e.merger_name,
                        subtitle = e.title ?: type.label,
                        trailing = null,
                        onClick = { vm.openMerger(e.merger_id) },
                        leading = { RecentEventTile(type) },
                    )
                }
            }
        }

    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("en-AU"))

private fun dayKey(day: String?) = "u-$day"

private fun eventKey(e: UpcomingEvent) = "u-${e.merger_id}-${e.type}-${e.date}"

private fun pastDayKey(day: String) = "r-$day"

// Two events of a type can share a matter and a day (two Tribunal filings), so the title is in the key too.
private fun eventKey(e: RecentEvent) = "r-${e.merger_id}-${e.type}-${e.date}-${e.title}"

/**
 * A day in Coming up or Recent events. Sticky like the section titles: while
 * its events scroll under it, it's pinned at the top, and as it has pushed
 * the [section]'s title off by then, it says so itself.
 */
@Composable
private fun DayHeader(section: String, day: String?, pinned: Boolean) {
    val d = Dates.day(day)
    val label = when {
        d == null -> "Undated"
        else -> when (Dates.daysUntil(day)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> dayFormat.format(d)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            // Read as one heading, the day alone: the section's name it gains
            // while pinned is for the eye, and would change what's read mid-list.
            .clearAndSetSemantics { heading(); contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slides in from the start, nudging the date along, rather than popping in.
        AnimatedVisibility(
            visible = pinned,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Text(
                "$section · ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
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

/**
 * The tiles' outline: Expressive's six-sided cookie. The widget's tile
 * (res/drawable/widget_tile.xml) is the same outline: keep the two in step.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TileShape get() = MaterialShapes.Cookie6Sided

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EventTypeTile(type: String?, description: String?) {
    val c = eventTypeColors(type)
    Box(
        Modifier
            .size(32.dp)
            .clip(TileShape.toShape())
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

/** What kind of register event a recent event is, as the site's icons (constants/recentEventTypes.js). */
private fun recentEventIcon(type: RecentEventType): ImageVector = when (type) {
    RecentEventType.TRIBUNAL -> Icons.Outlined.Balance
    RecentEventType.FEDERAL_COURT -> Icons.Outlined.AccountBalance
    RecentEventType.PHASE_2_DETERMINATION, RecentEventType.PHASE_1_DETERMINATION -> Icons.Outlined.Gavel
    RecentEventType.REASONS -> Icons.Outlined.Description
    RecentEventType.UNDERTAKING_ACCEPTED -> Icons.Outlined.HistoryEdu
    RecentEventType.CEASED -> Icons.Outlined.Block
    RecentEventType.PHASE_2_REFERRAL -> Icons.AutoMirrored.Outlined.ArrowForward
    RecentEventType.COMPETITION_CONCERNS -> Icons.Outlined.WarningAmber
    RecentEventType.PUBLIC_BENEFIT -> Icons.Outlined.VolunteerActivism
    RecentEventType.REMEDY_OFFER -> Icons.Outlined.Handshake
    RecentEventType.REMEDY_QUESTIONNAIRE, RecentEventType.PHASE_2_CONSULTATION,
    RecentEventType.QUESTIONNAIRE -> Icons.Outlined.Forum
    RecentEventType.MATERIALLY_INCOMPLETE -> Icons.Outlined.ErrorOutline
    RecentEventType.NOTIFICATION_COMPLETED -> Icons.Outlined.CheckCircle
    RecentEventType.TIMELINE_EXTENSION -> Icons.Outlined.HourglassEmpty
    RecentEventType.WAIVER_DETERMINATION -> Icons.Outlined.Approval
    RecentEventType.NOTIFIED -> Icons.Outlined.AddCircleOutline
    RecentEventType.OTHER -> Icons.Outlined.Info
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentEventTile(type: RecentEventType) {
    val c = recentEventColors(type)
    Box(
        Modifier
            .size(32.dp)
            .clip(TileShape.toShape())
            .background(c.fill)
            .semantics { role = Role.Image; contentDescription = type.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(recentEventIcon(type), contentDescription = null, tint = c.onFill, modifier = Modifier.size(18.dp))
    }
}

private fun median(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

/**
 * One of the site's /current-status headlines: the median business days for
 * what was decided in the window, coloured by how it sits against the all-time
 * median (slower in the declined red, faster in the cleared green).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DecisionTimeCard(title: String, d: WindowDurations, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val darkTheme = isDarkTheme()
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
                    style = MaterialTheme.typography.headlineLargeEmphasized,
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

/**
 * The dashboard's headline: how many matters are under assessment, big, on
 * the brand's container colour, with the under-assessment shape
 * ([outcomeShape]) turning into place behind it on a spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(count: Int, subtitle: String, onClick: () -> Unit) {
    val turn = remember { Animatable(-60f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { turn.animateTo(0f, spring) }
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            val shape = outcomeShape(Status.UNDER_ASSESSMENT).toShape()
            // Drawn to the content's height, not its own: the shape bleeds off the corner.
            Box(
                Modifier
                    .matchParentSize()
                    .wrapContentSize(Alignment.TopEnd, unbounded = true)
                    .offset(x = 36.dp, y = (-28).dp)
                    .requiredSize(168.dp)
                    .graphicsLayer { rotationZ = turn.value }
                    .clip(shape)
                    .background(ink.copy(alpha = 0.08f)),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Mergers", style = MaterialTheme.typography.labelLargeEmphasized, color = ink.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.displayMediumEmphasized,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        " under assessment",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatCard(title: String, value: String, subtitle: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(top = 4.dp))
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
