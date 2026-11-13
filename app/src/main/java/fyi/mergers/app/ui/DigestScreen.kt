package fyi.mergers.app.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import fyi.mergers.app.ui.theme.isDarkTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
    val snackbar = remember { SnackbarHostState() }
    val toggleFollow = rememberMergerFollowToggle(vm, snackbar)
    LaunchedEffect(Unit) { vm.refreshDigest() }
    val digest = state.value

    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
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
            // Above the keyboard, so the signup at the foot can scroll clear of it.
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
        ) {
            when {
                digest == null && state.loading -> Loading()
                digest == null -> Centered("Couldn't load the digest. Pull down to try again.")
                else -> DigestContent(vm, digest, tracking, toggleFollow)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestContent(vm: AppViewModel, digest: Digest, tracking: TrackingState, toggleFollow: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    SectionHeadingHaptics(listState)
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
        contentPadding = PaddingValues(bottom = 32.dp).readable(),
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
            stickyHeader(key = SectionHeaderPrefix + section.id) { SectionHeading(section, count) }
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
                        onSwipe = { toggleFollow(row.merger.merger_id) },
                    )
                }
            }
        }

        item(key = "email") { EmailSignup(vm) }
    }
}

/**
 * The digest by email, signed up for here: the site's worker emails a link
 * to confirm (it can't ask the app for its Turnstile check), and the address
 * goes on the list once that's pressed. Says what's collected and links the
 * privacy policy, as Play asks of a form like this.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EmailSignup(vm: AppViewModel) {
    val context = LocalContext.current
    val state = vm.signup
    var email by rememberSaveable { mutableStateOf("") }
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    // While the address is being typed, the whole card (Subscribe included)
    // stays above the keyboard: once it has finished opening, and again if
    // the card grows (an error under the field).
    val card = remember { BringIntoViewRequester() }
    var typing by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(typing) {
        if (!typing) return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) }.collectLatest {
            delay(120) // until the keyboard stops moving
            card.bringIntoView()
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = ink),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .bringIntoViewRequester(card)
            .testTag("digest-signup"),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state is SignupState.Sent) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    if (state is SignupState.Sent) "Check your inbox" else "Get this every Monday morning",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    // Announced when it changes to "Check your inbox": the button just pressed is gone.
                    modifier = Modifier.padding(start = 12.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state is SignupState.Sent) {
                Text(
                    "We’ve sent a link to ${state.email}. Tap it to confirm, and the digest will arrive each Monday.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                // Filled, so it reads as a button and its edge lines up with the text above.
                FilledTonalButton(
                    onClick = vm::resetDigestSignup,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = ink,
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Use another address")
                }
                return@Column
            }
            val sending = state == SignupState.Sending
            val submit = { vm.subscribeToDigest(email) }
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (state is SignupState.Failed) vm.resetDigestSignup()
                },
                label = { Text("Email address") },
                singleLine = true,
                enabled = !sending,
                isError = state is SignupState.Failed,
                supportingText = (state as? SignupState.Failed)?.let { f -> { Text(f.message) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .onFocusChanged { typing = it.isFocused }
                    .semantics { contentType = ContentType.EmailAddress },
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("We’ll email a link to confirm. ")
                        withLink(
                            LinkAnnotation.Url(
                                "$SITE/privacy",
                                TextLinkStyles(SpanStyle(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)),
                            ) { openUrl(context, "$SITE/privacy") },
                        ) { append("Privacy policy") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Button(
                    onClick = submit,
                    enabled = !sending && email.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (sending) {
                        LoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Subscribe")
                    }
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

/** A section of what's still under way (or only just filed), where a card can show it's followed. */
private val DigestSection.running: Boolean get() = when (this) {
    DigestSection.NEW, DigestSection.ONGOING_PHASE_1, DigestSection.ONGOING_PHASE_2,
    DigestSection.ONGOING_PUBLIC_BENEFIT, DigestSection.ONGOING_APPEALS -> true
    else -> false
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
    onSwipe: () -> Unit,
) {
    val context = LocalContext.current
    val c = digestColors(section.color)
    val presses = remember { MutableInteractionSource() }
    // Swiped either way, it follows or unfollows the merger, as a card in the merger list does.
    SwipeToFollow(
        followed,
        onSwipe,
        Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
        shape,
        // The section's colour: in the dark its ink is the light shade, its bar the deep one.
        if (isDarkTheme()) c.ink else c.bar,
    ) { actions ->
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            interactionSource = presses,
            modifier = actions.fillMaxWidth().then(rememberSquish(presses)),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        row.merger.merger_name,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    // Where nothing sits at the card's end; a decided card has its determination there.
                    if (followed && section.running) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Following",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).size(18.dp),
                        )
                    }
                }
                // The ID line, and at its end the determination: the one thing on
                // the card that isn't the merger, so a trailing icon button (as
                // Material's list items take one) rather than a label to read.
                // The button's touch target already stands it off the title.
                Row(
                    Modifier.padding(top = if (row.document != null) 0.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (listOf(row.merger.merger_id) + row.facts).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    row.document?.let { doc ->
                        FilledTonalIconButton(
                            onClick = { openUrl(context, doc) },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.wash, contentColor = c.ink),
                            modifier = Modifier.padding(start = 8.dp).size(IconButtonDefaults.smallContainerSize()),
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = "Open the determination (PDF)",
                                modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                            )
                        }
                    }
                }
                row.outcome?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = c.ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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

