package fyi.mergers.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.toShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerEvent
import fyi.mergers.app.data.MergerIds
import fyi.mergers.app.data.Party
import fyi.mergers.app.data.Reasons
import fyi.mergers.app.data.documentUrl
import fyi.mergers.app.data.headerStatus
import fyi.mergers.app.data.mergerPageUrl
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.outcomeText
import fyi.mergers.app.data.stageLabel
import fyi.mergers.app.tracking.TrackedEvents
import fyi.mergers.app.ui.theme.AppealIndigo
import fyi.mergers.app.ui.theme.statusColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MergerDetailScreen(vm: AppViewModel, id: String) {
    val state by remember(id) { vm.detail(id) }.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { vm.loadDetail(id) }

    val m = state.value
    val followed = tracking.isTracked(id)
    // Not a failure to load: a followed id still waiting, or a well-formed one
    // the register doesn't list (a link to it, say).
    val listState by vm.list.collectAsStateWithLifecycle()
    val unlisted = tracking.isAwaiting(id) ||
        MergerIds.parse(id) == id && listState.value?.none { it.merger_id == id } == true
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val toggleFollow = {
        val now = vm.toggleFollow(id)
        if (now && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(if (now) "Following: you'll be notified of updates" else "No longer following")
        }
        Unit
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(id) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Fills and morphs from round to square-ish when on, on a spring.
                    FilledTonalIconToggleButton(
                        checked = followed,
                        onCheckedChange = { toggleFollow() },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(
                            if (followed) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (followed) "Unfollow" else "Follow",
                        )
                    }
                    IconButton(onClick = { share(context, m?.merger_name ?: id, mergerPageUrl(id)) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Open on mergers.fyi") },
                                onClick = { menu = false; openUrl(context, mergerPageUrl(id)) },
                            )
                            m?.url?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open on the ACCC register") },
                                    onClick = { menu = false; openUrl(context, url) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = state.loading && m != null,
            onRefresh = { vm.loadDetail(id) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                m == null && state.loading -> Loading()
                m == null && unlisted -> LazyColumn(Modifier.fillMaxSize()) {
                    item { NotOnRegister(vm, id, followed, Modifier.padding(top = 8.dp)) }
                }
                m == null -> Centered("Couldn't load $id. Pull down to try again.")
                else -> Detail(vm, m)
            }
        }
    }
}

@Composable
private fun Detail(vm: AppViewModel, m: MergerDetail) {
    val context = LocalContext.current
    val events = remember(m) { m.events.sortedWith(compareByDescending<MergerEvent> { it.date ?: "" }) }
    val upcoming = remember(m) { TrackedEvents.upcoming(m).sortedBy { it.date } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Header(m) }

        item { Facts(m) }

        if (upcoming.isNotEmpty()) {
            item { SectionTitle("Coming up") }
            items(upcoming, key = { "up-" + it.key }) { e ->
                ListRow(
                    title = e.title,
                    subtitle = Dates.relative(e.date)?.replaceFirstChar { it.uppercase() },
                    trailing = Dates.format(e.date),
                    onClick = {},
                )
            }
        }

        m.related_merger?.merger_id?.let { relatedId ->
            item {
                val what = when (m.related_merger.relationship) {
                    "refiled_as", "suspended_refiled_as" -> "Re-filed as"
                    "refiled_from", "suspended_refiled_from" -> "Re-filed from"
                    else -> "Related"
                }
                SectionTitle(what)
                ListRow(m.related_merger.merger_name ?: relatedId, relatedId, null, onClick = { vm.openMerger(relatedId) })
            }
        }

        m.merger_description?.takeIf { it.isNotBlank() }?.let { desc ->
            item {
                SectionTitle("About the acquisition")
                Expandable(desc)
            }
        }

        item { Parties(m) }

        if (m.anzsic_codes.isNotEmpty()) {
            item {
                SectionTitle("Industries")
                Chips(m.anzsic_codes.map { "${it.name} (${it.code})" to { vm.openIndustry(it.code) } })
            }
        }

        if (m.comments.isNotEmpty()) {
            item { SectionTitle("Commentary") }
            items(m.comments) { c ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Prose(c.commentary)
                        val by = listOfNotNull(c.author, Dates.format(c.date)).joinToString(" · ")
                        if (by.isNotEmpty()) {
                            Text(by, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        (m.appeals.ifEmpty { listOfNotNull(m.appeal) }).forEach { a ->
            item {
                SectionTitle("Tribunal review")
                InfoCard(
                    listOfNotNull(
                        a.tribunal_number?.let { "Matter" to it },
                        a.appellant?.let { "Applicant" to it },
                        "Status" to (if (a.status == "concluded") "Concluded" else "Current"),
                        a.outcome?.let { "Outcome" to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                        Dates.format(a.filed_date)?.let { "Filed" to it },
                        Dates.format(a.hearing_date)?.let { start ->
                            "Hearing" to (Dates.format(a.hearing_end_date)?.let { "$start – $it" } ?: start)
                        },
                        Dates.format(a.concluded_date)?.let { "Concluded" to it },
                    ),
                    link = a.tribunal_url?.let { "Tribunal page" to it },
                )
            }
        }

        m.judicial_reviews.forEach { r ->
            item {
                SectionTitle("Federal Court judicial review")
                InfoCard(
                    listOfNotNull(
                        r.case_number?.let { "File" to it },
                        r.applicant?.let { "Applicant" to it },
                        r.court?.let { "Court" to it },
                        "Status" to (if (r.status == "concluded") "Concluded" else "Current"),
                        r.outcome?.let { "Outcome" to it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                        Dates.format(r.filed_date)?.let { "Filed" to it },
                    ),
                    link = r.case_url?.let { "Court file" to it },
                )
            }
        }

        if (events.isNotEmpty()) {
            item { SectionTitle("Timeline") }
            items(events.size) { i ->
                EventRow(events[i], last = i == events.lastIndex) { url -> openUrl(context, url) }
            }
        }

        if (m.similar_mergers.isNotEmpty()) {
            item { SectionTitle("Similar mergers") }
            items(m.similar_mergers, key = { "sim-" + it.merger_id }) { s ->
                ListRow(
                    s.merger_name,
                    s.merger_id,
                    null,
                    onClick = { vm.openMerger(s.merger_id) },
                    // Decided matters show their outcome; live ones their status
                    // (under assessment, referred to phase 2 and so on).
                    leading = (s.accc_determination ?: s.status)?.let { { StatusBadge(it) } },
                )
            }
        }
    }
}

/**
 * The title block, filled with the colour of what the matter is carrying, as
 * the site's detail page: a deep fill once decided (indigo while under
 * appeal), a pale tint of the status colour while it runs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(m: MergerDetail) {
    val input = m.outcomeInput()
    val header = input.headerStatus()
    val decided = header?.decided == true
    val colors = statusColors(header?.label ?: m.status)
    val underReview = m.under_appeal || m.under_judicial_review
    val (bg, fg) = when {
        decided && underReview -> AppealIndigo.fill to AppealIndigo.onFill
        decided -> colors.fill to colors.onFill
        else -> colors.fill.copy(alpha = 0.14f) to MaterialTheme.colorScheme.onSurface
    }
    val statusLine = listOfNotNull(
        input.outcomeText(),
        input.stageLabel(),
        "Waiver".takeIf { m.is_waiver },
        "Under appeal".takeIf { m.under_appeal },
        "Under judicial review".takeIf { m.under_judicial_review },
    ).joinToString(" · ")

    // The outcome's own shape (as its glyph in lists), large and faint,
    // turning into place behind the title on a spring.
    val turn = remember(m.merger_id) { Animatable(-45f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(m.merger_id) { turn.animateTo(0f, spring) }
    val shape = outcomeShape(header?.label ?: m.status).toShape()

    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg),
    ) {
        // Drawn to the content's height, not its own: the shape bleeds off the corner.
        Box(
            Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.TopEnd, unbounded = true)
                .offset(x = 40.dp, y = (-36).dp)
                .requiredSize(160.dp)
                .graphicsLayer { rotationZ = turn.value }
                .clip(shape)
                .background(fg.copy(alpha = 0.10f)),
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                statusLine.uppercase(),
                color = fg,
                style = MaterialTheme.typography.labelMediumEmphasized,
                letterSpacing = 0.8.sp,
            )
            Text(
                m.merger_name,
                color = fg,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
    }
}

@Composable
private fun Facts(m: MergerDetail) {
    val determinationDate = m.determination_publication_date
    val facts = listOfNotNull(
        Dates.format(m.effective_notification_datetime)?.let { (if (m.is_waiver) "Applied" else "Notified") to it },
        Dates.format(m.original_notification_datetime)
            ?.takeIf { m.original_notification_datetime?.take(10) != m.effective_notification_datetime?.take(10) }
            ?.let { "First notified" to it },
        Dates.format(m.consultation_response_due_date)?.let { "Consultation responses due" to it },
        if (determinationDate == null) {
            Dates.format(m.end_of_determination_period)?.let { "Determination due" to it }
        } else {
            Dates.format(determinationDate)?.let { "Determined" to it }
        },
        m.phase_1_estimate?.takeIf { determinationDate == null && !m.is_waiver }?.expected_business_days?.let { days ->
            val range = m.phase_1_estimate.range_business_days.takeIf { it.size == 2 }?.let { " (${it[0]}–${it[1]})" } ?: ""
            "Typical phase 1" to "$days business days$range"
        },
        m.phase_2_determination?.let { "Phase 2 determination" to it },
        Dates.format(m.ceased_date)?.let { "Ceased" to it },
        m.stage?.let { "Stage" to it },
    )
    InfoCard(facts)
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, link: Pair<String, String>? = null) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        k,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
                }
            }
            if (link != null) {
                TextButton(onClick = { openUrl(context, link.second) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(link.first)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Expandable(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = text.length > 600
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        if (!long || expanded) {
            Prose(text)
        } else {
            Prose(text.take(600).substringBeforeLast(' ') + "…")
        }
        if (long) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
        }
    }
}

@Composable
private fun Parties(m: MergerDetail) {
    val groups = listOf("Acquirers" to m.acquirers, "Targets" to m.targets, "Other parties" to m.other_parties)
        .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) return
    SectionTitle("Parties")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (label, parties) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                parties.forEach { PartyLine(it) }
            }
        }
    }
}

@Composable
private fun PartyLine(p: Party) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(p.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (p.identifier != null) {
            Text(
                "  ${p.identifier_type ?: ""} ${p.identifier}".trimEnd(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(labels: List<Pair<String, () -> Unit>>) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { (label, onClick) ->
            AssistChip(onClick = onClick, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

/** One event on the timeline: a dot and a line, its date, title, document and the reasons. */
@Composable
private fun EventRow(e: MergerEvent, last: Boolean, open: (String) -> Unit) {
    val doc = documentUrl(e.url_gh, e.url)
    var expanded by rememberSaveable(e.date, e.title) { mutableStateOf(false) }
    val reasons: List<Reasons.Block> = remember(e) { Reasons.forEvent(e) }
    val accent = when {
        e.is_appeal -> AppealIndigo.fill
        e.is_determination_event -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(accent))
            if (!last) {
                Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp, bottom = 14.dp)
                .then(if (doc != null) Modifier.clickable { open(doc) } else Modifier),
        ) {
            Text(
                Dates.format(e.date) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                e.display_title ?: e.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (e.is_determination_event) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (doc != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(
                        " Open document",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (reasons.isNotEmpty()) {
                Row(
                    Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) "Hide reasons" else "Show reasons",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (expanded) {
                    Column(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reasons.forEach { ReasonBlock(it) }
                    }
                }
            }
        }
    }
}

/** One block of a determination's reasons: a heading, a paragraph or a list. */
@Composable
private fun ReasonBlock(b: Reasons.Block) {
    val body = MaterialTheme.typography.bodySmall
    when (b) {
        is Reasons.Block.Heading -> Text(b.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        is Reasons.Block.Paragraph -> Text(if (b.number != null) "${b.number}  ${b.text}" else b.text, style = body)
        is Reasons.Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEach { item ->
                ListItemRow("•", item.text)
                item.children.forEach { child ->
                    Box(Modifier.padding(start = 14.dp)) { ListItemRow("◦", child) }
                }
            }
        }
        is Reasons.Block.Lettered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEach { (letter, text) -> ListItemRow(letter, text, bold = true) }
        }
    }
}

@Composable
private fun ListItemRow(marker: String, text: String, bold: Boolean = false) {
    Row {
        Text(
            marker,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
