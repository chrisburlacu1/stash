package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stash.data.FeedView
import com.example.stash.data.SortOrder
import com.example.stash.data.TagCount
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow
import com.example.stash.ui.theme.feedTextStyles

/**
 * Bundled action callbacks for stash item interactions.
 */
class StashItemActions(
    val onOpenLink: (StashItem) -> Unit,
    val onToggleRead: (StashItem) -> Unit,
    val onOpenDetail: (StashItem) -> Unit,
    val onDelete: (String) -> Unit,
    val onChat: (StashItem) -> Unit,
    val onToggleSelect: ((StashItem) -> Unit)? = null,
    val onLongClickSelect: ((StashItem) -> Unit)? = null,
)

private enum class AgeBucket(val label: String) {
    Today("Today"),
    ThisWeek("Earlier this week"),
    ThisMonth("Earlier this month"),
    Older("Older"),
}

private fun ageBucketOf(savedAtEpochMillis: Long, nowMillis: Long): AgeBucket {
    val days = (nowMillis - savedAtEpochMillis).coerceAtLeast(0L) / 86_400_000L
    return when {
        days < 1 -> AgeBucket.Today
        days < 7 -> AgeBucket.ThisWeek
        days < 30 -> AgeBucket.ThisMonth
        else -> AgeBucket.Older
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FeedList(
    items: List<StashItem>,
    selectedTags: Set<String>,
    selectedItemIds: Set<String> = emptySet(),
    listState: LazyListState,
    actions: StashItemActions,
    contentPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCatchMeUp: (() -> Unit)? = null,
    activeTopic: String? = null,
    sortOrder: SortOrder = SortOrder.Newest,
    modifier: Modifier = Modifier,
) {
    val isInSelectionMode = selectedItemIds.isNotEmpty()
    val haptic = LocalHapticFeedback.current

    val showSectionLabels = sortOrder == SortOrder.Newest || sortOrder == SortOrder.Oldest
    val nowMillis = remember(items) { System.currentTimeMillis() }
    val sectionLabelStyle = feedTextStyles.sectionLabel
    val sectionLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (!activeTopic.isNullOrBlank() && onCatchMeUp != null && items.size >= 2 && !isInSelectionMode) {
            item(key = "topic_catch_up_banner") {
                OutlinedCard(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCatchMeUp()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Catch up on $activeTopic",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Briefing across these ${items.size} sources",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        itemsIndexed(items, key = { _, item -> "${sortOrder.name}-${item.id}" }) { index, item ->
            val sectionLabel = if (showSectionLabels) {
                val bucket = ageBucketOf(item.savedAtEpochMillis, nowMillis)
                val previousBucket = items.getOrNull(index - 1)
                    ?.let { ageBucketOf(it.savedAtEpochMillis, nowMillis) }
                if (bucket != previousBucket) bucket.label else null
            } else null

            Column {
                if (sectionLabel != null) {
                    Text(
                        text = sectionLabel.uppercase(),
                        style = sectionLabelStyle,
                        color = sectionLabelColor,
                        modifier = Modifier.padding(
                            start = 28.dp,
                            end = 24.dp,
                            top = if (index == 0) 4.dp else 20.dp,
                            bottom = 6.dp,
                        ),
                    )
                }

                StashCardRow(
                    item = item,
                    onClick = { actions.onOpenDetail(item) },
                    onOpenLink = { actions.onOpenLink(item) },
                    onDelete = { actions.onDelete(item.id) },
                    onChat = { actions.onChat(item) },
                    activeTags = selectedTags,
                    isInSelectionMode = isInSelectionMode,
                    isSelected = item.id in selectedItemIds,
                    onToggleSelect = { actions.onToggleSelect?.invoke(item) },
                    onLongClick = { actions.onLongClickSelect?.invoke(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}

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
    feedView: FeedView,
    onSelectFeedView: (FeedView) -> Unit,
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
        item(key = "sort_order_chip") {
            var showSortMenu by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    AssistChip(
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
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                        ),
                        shape = RoundedCornerShape(10.dp),
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

                FilledIconToggleButton(
                    checked = feedView == FeedView.Gallery,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectFeedView(if (it) FeedView.Gallery else FeedView.List)
                    },
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = if (feedView == FeedView.Gallery) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Filled.ViewAgenda
                        },
                        contentDescription = if (feedView == FeedView.Gallery) {
                            "Switch to list view"
                        } else {
                            "Switch to gallery view"
                        },
                        modifier = Modifier.size(17.dp),
                    )
                }

                VerticalDivider(
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
