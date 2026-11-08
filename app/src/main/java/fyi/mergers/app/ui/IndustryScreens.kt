package fyi.mergers.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.IndustryEntry
import fyi.mergers.app.data.IndustryNode
import fyi.mergers.app.data.IndustryPhase
import fyi.mergers.app.data.IndustryRef
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.industryPageUrl
import fyi.mergers.app.data.node
import fyi.mergers.app.ui.theme.phaseAccent
import kotlinx.coroutines.launch
import java.util.Locale

// ---- the list ----------------------------------------------------------------

/**
 * Every ANZSIC code a merger has been tagged with, most mergers first, as the
 * site's /industries table: a search box and each one's share of all reviews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndustriesScreen(vm: AppViewModel) {
    val state by vm.industries.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadIndustries() }

    var query by rememberSaveable { mutableStateOf("") }
    val index = state.value
    val shown = remember(index, query) { index?.let { vm.filterIndustries(it.industries, query) }.orEmpty() }
    // The bars are scaled to the biggest industry shown: none comes near 100%.
    val max = shown.maxOfOrNull { it.merger_count }?.coerceAtLeast(1) ?: 1

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Industries") },
                navigationIcon = { BackButton(vm) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchField(query, { query = it }, "Search by industry name or code")
            if (index != null) {
                Text(
                    "${shown.size} of ${index.industries.size} industries · ${index.total_mergers} reviews",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            RefreshBox(
                refreshing = state.loading && index != null,
                onRefresh = vm::loadIndustries,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    index == null && state.loading -> Loading()
                    index == null -> Centered("Couldn't load the industries. Pull down to try again.")
                    shown.isEmpty() -> Centered("No industries match.")
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(shown, key = { it.code }) { i ->
                            IndustryRow(
                                i,
                                total = index.total_mergers,
                                max = max,
                                followed = tracking.isIndustryTracked(i.code),
                                onClick = { vm.openIndustry(i.code) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndustryRow(i: IndustryEntry, total: Int, max: Int, followed: Boolean, onClick: () -> Unit) {
    // A merger can sit in several industries, so these sum to more than 100%.
    val share = if (total > 0) i.merger_count * 100.0 / total else 0.0
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                i.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                i.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            LinearProgressIndicator(
                progress = { i.merger_count.toFloat() / max },
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${i.merger_count} · ${"%.1f".format(Locale.ROOT, share)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// ---- one industry -------------------------------------------------------------

/**
 * One ANZSIC node, as the site's /industries/{code}: where it sits in the
 * tree, its counts, the industries under it, and its mergers by phase. Follow
 * it to hear of each new filing and determination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndustryScreen(vm: AppViewModel, code: String) {
    val state by remember(code) { vm.division(code) }.collectAsStateWithLifecycle()
    val index by vm.industries.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) { vm.loadIndustry(code) }

    val file = state.value
    val node = remember(file, code) { file?.node(code) }
    // The node carries its own name; the index is the fallback for a code with no node.
    val name = node?.name ?: index.value?.industries?.firstOrNull { it.code == code }?.name ?: code
    val followed = tracking.isIndustryTracked(code)
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val toggleFollow = {
        val now = vm.toggleIndustryFollow(code, name)
        if (now && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                if (now) "Following: you'll hear of new filings and determinations" else "No longer following",
            )
        }
        Unit
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ANZSIC $code") },
                navigationIcon = { BackButton(vm) },
                actions = {
                    if (node != null) {
                        IconButton(onClick = toggleFollow) {
                            Icon(
                                if (followed) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (followed) "Unfollow industry" else "Follow industry",
                            )
                        }
                    }
                    IconButton(onClick = { share(context, name, industryPageUrl(code)) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openUrl(context, industryPageUrl(code)) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on mergers.fyi")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = state.loading && file != null,
            onRefresh = { vm.loadIndustry(code) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                file == null && state.loading -> Loading()
                file == null -> Centered("Couldn't load this industry. Pull down to try again.")
                // A code outside the ANZSIC tree, or one nothing is filed under.
                node == null -> Centered("No mergers are filed under ANZSIC $code.")
                else -> IndustryContent(vm, node)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndustryContent(vm: AppViewModel, node: IndustryNode) {
    val list by vm.list.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    // list.json's fuller summaries (parties, stage) where it has them.
    val summaries = remember(list.value) { list.value.orEmpty().associateBy { it.merger_id } }

    var query by rememberSaveable(node.code) { mutableStateOf("") }
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val shown = remember(node, tokens) {
        node.mergers.filter { m ->
            val text = listOfNotNull(m.merger_name, m.merger_id, m.status, m.determination).joinToString(" ").lowercase()
            tokens.all { it in text }
        }
    }
    val groups = remember(shown) {
        IndustryPhase.entries.map { p -> p to shown.filter { IndustryPhase.of(it) == p } }.filter { it.second.isNotEmpty() }
    }
    val searchable = node.mergers.size > 5

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "crumbs") { Breadcrumb(vm, node.ancestors) }

        item(key = "head") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                val n = node.mergers.size
                Text(
                    "ANZSIC ${node.levelLabel?.lowercase() ?: "code"} ${node.code} · $n merger${if (n == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (node.mergers.isNotEmpty()) {
            item(key = "stats") {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Total reviews", "${node.mergers.size}", null, Modifier.weight(1f))
                        StatCard("Phase 2 reviews", "${node.phase2Count}", null, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Waivers", "${node.waiverCount}", null, Modifier.weight(1f))
                        StatCard("Under assessment", "${node.activeCount}", null, Modifier.weight(1f))
                    }
                }
            }
        }

        if (node.children.isNotEmpty()) {
            item(key = "children-title") { SectionTitle("Sub-industries") }
            items(node.children, key = { "c-" + it.code }) { child ->
                ChildRow(child, onClick = { vm.openIndustry(child.code) })
            }
        }

        if (node.mergers.isEmpty()) {
            item(key = "empty") {
                Centered(
                    if (node.children.isNotEmpty()) "No mergers filed here. Try the sub-industries above."
                    else "No mergers filed in this industry.",
                )
            }
        } else {
            item(key = "mergers-title") {
                SectionTitle("Mergers in this industry") {
                    if (searchable) {
                        Text(
                            "${shown.size} of ${node.mergers.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            if (searchable) {
                item(key = "search") { SearchField(query, { query = it }, "Search mergers in this industry") }
            }
            if (shown.isEmpty()) {
                item(key = "none") { Centered("No mergers match “${query.trim()}”.") }
            }
            groups.forEach { (phase, mergers) ->
                item(key = "g-" + phase.name) { GroupHeader(phase, mergers.size) }
                items(mergers, key = { phase.name + it.merger_id }) { m ->
                    val summary: MergerSummary = summaries[m.merger_id] ?: m.toSummary()
                    MergerCard(
                        summary,
                        followed = tracking.isTracked(m.merger_id),
                        onClick = { vm.openMerger(m.merger_id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Industries › division › … › parent, each a link up the tree. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Breadcrumb(vm: AppViewModel, ancestors: List<IndustryRef>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Crumb("Industries") { vm.openIndustries() }
        ancestors.forEach { a ->
            Separator()
            Crumb(a.name) { vm.openIndustry(a.code) }
        }
    }
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun Separator() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp).size(16.dp),
    )
}

@Composable
private fun ChildRow(child: IndustryRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            child.code,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(
            child.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountPill(child.count)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun CountPill(n: Int) {
    Text(
        "$n",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A phase group's heading: an accent bar in the phase's colour, its name and a count. */
@Composable
private fun GroupHeader(phase: IndustryPhase, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(phaseAccent(phase)))
        Text(
            phase.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp).weight(1f).semantics { heading() },
        )
        CountPill(count)
    }
}

// ---- shared -------------------------------------------------------------------

@Composable
private fun BackButton(vm: AppViewModel) {
    IconButton(onClick = { vm.back() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
