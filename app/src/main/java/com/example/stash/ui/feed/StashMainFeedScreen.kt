package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection.Companion.Bottom
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    /** Opens the on-device chat for an item; navigation is the layout's concern, not the feed's. */
    onOpenChat: (StashItem) -> Unit,
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

    // The plain (non-contained) state: with the app bar gone there is no pill for the field to
    // grow out of, so it opens as its own full-screen surface.
    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()

    // Which item the "ask about this" sheet is open for, or null when it is closed.
    //
    // The *id*, not the item. Holding the object captured a snapshot from the instant of the swipe,
    // and a link swiped while it was still summarizing kept whatever it had then — category
    // "Unsorted", which resolves to the website hue, so the sheet's gradient was green regardless of
    // what the model went on to decide. The row underneath had already updated. Looking the item up
    // from the feed's live list each recomposition means the sheet follows it.
    //
    // Not rememberSaveable: a chooser open across process death should not be restored.
    var askAboutItemId by remember { mutableStateOf<String?>(null) }

    val itemActions = remember(viewModel, context, onOpenChat) {
        StashItemActions(
            onOpenLink = { openUrl(context, it.url) },
            onToggleRead = { viewModel.setRead(it.id, !it.isRead) },
            onExpand = { viewModel.setRead(it.id, true) },
            onDelete = viewModel::delete,
            // The swipe no longer opens the on-device chat directly: it asks which model should
            // answer. Nano is private and offline, the Gemini app is stronger and reads the live
            // page, and that trade is the user's to make per question rather than the app's to
            // decide once.
            //
            // The search surface is its own window over the NavDisplay, so a chat opened from a
            // result would otherwise slide in *behind* it. Collapse first; the feed path is a
            // no-op collapse. The sheet inherits the same constraint.
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
    // parked mid-row. Reset to the top so the filtered results start from the beginning. The chip
    // row is pinned above the list now, so it stays visible regardless.
    LaunchedEffect(state.selectedTags) {
        listState.scrollToItem(0)
    }

    // A shared or added link is inserted at the top, but the list keeps its offset, so the new row
    // lands above the viewport — the user never sees it arrive, nor the Pending/Summarizing state
    // it passes through while the model works. Pull the list up so the new row is visible.
    //
    // Keyed on the id alone: keying on the whole item would re-fire on every aiState transition
    // (Pending → Summarizing → Ready) and re-scroll a list the user had since moved on from.
    val newestId = state.items.firstOrNull()?.id
    LaunchedEffect(newestId) {
        // `hasLoaded` marks that the feed has rendered its first emission — empty or not. Tracking
        // the newest id alone cannot tell "the feed just populated on open" from "a link was
        // added": on an empty stash the first saved link looks identical to an initial load, and
        // would never scroll — exactly the case where seeing it arrive matters most.
        val shouldScroll = hasLoaded && newestId != null && newestId != lastSeenNewestId
        lastSeenNewestId = newestId
        hasLoaded = true
        if (shouldScroll) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            // No nestedScroll: the toolbar is pinned. It carries the only way to add a link or
            // search, so hiding it on scroll took the app's primary actions away exactly when the
            // user was moving through content and most likely to want them.
            //
            // No top bar either: search moved into that toolbar, which is the only thing that used
            // to open it. The feed gets the full height of the screen back.
            floatingActionButton = {
                FeedToolbar(
                    effort = state.summaryEffort,
                    modelChoice = state.modelChoice,
                    modelOptions = state.modelOptions,
                    isProbingModels = state.isProbingModels,
                    onAddUrl = viewModel::showAddUrl,
                    onSearch = { scope.launch { searchBarState.animateToExpanded() } },
                    onOpenModelMenu = viewModel::refreshModels,
                    onSelectEffort = viewModel::setSummaryEffort,
                    onSelectModel = viewModel::selectModel,
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
        ) { _ ->
            // With the app bar gone the Scaffold reports no top inset, so the status bar has to be
            // cleared here or the first row sits under the clock. Applied as real layout padding
            // rather than contentPadding: content should stop at the status bar, not scroll under
            // a bar that no longer exists.
            val contentModifier = Modifier.statusBarsPadding()
            val listContentPadding = PaddingValues(
                // Enough for the last row to scroll clear of the floating toolbar. It cannot stop
                // the toolbar overlapping a short list, since the toolbar floats in its own layer
                // — but a list that short does not scroll, so the toolbar stays put anyway.
                bottom = 96.dp,
            )

            if (state.items.isEmpty()) {
                FeedEmptyState(
                    isFiltered = state.selectedTags.isNotEmpty(),
                    tags = state.tags,
                    selectedTags = state.selectedTags,
                    chipsState = chipsState,
                    onToggleTag = viewModel::toggleTag,
                    modifier = contentModifier.padding(listContentPadding),
                )
            } else {
                FeedList(
                    items = state.items,
                    tags = state.tags,
                    selectedTags = state.selectedTags,
                    listState = listState,
                    chipsState = chipsState,
                    onToggleTag = viewModel::toggleTag,
                    actions = itemActions,
                    contentPadding = listContentPadding,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = contentModifier,
                )
            }
        }

        // Sibling of the Scaffold, not inside it: this is a full-screen surface that covers the
        // feed and the toolbar when expanded, so it must not be constrained by a Scaffold slot.
        FeedSearchSurface(
            searchBarState = searchBarState,
            searchFieldState = searchFieldState,
            scope = scope,
        ) {
            FeedSearchResults(
                query = state.query,
                results = state.searchResults,
                actions = itemActions,
            )
        }
    }

    // Resolved from live state every recomposition, so the sheet tracks the item rather than a
    // snapshot of it. Searched in both lists because the swipe can come from either the feed or the
    // search surface, and a sheet opened from a search result would otherwise find nothing here and
    // close itself.
    val askAboutItem = askAboutItemId?.let { id ->
        state.items.firstOrNull { it.id == id } ?: state.searchResults.firstOrNull { it.id == id }
    }

    // An id with no item behind it means the row is gone — deleted from the feed while its sheet
    // was open. Clear it, or the state stays set forever and the next swipe to the same id would
    // reopen a sheet for something that no longer exists.
    if (askAboutItemId != null && askAboutItem == null) {
        LaunchedEffect(askAboutItemId) { askAboutItemId = null }
    }

    // Outside the Box for the same reason AddUrlDialog is: a modal sheet hosts itself in its own
    // window, so nesting it in the feed's layout would buy nothing and constrain it.
    askAboutItem?.let { item ->
        AskAboutItemSheet(
            item = item,
            // Dismiss first, then navigate. The chat route slides up from the bottom edge — the
            // same edge the sheet occupies — so leaving the sheet up would have the two surfaces
            // crossing on the same axis.
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
