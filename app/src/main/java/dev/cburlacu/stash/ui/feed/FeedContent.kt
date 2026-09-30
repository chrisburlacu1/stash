package dev.cburlacu.stash.ui.feed

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cburlacu.stash.data.SortOrder
import dev.cburlacu.stash.data.TopicCount
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.components.StashCardRow
import dev.cburlacu.stash.ui.theme.feedTextStyles

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

@Composable
fun FeedList(
    items: List<StashItem>,
    selectedTags: Set<String>,
    selectedItemIds: Set<String> = emptySet(),
    listState: LazyListState,
    actions: StashItemActions,
    contentPadding: PaddingValues,
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
    topics: List<TopicCount>,
    selectedTopic: String?,
    chipsState: LazyListState,
    onSelectTopic: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (topics.isEmpty()) return

    val haptic = LocalHapticFeedback.current

    LazyRow(
        state = chipsState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {

        items(topics, key = { it.name }) { topic ->
            val isSelected = topic.name.equals(selectedTopic, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTopic(if (isSelected) null else topic.name)
                },
                label = {
                    Text(
                        text = topic.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
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
