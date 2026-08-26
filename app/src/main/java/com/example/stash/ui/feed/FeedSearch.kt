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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow

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

    val listState = rememberLazyListState()
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(results, key = StashItem::id) { item ->
            StashCardRow(
                item = item,
                onClick = { actions.onOpenDetail(item) },
                onOpenLink = { actions.onOpenLink(item) },
                onDelete = { actions.onDelete(item.id) },
                onChat = { actions.onChat(item) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
