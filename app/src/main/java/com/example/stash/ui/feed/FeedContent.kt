package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stash.data.SortOrder
import com.example.stash.data.TagCount
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow

/**
 * The per-item callbacks a [StashCardRow] needs, bundled so the feed and the search surface bind
 * them identically. Both render the same card with the same five actions; passing them as one
 * object keeps the two call sites from drifting apart.
 */
class StashItemActions(
    /** Opens the page itself, from the card's header image or the detail sheet's primary button. */
    val onOpenLink: (StashItem) -> Unit,
    val onToggleRead: (StashItem) -> Unit,
    /**
     * Opens the item's detail sheet — what a plain tap on a card does.
     */
    val onOpenDetail: (StashItem) -> Unit,
    val onDelete: (String) -> Unit,
    val onChat: (StashItem) -> Unit,
)

/**
 * The main feed scrolling list.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FeedList(
    items: List<StashItem>,
    selectedTags: Set<String>,
    listState: LazyListState,
    actions: StashItemActions,
    contentPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(items, key = StashItem::id) { item ->
            StashCardRow(
                item = item,
                onClick = { actions.onOpenDetail(item) },
                onToggleRead = { actions.onToggleRead(item) },
                onOpenLink = { actions.onOpenLink(item) },
                onDelete = { actions.onDelete(item.id) },
                onChat = { actions.onChat(item) },
                activeTags = selectedTags,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateItem(),
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

/** Shown when the feed has no rows. */
@Composable
fun FeedEmptyState(
    isFiltered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            if (isFiltered) "Nothing matches this filter." else "Your stash is ready.",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (isFiltered) {
                "Clear a tag to see the rest of your stash."
            } else {
                "Tap + to save your first URL. Everything stays on this device."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterChipsRow(
    sortOrder: SortOrder,
    onSelectSortOrder: (SortOrder) -> Unit,
    tags: List<TagCount>,
    selected: Set<String>,
    chipsState: LazyListState,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    LazyRow(
        state = chipsState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Leading Sort Chip with distinct styling and Divider
        item(key = "sort_order_chip") {
            var showSortMenu by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSortMenu = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label = {
                            Text(
                                text = sortOrder.label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = androidx.compose.material3.AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    )

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        SortOrder.entries.forEach { order ->
                            val isSelected = order == sortOrder
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectSortOrder(order)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }

                androidx.compose.material3.VerticalDivider(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }

        items(tags, key = { it.name }) { tag ->
            val isSelected = tag.name in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(tag.name)
                },
                label = { Text(tag.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                shapes = ChipShapes(
                    shape = MaterialTheme.shapes.medium,
                    selectedShape = MaterialTheme.shapes.large,
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    selectedBorderColor = Color.Transparent,
                ),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}
