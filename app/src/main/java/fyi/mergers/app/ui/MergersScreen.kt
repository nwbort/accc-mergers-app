package fyi.mergers.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import fyi.mergers.app.data.mergerPageUrl
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.PhaseFilter
import fyi.mergers.app.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergersScreen(vm: AppViewModel) {
    val listState by vm.list.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val f = vm.filter
    val all = listState.value
    val shown = remember(all, f, tracking.trackedSet) {
        all?.let { vm.applyFilter(it, f, tracking.trackedSet) }.orEmpty()
    }
    val lazy = rememberLazyListState()
    // A new search or filter starts from the top; coming back from a merger
    // keeps the place.
    var lastFilter by rememberSaveable { mutableIntStateOf(f.hashCode()) }
    LaunchedEffect(f) {
        if (f.hashCode() != lastFilter) {
            lastFilter = f.hashCode()
            lazy.scrollToItem(0)
        }
    }

    // Press and hold a card for a menu to follow it without opening it.
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var menuFor by remember { mutableStateOf<String?>(null) }
    val toggleFollow = { id: String ->
        val now = vm.toggleFollow(id)
        if (now && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val undo = snackbar.showSnackbar(
                if (now) "Following: you'll be notified of updates" else "No longer following",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (undo == SnackbarResult.ActionPerformed) vm.toggleFollow(id)
        }
        Unit
    }

    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).statusBarsPadding()) {
            OutlinedTextField(
                value = f.query,
                onValueChange = { vm.filter = f.copy(query = it) },
                placeholder = { Text("Search mergers, parties, industries") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (f.query.isNotEmpty()) {
                        IconButton(onClick = { vm.filter = f.copy(query = "") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Filters(vm, f)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        all == null -> ""
                        shown.size == 1 -> "1 matter"
                        else -> "${shown.size} matters"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::openIndustries) {
                    Icon(Icons.Filled.Category, contentDescription = null, Modifier.size(16.dp))
                    Text("By industry", Modifier.padding(start = 6.dp))
                }
            }
            RefreshBox(
                refreshing = listState.loading && all != null,
                onRefresh = vm::refreshList,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    all == null && listState.loading -> Loading()
                    all == null -> Centered("Couldn't load the register. Pull down to try again.")
                    shown.isEmpty() -> Centered("Nothing matches.")
                    else -> LazyColumn(
                        state = lazy,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.merger_id }) { m ->
                            val followed = tracking.isTracked(m.merger_id)
                            Box {
                                MergerCard(
                                    m,
                                    followed,
                                    onClick = { vm.openMerger(m.merger_id) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuFor = m.merger_id
                                    },
                                )
                                DropdownMenu(expanded = menuFor == m.merger_id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text(if (followed) "Stop following" else "Follow") },
                                        leadingIcon = {
                                            Icon(if (followed) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd, contentDescription = null)
                                        },
                                        onClick = { menuFor = null; toggleFollow(m.merger_id) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                        onClick = { menuFor = null; share(context, m.merger_name, mergerPageUrl(m.merger_id)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(vm: AppViewModel, f: ListFilter) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = f.followedOnly,
            onClick = { vm.filter = f.copy(followedOnly = !f.followedOnly) },
            label = { Text("Following") },
            leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        Menu(
            label = f.phase?.label ?: "Phase",
            selected = f.phase != null,
            options = listOf<Pair<String, PhaseFilter?>>("Any phase" to null) + PhaseFilter.entries.map { it.label to it },
            onPick = { vm.filter = f.copy(phase = it) },
        )
        Menu(
            label = f.status ?: "Status",
            selected = f.status != null,
            options = listOf<Pair<String, String?>>("Any status" to null) + vm.statusOptions.map { it to it },
            onPick = { vm.filter = f.copy(status = it) },
        )
        Menu(
            label = f.sort.label,
            selected = false,
            icon = true,
            options = SortOrder.entries.map { it.label to it },
            onPick = { vm.filter = f.copy(sort = it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Menu(label: String, selected: Boolean, options: List<Pair<String, T>>, onPick: (T) -> Unit, icon: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = if (icon) {
                { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}
