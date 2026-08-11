package com.example.stash.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The full-screen search surface.
 *
 * There is no collapsed app bar any more — the toolbar's search button expands this directly, so
 * the feed keeps its full height and search costs nothing until it is asked for. That makes this a
 * plain full-screen search bar rather than the *contained* variant, which exists specifically to
 * grow out of an app bar pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSearchSurface(
    searchBarState: SearchBarState,
    searchFieldState: TextFieldState,
    scope: CoroutineScope,
    content: @Composable () -> Unit,
) {
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Search your stash...") },
            // Always a back affordance: this field is only ever shown expanded, so the collapsed
            // search-glyph state the old app bar pill needed no longer exists.
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

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
    ) {
        content()
    }
}

/**
 * Contents of the expanded search surface. Stays bare until something is typed — this space is for
 * search-specific content (recent searches, suggestions) once there is any.
 */
@Composable
fun FeedSearchResults(
    query: String,
    results: List<StashItem>,
    actions: StashItemActions,
) {
    if (query.isBlank()) return

    if (results.isEmpty()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Nothing found.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Try a different phrase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Explicit state, reset whenever the query changes.
    //
    // Without this the LazyColumn gets a fresh internal state each time the composable re-enters
    // (it returns early on a blank query, so it leaves and re-enters constantly while typing) and
    // Compose restores the *previous* query's scroll offset onto a completely different result
    // set. The visible effect is a search that opens part-way down its own results — usually at
    // the bottom, since the offset was saved against a longer list.
    val listState = rememberLazyListState()
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    // imePadding: the activity is adjustNothing, so the window no longer shrinks for the keyboard
    // and this list would otherwise run underneath it with its last results unreachable.
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(results, key = StashItem::id) { item ->
            // No shared-element scopes: this content lives in its own window. Results expand in
            // place here too, so picking one no longer has to dismiss the search surface.
            StashCardRow(
                item = item,
                onClick = { actions.onOpenLink(item) },
                onToggleRead = { actions.onToggleRead(item) },
                onOpenLink = { actions.onOpenLink(item) },
                onExpand = { actions.onExpand(item) },
                onDelete = { actions.onDelete(item.id) },
                onChat = { actions.onChat(item) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
