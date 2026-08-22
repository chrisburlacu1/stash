package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.models.StashItem
import com.example.stash.ui.components.AskAboutItemSheet
import com.example.stash.ui.util.openInGemini
import com.example.stash.ui.util.openUrl
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun StashMainFeedScreen(
    viewModel: StashFeedViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenDetail: (StashItem) -> Unit,
    onOpenChat: (StashItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Hoisted out of FilterChipsRow so the horizontal scroll position survives the feed swapping
    // between FeedList and FeedEmptyState when a filter matches nothing.
    val chipsState = rememberLazyListState()

    // Tracks which item was newest last time, so a new arrival can be told apart from the initial
    // load. Not rememberSaveable: after process death the feed is "new" again and should not
    // animate.
    var lastSeenNewestId by remember { mutableStateOf<String?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()

    // Which item the "ask about this" sheet is open for, or null when it is closed.
    var askAboutItemId by remember { mutableStateOf<String?>(null) }

    val itemActions = remember(viewModel, context, onOpenDetail, onOpenChat) {
        StashItemActions(
            onOpenLink = { openUrl(context, it.url) },
            onToggleRead = { viewModel.setRead(it.id, !it.isRead) },
            onOpenDetail = { item ->
                scope.launch { searchBarState.animateToCollapsed() }
                onOpenDetail(item)
            },
            onDelete = viewModel::delete,
            onChat = { item ->
                scope.launch { searchBarState.animateToCollapsed() }
                askAboutItemId = item.id
            },
        )
    }

    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collect(viewModel::setQuery)
    }

    // Changing a filter swaps the item set under a retained scroll offset, which leaves the list
    // parked mid-row. Reset to the top so the filtered results start from the beginning.
    LaunchedEffect(state.selectedTags) {
        listState.scrollToItem(0)
    }

    val newestId = state.items.firstOrNull()?.id
    LaunchedEffect(newestId) {
        val shouldScroll = hasLoaded && newestId != null && newestId != lastSeenNewestId
        lastSeenNewestId = newestId
        hasLoaded = true
        if (shouldScroll) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                FeedTopBar(
                    searchBarState = searchBarState,
                    searchFieldState = searchFieldState,
                    query = state.query,
                    searchResults = state.searchResults,
                    itemActions = itemActions,
                    onOpenSettings = onOpenSettings,
                    sortOrder = state.sortOrder,
                    onSelectSortOrder = viewModel::setSortOrder,
                    tags = state.tags,
                    selectedTags = state.selectedTags,
                    chipsState = chipsState,
                    onToggleTag = viewModel::toggleTag,
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = viewModel::showAddUrl,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add URL",
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            val listContentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 80.dp,
            )

            if (state.items.isEmpty()) {
                FeedEmptyState(
                    isFiltered = state.selectedTags.isNotEmpty(),
                    modifier = Modifier.padding(listContentPadding),
                )
            } else {
                FeedList(
                    items = state.items,
                    selectedTags = state.selectedTags,
                    listState = listState,
                    actions = itemActions,
                    contentPadding = listContentPadding,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }

    val askAboutItem = askAboutItemId?.let { id ->
        state.items.firstOrNull { it.id == id } ?: state.searchResults.firstOrNull { it.id == id }
    }
    if (askAboutItemId != null && askAboutItem == null) {
        LaunchedEffect(askAboutItemId) { askAboutItemId = null }
    }

    askAboutItem?.let { item ->
        AskAboutItemSheet(
            item = item,
            onAskOnDevice = {
                askAboutItemId = null
                onOpenChat(item)
            },
            onAskGemini = {
                askAboutItemId = null
                openInGemini(context, item)
            },
            onDismiss = { askAboutItemId = null },
        )
    }

    if (state.showAddUrl) AddUrlDialog(viewModel::dismissAddUrl, viewModel::addUrl)
}
