package com.example.stash.ui.feed

import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stash.ui.components.StashLogo
import com.example.stash.ui.theme.StashTheme
import com.example.stash.data.FeedView
import com.example.stash.data.SortOrder
import com.example.stash.data.TagCount
import com.example.stash.models.StashItem
import kotlinx.coroutines.launch

/**
 * Top bar integrating Material 3 Expressive [AppBarWithSearch] with topic filter chips and sort controls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedTopBar(
    searchBarState: SearchBarState,
    searchFieldState: TextFieldState,
    query: String,
    searchResults: List<StashItem>,
    itemActions: StashItemActions,
    onOpenSettings: () -> Unit,
    sortOrder: SortOrder,
    onSelectSortOrder: (SortOrder) -> Unit,
    feedView: FeedView,
    onSelectFeedView: (FeedView) -> Unit,
    tags: List<TagCount>,
    selectedTags: Set<String>,
    chipsState: LazyListState,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    onScrollToTop: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val searchBarColors = SearchBarDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        inputFieldColors = SearchBarDefaults.inputFieldColors(
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = searchBarColors,
    )

    LaunchedEffect(searchBarState.targetValue) {
        if (searchBarState.targetValue == SearchBarValue.Collapsed && searchFieldState.text.isNotEmpty()) {
            searchFieldState.clearText()
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchFieldState,
            searchBarState = searchBarState,
            colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
            onSearch = {
                searchFieldState.clearText()
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = {
                Text(
                    text = "Search your stash...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            },
            leadingIcon = {
                if (searchBarState.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = {
                        searchFieldState.clearText()
                        scope.launch { searchBarState.animateToCollapsed() }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            trailingIcon = {
                if (searchFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { searchFieldState.clearText() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Tapping anywhere in the top-left area near the logo triggers scroll to top
                        if (offset.x <= 80.dp.toPx() && offset.y <= 80.dp.toPx()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScrollToTop()
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppBarWithSearch(
                state = searchBarState,
                colors = appBarWithSearchColors,
                inputField = inputField,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScrollToTop()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        StashLogo(
                            modifier = Modifier.size(width = 26.dp, height = 24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Open Settings",
                        )
                    }
                },
            )

        ExpandedFullScreenContainedSearchBar(
            state = searchBarState,
            inputField = inputField,
            colors = appBarWithSearchColors.searchBarColors,
        ) {
            FeedSearchResults(
                query = query,
                results = searchResults,
                actions = itemActions,
            )
        }

        FilterChipsRow(
            sortOrder = sortOrder,
            onSelectSortOrder = onSelectSortOrder,
            feedView = feedView,
            onSelectFeedView = onSelectFeedView,
            tags = tags,
            selected = selectedTags,
            chipsState = chipsState,
            onToggle = onToggleTag,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Feed Top Bar - Light", showBackground = true)
@Preview(name = "Feed Top Bar - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedTopBarPreview() {
    StashTheme(dynamicColor = false) {
        FeedTopBar(
            searchBarState = rememberSearchBarState(),
            searchFieldState = rememberTextFieldState(),
            query = "",
            searchResults = emptyList(),
            itemActions = StashItemActions(
                onOpenLink = {},
                onToggleRead = {},
                onOpenDetail = {},
                onDelete = {},
                onChat = {},
            ),
            onOpenSettings = {},
            sortOrder = SortOrder.Newest,
            onSelectSortOrder = {},
            feedView = FeedView.List,
            onSelectFeedView = {},
            tags = listOf(
                TagCount("AI Agent", 7),
                TagCount("LLM", 5),
                TagCount("Android", 4),
                TagCount("Design", 3),
            ),
            selectedTags = emptySet(),
            chipsState = rememberLazyListState(),
            onToggleTag = {},
        )
    }
}
