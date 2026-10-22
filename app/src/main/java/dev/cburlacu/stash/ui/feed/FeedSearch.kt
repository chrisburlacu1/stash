package dev.cburlacu.stash.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.components.ImageBitmapCache
import dev.cburlacu.stash.ui.theme.CardTones
import dev.cburlacu.stash.ui.theme.CategoryStyle
import dev.cburlacu.stash.ui.theme.cardTones
import dev.cburlacu.stash.ui.theme.categoryStyle
import dev.cburlacu.stash.ui.theme.feedTextStyles

private val THUMBNAIL_SIZE = 72.dp
private const val SEARCH_THUMBNAIL_TARGET_PX = 216

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
                text = "Try a different keyword or phrase.",
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        itemsIndexed(
            items = results,
            key = { _, item -> item.id },
        ) { index, item ->
            StashSearchResultRow(
                item = item,
                shape = segmentedShape(index = index, count = results.size),
                onClick = { actions.onOpenDetail(item) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Uses standard MaterialTheme.shapes tokens (`extraLarge` = 28.dp outer group corners,
 * `extraSmall` = 4.dp inner seam corners) for the Material 3 segmented list container.
 */
@Composable
private fun segmentedShape(index: Int, count: Int): CornerBasedShape {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall
    return when {
        count <= 1 -> outer
        index == 0 -> outer.copy(
            bottomStart = inner.bottomStart,
            bottomEnd = inner.bottomEnd,
        )
        index == count - 1 -> outer.copy(
            topStart = inner.topStart,
            topEnd = inner.topEnd,
        )
        else -> inner
    }
}

/**
 * Material 3 segmented search result row:
 * - Uses `MaterialTheme.shapes.extraLarge` (`28.dp`) outer corners and `extraSmall` (`4.dp`) inner corners.
 * - Uses balanced `16.dp` padding on all four sides so the top domain eyebrow and full summary sentence
 *   have generous breathing room.
 * - Displays the complete summary/takeaway sentence underneath the title without `...` truncation.
 */
@Composable
private fun StashSearchResultRow(
    item: StashItem,
    shape: CornerBasedShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = categoryStyle(item.category)
    val textStyles = feedTextStyles
    val tones = remember(item.seedColor, darkTheme) {
        cardTones(item.seedColor, darkTheme)
    }

    val containerColor = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val summaryLine = remember(item.headline, item.summary) {
        val raw = item.headline.ifBlank { item.summary }
        raw.lineSequence()
            .map { it.removePrefix("- ").trim() }
            .firstOrNull(String::isNotBlank)
            .orEmpty()
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Layer 1: Colored source domain eyebrow with category icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = tones.accent,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = item.domain.removePrefix("www.").uppercase(),
                        style = textStyles.eyebrow,
                        color = tones.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Layer 2: Primary bold title
                Text(
                    text = item.title,
                    style = textStyles.compactTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Layer 3: Full conceptual summary / takeaway sentence (no truncation)
                if (summaryLine.isNotBlank()) {
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SearchResultThumbnail(
                path = item.imagePath,
                cropBias = item.cropBias,
                style = style,
                tones = tones,
            )
        }
    }
}

@Composable
private fun SearchResultThumbnail(
    path: String?,
    cropBias: Float,
    style: CategoryStyle,
    tones: CardTones,
) {
    val shape = MaterialTheme.shapes.medium
    if (path.isNullOrBlank()) {
        FallbackSearchThumbnail(style = style, tones = tones, shape = shape)
        return
    }

    val bitmap by produceState<ImageBitmap?>(
        initialValue = path.let(ImageBitmapCache::get),
        key1 = path,
    ) {
        value = ImageBitmapCache.load(path, targetHeightPx = SEARCH_THUMBNAIL_TARGET_PX)
    }

    val currentBitmap = bitmap
    if (currentBitmap == null) {
        FallbackSearchThumbnail(style = style, tones = tones, shape = shape)
        return
    }

    val clampedBias = cropBias.coerceIn(-1f, 1f)
    val cropAlignment = remember(clampedBias) { BiasAlignment(0f, clampedBias) }

    Box(
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Image(
            bitmap = currentBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = cropAlignment,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FallbackSearchThumbnail(
    style: CategoryStyle,
    tones: CardTones,
    shape: CornerBasedShape,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(shape)
            .background(tones.accent.copy(alpha = 0.14f)),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = style.label,
            tint = tones.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}
