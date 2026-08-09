package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.data.FeedLayout
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow
import com.example.stash.ui.components.StashListRow
import kotlinx.coroutines.launch

/** Opens a saved link in the browser, tolerating URLs stored without a scheme. */
private fun openUrl(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(if (url.startsWith("http")) url else "https://$url")
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun StashMainFeedScreen(
    viewModel: StashFeedViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onItemClick: (StashItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val isExpanded = searchBarState.targetValue == SearchBarValue.Expanded
    val listState = rememberLazyListState()

    val useCardRows = state.feedLayout == FeedLayout.Card
    val context = LocalContext.current

    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collect(viewModel::setQuery)
    }

    // Changing a filter swaps the item set under a retained scroll offset, which leaves the
    // list parked mid-row — the first item renders clipped behind the chips. Reset to the top
    // whenever the filter changes.
    LaunchedEffect(state.selectedTags) {
        listState.scrollToItem(0)
    }

    // Only ever rendered inside the expanded search surface now, so it is always the live,
    // focused field — no collapsed tap-proxy role to account for.
    val searchInputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Search your stash...") },
            leadingIcon = {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            trailingIcon = {
                if (searchFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { searchFieldState.clearText() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // TopAppBar applies its own window insets; wrapping it in statusBarsPadding
                // would pad the status bar twice.
                Column {
                    // A plain app bar with a trailing search action: search is an action here,
                    // not the screen's identity, so the field only exists once invoked.
                    TopAppBar(
                        title = {
                            Column {
                                Text("Stash")
                                // Which Gemini Nano variant resolved (preview/fast vs stable/full).
                                // The two differ ~3x in inference time, so it is worth surfacing.
                                Text(
                                    text = state.modelVersion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::toggleFeedLayout) {
                                Icon(
                                    imageVector = if (useCardRows) {
                                        Icons.Default.ViewAgenda
                                    } else {
                                        Icons.Default.ViewList
                                    },
                                    contentDescription = if (useCardRows) {
                                        "Switch to compact rows"
                                    } else {
                                        "Switch to card rows"
                                    },
                                )
                            }
                            IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                )
                            }
                        },
                    )

                    if (state.tags.isNotEmpty()) {
                        FilterChipsRow(state.tags, state.selectedTags, viewModel::toggleTag)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = viewModel::showAddUrl,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    // The Scaffold zeroes its content insets, so the FAB has to clear the
                    // navigation bar itself or it floats over the last row.
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp, end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add URL"
                    )
                }
            }
        ) { padding ->
            // Only the bottom inset belongs in contentPadding — that space is scrollable, so
            // rows travel up through it. The top inset is applied as real layout padding on the
            // list itself, otherwise items scroll behind the transparent app bar and chip row.
            val listContentPadding = PaddingValues(
                // Enough for the last row to scroll clear of the FAB. It cannot prevent the FAB
                // overlapping a short list, since the FAB floats in its own layer.
                bottom = 96.dp,
            )
            
            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .padding(listContentPadding)
                        .padding(24.dp)
                ) {
                    // An active tag filter can empty the feed even with a blank query, so the
                    // "nothing saved yet" copy must not claim the stash is empty.
                    val isFiltered = state.query.isNotBlank() || state.selectedTags.isNotEmpty()
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (isFiltered) "Nothing found." else "Your stash is ready.",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        if (isFiltered) {
                            "Try a different phrase or clear a filter."
                        } else {
                            "Tap + to save your first URL. Everything stays on this device."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                    contentPadding = listContentPadding,
                ) {
                    items(state.items, key = StashItem::id) { item ->
                        // Opening an item counts as reading it; the toggle is for correcting that
                        // or marking something read without opening.
                        val open = {
                            viewModel.setRead(item.id, true)
                            onItemClick(item)
                        }
                        if (useCardRows) {
                            StashCardRow(
                                item = item,
                                onClick = open,
                                onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                                onOpenLink = { openUrl(context, item.url) },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        } else {
                            StashListRow(
                                item = item,
                                onClick = open,
                                onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    }
                }
            }
        }

        // Hosts the editable field and the keyboard; AppBarWithSearch expands into this.
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = searchInputField,
        ) {
            if (state.items.isEmpty()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = if (state.query.isBlank()) "Search your stash" else "Nothing found.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.query.isBlank()) {
                            "Find saved links by title, summary, or tag."
                        } else {
                            "Try a different phrase."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.items, key = StashItem::id) { item ->
                        // No shared-element scopes: this content lives in a Dialog window.
                        StashListRow(
                            item = item,
                            onClick = {
                                scope.launch { searchBarState.animateToCollapsed() }
                                onItemClick(item)
                            }
                        )
                    }
                }
            }
        }
    }

    if (state.showAddUrl) AddUrlDialog(viewModel::dismissAddUrl, viewModel::addUrl)
}

@Composable
private fun FilterChipsRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    // Order is stable: chips select in place rather than reordering under the finger.
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tags, key = { it }) { tag ->
            val isSelected = tag in selected
            // The `shapes` overload is the expressive one: it morphs the corner radius on
            // select/press via MotionScheme springs. Passing a fixed `shape` instead routes to
            // the legacy overload and disables that morph entirely.
            //
            // Shapes are pinned to rounded rectangles rather than FilterChipDefaults.shapes(),
            // whose selectedShape is CornerFull — a full pill reads as a different component
            // than the unselected chip and overshoots the current M3 chip spec.
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
                shapes = FilterChipDefaults.shapes(
                    shape = RoundedCornerShape(12.dp),
                    selectedShape = RoundedCornerShape(20.dp),
                    pressedShape = RoundedCornerShape(8.dp),
                ),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun AddUrlDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Stash") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
