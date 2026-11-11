package fyi.mergers.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fyi.mergers.app.data.MergerIds

/** Industries shown before "Show all": enough to find one by name, short enough to leave room for mergers. */
private const val INDUSTRIES_SHOWN = 4

/** Mergers shown before "See all", which hands the search to the merger list. */
private const val MERGERS_SHOWN = 10

/**
 * Search across the site from the dashboard: industries by name or ANZSIC
 * code, then mergers by name, ID, party or industry. Each section shows the
 * best few; the merger list and the full industry matches are a tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val listState by vm.list.collectAsStateWithLifecycle()
    val industriesState by vm.industries.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadIndustries() }

    val query = vm.searchQuery
    val blank = query.isBlank()
    // A merger id however it's typed ("wa01087"), so it finds the one merger,
    // or, if the register hasn't got it, offers to follow it anyway.
    val typedId = remember(query) { MergerIds.parse(query) }
    val exact = typedId?.let { id -> listState.value?.firstOrNull { it.merger_id == id } }
    val unlisted = typedId?.takeIf { listState.value != null && exact == null }
    val mergers = remember(listState.value, query, tracking.trackedSet) {
        if (blank) emptyList()
        else listState.value?.let { all ->
            val found = vm.applyFilter(all, ListFilter(query = query), tracking.trackedSet)
            if (exact != null && exact !in found) listOf(exact) + found else found
        }.orEmpty()
    }
    val industries = remember(industriesState.value, query) {
        if (blank) emptyList()
        else industriesState.value?.let { vm.filterIndustries(it.industries, query) }.orEmpty()
    }
    var allIndustries by rememberSaveable(query) { mutableStateOf(false) }
    val loading = listState.value == null && listState.loading || industriesState.value == null && industriesState.loading

    // The cursor goes in the field when you arrive, not when you come back from a result.
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(vm.focusSearch) {
        if (vm.focusSearch) {
            vm.focusSearch = false
            field.requestFocus()
            keyboard?.show()
        }
    }
    // Put the keyboard away before leaving for a result.
    fun go(action: () -> Unit) {
        focus.clearFocus()
        action()
    }

    val snackbar = remember { SnackbarHostState() }
    val toggleFollow = rememberMergerFollowToggle(vm, snackbar)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = { go { vm.back() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Mergers, parties, industries") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(field),
            )
            when {
                blank -> Centered("Search mergers by name, merger ID or party, and industries by name or ANZSIC code.")
                unlisted == null && mergers.isEmpty() && industries.isEmpty() && loading -> Loading()
                unlisted == null && mergers.isEmpty() && industries.isEmpty() -> Centered("Nothing matches.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("search-results"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (unlisted != null) {
                        item(key = "unlisted") { NotOnRegister(vm, unlisted, tracking.isTracked(unlisted)) }
                    }
                    if (industries.isNotEmpty()) {
                        item(key = "h-industries") { SectionTitle(count("Industries", industries.size)) }
                        val shown = if (allIndustries) industries else industries.take(INDUSTRIES_SHOWN)
                        items(shown, key = { "i-" + it.code }) { i ->
                            ListRow(
                                title = i.name,
                                subtitle = "ANZSIC ${i.code} · ${i.merger_count} ${if (i.merger_count == 1) "merger" else "mergers"}",
                                trailing = null,
                                onClick = { go { vm.openIndustry(i.code) } },
                                leading = {
                                    Icon(
                                        Icons.Filled.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                        }
                        if (!allIndustries && industries.size > INDUSTRIES_SHOWN) {
                            item(key = "more-industries") {
                                SeeAll("Show all ${industries.size} industries") { allIndustries = true }
                            }
                        }
                    }
                    if (mergers.isNotEmpty()) {
                        item(key = "h-mergers") { SectionTitle(count("Mergers", mergers.size)) }
                        items(mergers.take(MERGERS_SHOWN), key = { "m-" + it.merger_id }) { m ->
                            MergerCard(
                                m,
                                tracking.isTracked(m.merger_id),
                                onClick = { go { vm.openMerger(m.merger_id) } },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                onSwipe = { toggleFollow(m.merger_id) },
                            )
                        }
                        if (mergers.size > MERGERS_SHOWN) {
                            item(key = "more-mergers") {
                                SeeAll("See all ${mergers.size} mergers") { go { vm.showAllMergersMatching(query) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun count(title: String, n: Int) = "$title ($n)"

@Composable
private fun SeeAll(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(text)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
    }
}
