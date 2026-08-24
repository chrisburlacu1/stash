package com.example.stash.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
    onOpenBriefing: (itemIds: List<String>, topic: String?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    chipsState: LazyListState = rememberLazyListState(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = state.selectedItemIds.isNotEmpty()) {
        viewModel.clearSelection()
    }

    // Tracks which item was newest last time, so a new arrival can be told apart from the initial
    // load. Not rememberSaveable: after process death the feed is "new" again and should not
    // animate.
    var lastSeenNewestId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasLoaded by rememberSaveable { mutableStateOf(false) }

    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()

    // Which item the "ask about this" sheet is open for, or null when it is closed.
    var askAboutItemId by remember { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

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
            onToggleSelect = { item -> viewModel.toggleSelectItem(item.id) },
            onLongClickSelect = { item -> viewModel.toggleSelectItem(item.id) },
        )
    }

    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collect(viewModel::setQuery)
    }

    // Changing a filter swaps the item set under a retained scroll offset, which leaves the list
    // parked mid-row. Reset to the top only when the filter or sort order actually changes.
    var prevSelectedTags by remember { mutableStateOf(state.selectedTags) }
    LaunchedEffect(state.selectedTags) {
        if (state.selectedTags != prevSelectedTags) {
            prevSelectedTags = state.selectedTags
            listState.scrollToItem(0)
        }
    }

    var prevSortOrder by remember { mutableStateOf(state.sortOrder) }
    LaunchedEffect(state.sortOrder) {
        if (state.sortOrder != prevSortOrder) {
            prevSortOrder = state.sortOrder
            listState.scrollToItem(0)
        }
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
                if (state.selectedItemIds.isEmpty()) {
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
                    selectedItemIds = state.selectedItemIds,
                    listState = listState,
                    actions = itemActions,
                    contentPadding = listContentPadding,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onCatchMeUp = {
                        val activeTag = state.selectedTags.firstOrNull()
                        val matchingIds = state.items.map { it.id }
                        if (matchingIds.isNotEmpty()) {
                            onOpenBriefing(matchingIds, activeTag)
                        }
                    },
                    activeTopic = state.selectedTags.firstOrNull(),
                )
            }
        }

        // Floating contextual bottom selection bar
        AnimatedVisibility(
            visible = state.selectedItemIds.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ) + fadeIn(   animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ) + fadeOut(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            val count = state.selectedItemIds.size
            val primaryLabel = if (count == 1) "Brief" else "Compare"

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.wrapContentSize(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    // Leading Dismiss Button + Count
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Text(
                        text = "$count selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp),
                    )

                    VerticalDivider(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )

                    // Primary AI Action Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val ids = state.selectedItemIds.toList()
                            viewModel.clearSelection()
                            onOpenBriefing(ids, null)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Mark as Read
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.batchSetRead(true)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Mark as read",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Delete (with confirmation dialog)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showBatchDeleteConfirm = true
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    if (showBatchDeleteConfirm) {
        val count = state.selectedItemIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(if (count == 1) "Delete 1 item?" else "Delete $count items?") },
            text = { Text("Selected items will be permanently removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBatchDeleteConfirm = false
                        viewModel.batchDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
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

