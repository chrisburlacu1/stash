package com.example.stash.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stash.models.StashItem
import com.example.stash.ui.components.StashCardRow

/**
 * The per-item callbacks a [StashCardRow] needs, bundled so the feed and the search surface bind
 * them identically. Both render the same card with the same five actions; passing them as one
 * object keeps the two call sites from drifting apart.
 */
class StashItemActions(
    val onOpenLink: (StashItem) -> Unit,
    val onToggleRead: (StashItem) -> Unit,
    val onExpand: (StashItem) -> Unit,
    val onDelete: (String) -> Unit,
    val onChat: (StashItem) -> Unit,
)

/**
 * The main feed: a pinned filter row above a scrolling list.
 *
 * The chips sit outside the LazyColumn rather than as its first item. As a list item they scrolled
 * away with the content, which meant changing a filter required scrolling back to the top first —
 * and the filter is exactly what you reach for after scanning a filtered list.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FeedList(
    items: List<StashItem>,
    tags: List<String>,
    selectedTags: Set<String>,
    listState: LazyListState,
    chipsState: LazyListState,
    onToggleTag: (String) -> Unit,
    actions: StashItemActions,
    contentPadding: PaddingValues,
    /**
     * Height of the top app bar plus the status bar.
     *
     * Applied as layout padding to the pinned chip row and as *content* padding to the list, so
     * cards scroll under the bar and are occluded by it rather than stopping at its lower edge.
     *
     * Computed by the caller from the theme token and the window inset, **not** from the Scaffold's
     * reported padding — see the note there. Do not "simplify" it back to `scaffoldPadding`.
     */
    topInset: Dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    // The chips are pinned over the list rather than stacked above it in a Column.
    //
    // Stacked, they pushed the list down and took the top inset as layout padding, which meant
    // cards could only ever scroll under the *chips*, never under the bar — and since the chip row
    // has no surface of its own, rows showed through it. Worse, the row appears and disappears with
    // the tag list, so the list's top edge jumped by its height whenever the first tag arrived.
    //
    // Overlaid, the list keeps full height and reserves room via contentPadding, so appearing and
    // disappearing chips change nothing about the list's layout — only what is drawn on top of it.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // Room for the bar and, when present, the chip row. Content padding rather than
                // layout padding: rows scroll *through* this region and are occluded by the bar,
                // instead of stopping dead at its lower edge.
                top = topInset + if (tags.isEmpty()) 0.dp else CHIP_ROW_HEIGHT,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            items(items, key = StashItem::id) { item ->
                StashCardRow(
                    item = item,
                    // Cards expand in place rather than navigating, so a plain tap has nowhere
                    // else to go. Opening the link is the sensible fallback for the rare card
                    // with no key points to reveal.
                    onClick = { actions.onOpenLink(item) },
                    onToggleRead = { actions.onToggleRead(item) },
                    onOpenLink = { actions.onOpenLink(item) },
                    onExpand = { actions.onExpand(item) },
                    onDelete = { actions.onDelete(item.id) },
                    onChat = { actions.onChat(item) },
                    // Highlights whichever chip put this row in a filtered feed.
                    activeTags = selectedTags,
                    // The feed owns the row's width and gutters, not the card. The card gutters
                    // sit outside animateItem so a reordering card animates its own bounds, not
                    // the surrounding whitespace.
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .animateItem(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // Pinned over the list, directly under the bar. Carries `surface` because rows now scroll
        // beneath it — without one, cards read straight through the chips.
        //
        // AnimatedVisibility rather than an early return: the row appears the moment the first tag
        // is extracted and disappears when the last is filtered away, and an un-animated appearance
        // popped a solid band over the feed mid-scroll. It fades and expands instead. The list's
        // contentPadding still changes underneath, which the LazyColumn animates on its own.
        AnimatedVisibility(
            visible = tags.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
        ) {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                FilterChipsRow(tags, selectedTags, chipsState, onToggleTag)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Height reserved for the pinned chip row: the chip's own height plus the 4dp gap below it.
 *
 * Hard-coded because the row is drawn in a different layer from the list that must make room for
 * it, so there is no layout relationship to measure through. Measuring it with `onSizeChanged` and
 * feeding that back into `contentPadding` was the alternative and it oscillates: the padding change
 * remeasures the list, which is the thing the measurement came from.
 */
private val CHIP_ROW_HEIGHT = 48.dp

/**
 * Shown when the feed has no rows. Still renders the chips above it: filtering down to zero results
 * must leave a way to unfilter.
 */
@Composable
fun FeedEmptyState(
    isFiltered: Boolean,
    tags: List<String>,
    selectedTags: Set<String>,
    chipsState: LazyListState,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Stacked rather than overlaid, unlike FeedList: nothing scrolls here, so there is no list
        // to scroll under the chips and no reason to take them out of the layout flow.
        if (tags.isNotEmpty()) {
            FilterChipsRow(tags, selectedTags, chipsState, onToggleTag)
            Spacer(Modifier.height(4.dp))
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)) {
            Spacer(Modifier.weight(1f))
            // The search query no longer touches the feed, so an empty feed means either a tag
            // filter or a genuinely empty stash — nothing else.
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterChipsRow(
    tags: List<String>,
    selected: Set<String>,
    chipsState: LazyListState,
    onToggle: (String) -> Unit,
) {
    // Scroll state is hoisted by the caller, not remembered here. Filtering down to zero results
    // swaps FeedList for FeedEmptyState, which destroys this composable — a locally remembered
    // state would die with it and snap the chips back to the start, losing the tag the user had
    // just scrolled to and tapped.
    //
    // Order is stable: chips select in place rather than reordering under the finger.
    LazyRow(
        state = chipsState,
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
                shapes = ChipShapes(
                    shape = MaterialTheme.shapes.medium,
                    selectedShape = MaterialTheme.shapes.large
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
