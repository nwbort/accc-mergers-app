package fyi.mergers.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.Digest
import fyi.mergers.app.data.DigestRow
import fyi.mergers.app.data.DigestSection
import fyi.mergers.app.data.Digests
import fyi.mergers.app.data.SITE
import fyi.mergers.app.tracking.TrackingState
import fyi.mergers.app.ui.theme.DigestColors
import fyi.mergers.app.ui.theme.digestColors
import kotlinx.coroutines.launch

/** The site's weekly digest page. */
const val DIGEST_URL = "$SITE/digest"

/**
 * The weekly digest, as the site's /digest ("Catch me up"): last week's new
 * filings and decisions, then everything still running, each section in its
 * colour. The counts at the top jump to their sections, as a link to one of
 * them (/digest#mergers-referred, from the weekly email) does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestScreen(vm: AppViewModel) {
    val state by vm.digest.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshDigest() }
    val digest = state.value

    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Catch me up") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { share(context, "Catch me up: the week in Australian mergers", DIGEST_URL) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openUrl(context, DIGEST_URL) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on mergers.fyi")
                    }
                },
                scrollBehavior = bar,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = state.loading && digest != null,
            onRefresh = vm::refreshDigest,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                digest == null && state.loading -> Loading()
                digest == null -> Centered("Couldn't load the digest. Pull down to try again.")
                else -> DigestContent(vm, digest, tracking)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestContent(vm: AppViewModel, digest: Digest, tracking: TrackingState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sections = remember(digest) {
        Digests.sections(digest).map { (s, mergers) -> Triple(s, mergers.size, Digests.groups(s, mergers)) }
    }
    // Where each section's heading sits in the list: after the intro and the
    // counts, a heading, then each group's label (if it has one) and rows.
    val headingIndex = remember(sections) {
        var i = 2
        sections.associate { (s, _, groups) ->
            (s to i).also { i += 1 + groups.sumOf { g -> (if (g.label != null) 1 else 0) + g.rows.size } }
        }
    }
    // A link to one section of the digest scrolls there once it's drawn.
    LaunchedEffect(headingIndex, vm.digestSection) {
        val id = vm.digestSection ?: return@LaunchedEffect
        DigestSection.byId(id)?.let(headingIndex::get)?.let { listState.scrollToItem(it) }
        vm.digestSection = null
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("digest"),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "intro") { DigestHero(digest) }

        if (sections.isEmpty()) {
            item(key = "quiet") { Centered("No merger activity to report for this period.") }
        } else {
            item(key = "counts") {
                Counts(sections.map { it.first to it.second }) { s ->
                    headingIndex[s]?.let { scope.launch { listState.animateScrollToItem(it) } }
                }
            }
        }

        sections.forEach { (section, count, groups) ->
            stickyHeader(key = "h-" + section.id) { SectionHeading(section, count) }
            groups.forEach { group ->
                group.label?.let { label ->
                    item(key = "g-${section.id}-$label") { GroupLabel(label) }
                }
                itemsIndexed(group.rows, key = { _, r -> "${section.id}-${r.merger.merger_id}" }) { i, row ->
                    DigestRowCard(
                        row,
                        section,
                        shape = segment(i, group.rows.size),
                        followed = tracking.isTracked(row.merger.merger_id),
                        onClick = { vm.openMerger(row.merger.merger_id) },
                    )
                }
            }
        }

        // The site signs you up for the email; that takes its Turnstile check, so it stays there.
        item(key = "email") {
            Card(
                onClick = { openUrl(context, DIGEST_URL) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("Get this every Monday morning", style = MaterialTheme.typography.titleSmallEmphasized)
                        Text("Subscribe by email on mergers.fyi", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A card's spring under the thumb: pressed, it gives a little, and springs back. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberSquish(presses: MutableInteractionSource): Modifier {
    val pressed by presses.collectIsPressedAsState()
    val squish by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "squish",
    )
    return Modifier.graphicsLayer { scaleX = squish; scaleY = squish }
}

/**
 * Each section's own outline and glyph from the Expressive set. A decision
 * takes its outcome's shape ([outcomeShape]): cleared's sunny seal, refused's
 * hard cookie, ceased's gem, the clock face for whatever is still running.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DigestSection.shape(): RoundedPolygon = when (this) {
    DigestSection.NEW -> MaterialShapes.Cookie9Sided
    DigestSection.CLEARED -> MaterialShapes.Sunny
    DigestSection.DECLINED -> MaterialShapes.Cookie4Sided
    DigestSection.CEASED -> MaterialShapes.Gem
    DigestSection.TO_PUBLIC_BENEFIT -> MaterialShapes.Clover4Leaf
    DigestSection.APPEALED, DigestSection.ONGOING_APPEALS -> MaterialShapes.Pentagon
    DigestSection.REFERRED, DigestSection.ONGOING_PHASE_1, DigestSection.ONGOING_PHASE_2,
    DigestSection.ONGOING_PUBLIC_BENEFIT -> MaterialShapes.Cookie12Sided
}

private val DigestSection.icon: ImageVector get() = when (this) {
    DigestSection.NEW -> Icons.Outlined.NewReleases
    DigestSection.CLEARED -> Icons.Outlined.Check
    DigestSection.DECLINED -> Icons.Outlined.Close
    DigestSection.CEASED -> Icons.Outlined.Block
    DigestSection.REFERRED -> Icons.AutoMirrored.Outlined.ArrowForward
    DigestSection.TO_PUBLIC_BENEFIT, DigestSection.ONGOING_PUBLIC_BENEFIT -> Icons.Outlined.VolunteerActivism
    DigestSection.APPEALED, DigestSection.ONGOING_APPEALS -> Icons.Outlined.Balance
    DigestSection.ONGOING_PHASE_1, DigestSection.ONGOING_PHASE_2 -> Icons.Outlined.HourglassTop
}

/** A section's glyph: its icon in its shape, on its colour. Decoration beside its name. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionGlyph(section: DigestSection, c: DigestColors, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(section.shape().toShape())
            .background(c.ink)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(section.icon, contentDescription = null, tint = c.onInk, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The week, headline first, as the dashboard's hero: what was filed and
 * decided, big, on the brand's container colour, with a shape turning into
 * place behind it on a spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestHero(digest: Digest) {
    val turn = remember { Animatable(-60f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { turn.animateTo(0f, spring) }
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val moved = Digests.moved(digest)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = ink),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .wrapContentSize(Alignment.TopEnd, unbounded = true)
                    .offset(x = 40.dp, y = (-32).dp)
                    .requiredSize(176.dp)
                    .graphicsLayer { rotationZ = turn.value }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(ink.copy(alpha = 0.08f)),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    Digests.period(digest) ?: "Last week",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        "$moved",
                        style = MaterialTheme.typography.displayMediumEmphasized,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        if (moved == 1) " filing or decision" else " filings and decisions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(Digests.headline(digest), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f))
            }
        }
    }
}

/** The page's summary cards: each section's count on its colour, with its glyph, two to a row, a tap away from it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Counts(counts: List<Pair<DigestSection, Int>>, onClick: (DigestSection) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (s, n) ->
                    val c = digestColors(s.color)
                    val presses = remember { MutableInteractionSource() }
                    Card(
                        onClick = { onClick(s) },
                        colors = CardDefaults.cardColors(containerColor = c.wash),
                        shape = MaterialTheme.shapes.large,
                        interactionSource = presses,
                        modifier = Modifier.weight(1f).then(rememberSquish(presses)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$n",
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = c.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                SectionGlyph(s, c, 30.dp)
                            }
                            Text(
                                s.chip,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A section's heading, pinned while its rows scroll under it: its glyph, its name and how many. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeading(section: DigestSection, count: Int) {
    val c = digestColors(section.color)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionGlyph(section, c, 26.dp)
        Text(
            section.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(start = 12.dp).weight(1f).semantics { heading() },
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.ink,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.wash)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun GroupLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
    )
}

/**
 * Rows in a group sit together as one segmented list (Expressive's): the
 * first and last round off the run, the ones between barely do.
 */
private fun segment(i: Int, n: Int): RoundedCornerShape {
    val outer = 18.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) outer else inner,
        topEnd = if (i == 0) outer else inner,
        bottomStart = if (i == n - 1) outer else inner,
        bottomEnd = if (i == n - 1) outer else inner,
    )
}

/** A merger in the digest: its name, then the dates that put it here, what was decided (with the document), and its summary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestRowCard(
    row: DigestRow,
    section: DigestSection,
    shape: RoundedCornerShape,
    followed: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val c = digestColors(section.color)
    val presses = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = presses,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp).then(rememberSquish(presses)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    row.merger.merger_name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
                if (followed) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Following",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                    )
                }
            }
            Text(
                (listOf(row.merger.merger_id) + row.facts).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            row.outcome?.let { outcome ->
                val doc = row.document
                if (doc != null) {
                    // Expressive's tonal button: its corners square up under the thumb.
                    FilledTonalButton(
                        onClick = { openUrl(context, doc) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.wash, contentColor = c.ink),
                        contentPadding = PaddingValues(start = 14.dp, end = 12.dp),
                        modifier = Modifier.padding(top = 8.dp).height(36.dp),
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(outcome, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open determination",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Text(
                        outcome,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = c.ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            row.outcomeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            row.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stripMarkdown(it.replace("\n", " ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * The dashboard's way in: last week at a glance (what was filed and decided)
 * and the week it covers. The whole digest is a tap away.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DigestCard(digest: Digest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Newspaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    listOfNotNull("Catch me up", Digests.period(digest)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Digests.headline(digest),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

