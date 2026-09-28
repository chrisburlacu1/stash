package com.example.stash.ui.feed


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stash.data.SortOrder
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.components.MetaDot
import com.example.stash.ui.theme.CardTones
import com.example.stash.ui.theme.cardTones
import com.example.stash.ui.theme.categoryStyle
import com.example.stash.ui.theme.feedTextStyles
import com.example.stash.ui.util.ImageBitmapCache

/**
 * Visual gallery feed displaying saved items as full-width image cards with overlaid metadata.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedGalleryList(
    items: List<StashItem>,
    selectedItemIds: Set<String>,
    listState: LazyListState,
    actions: StashItemActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    sortOrder: SortOrder = SortOrder.Newest,
) {
    val isInSelectionMode = selectedItemIds.isNotEmpty()
    val nowMillis = remember(items) { System.currentTimeMillis() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GALLERY_GUTTER),
    ) {
        items(items, key = { item -> "${sortOrder.name}-${item.id}" }) { item ->
            GalleryCard(
                item = item,
                isInSelectionMode = isInSelectionMode,
                isSelected = item.id in selectedItemIds,
                nowMillis = nowMillis,
                onClick = { actions.onOpenDetail(item) },
                onLongClick = { actions.onLongClickSelect?.invoke(item) },
                onToggleSelect = { actions.onToggleSelect?.invoke(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCard(
    item: StashItem,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hasImage = !item.imagePath.isNullOrBlank()

    val style = categoryStyle(item.category, darkTheme = hasImage || darkTheme)
    // For cards with images, the text sits on a dark gradient scrim (up to 95% black),
    // so we derive vibrant dark-theme tones against black for maximum legibility and dynamic color.
    val effectiveTones = remember(item.seedColor, darkTheme, hasImage, style.color) {
        if (hasImage) {
            cardTones(item.seedColor, dark = true, fallbackSeed = style.color)
        } else {
            cardTones(item.seedColor, darkTheme, fallbackSeed = style.color)
        }
    }
    val text = feedTextStyles
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val timeLabel = relativeSavedLabel(item.savedAtEpochMillis, nowMillis)

    // Dynamic content tones derived from item seedColor (extracted from content image pixels)
    val domainColor = effectiveTones.accent
    val iconColor = effectiveTones.accent
    val timeColor = if (hasImage) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (hasImage) Color.White else effectiveTones.onContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_ASPECT_RATIO)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isInSelectionMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isInSelectionMode) onToggleSelect() else onLongClick()
                },
            )
            .clearAndSetSemantics {
                contentDescription =
                    "${item.title}. ${style.label} from ${item.domain}, saved $timeLabel"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                effectiveTones.container
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imagePath = item.imagePath
            if (!imagePath.isNullOrBlank()) {
                GalleryImage(path = imagePath, cropBias = item.cropBias, tones = effectiveTones)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.25f to Color.Black.copy(alpha = 0.12f),
                                    0.50f to Color.Black.copy(alpha = 0.50f),
                                    0.75f to Color.Black.copy(alpha = 0.82f),
                                    1.00f to Color.Black.copy(alpha = 0.95f),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    effectiveTones.accent.copy(alpha = 0.20f),
                                    effectiveTones.container,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = effectiveTones.accent.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = item.domain.uppercase(),
                        style = text.eyebrow,
                        color = domainColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MetaDot(color = timeColor.copy(alpha = 0.6f))
                    Text(
                        text = timeLabel.uppercase(),
                        style = text.eyebrow,
                        color = timeColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.title,
                    style = text.compactTitle,
                    color = titleColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isInSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.45f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryImage(path: String, cropBias: Float, tones: CardTones) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = ImageBitmapCache.get(path),
        key1 = path,
    ) {
        value = ImageBitmapCache.load(path, GALLERY_IMAGE_TARGET_PX)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tones.container),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = remember(cropBias) {
                    BiasAlignment(horizontalBias = 0f, verticalBias = cropBias)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val GALLERY_ASPECT_RATIO = 2f
private val GALLERY_GUTTER = 12.dp
private const val GALLERY_IMAGE_TARGET_PX = 600
