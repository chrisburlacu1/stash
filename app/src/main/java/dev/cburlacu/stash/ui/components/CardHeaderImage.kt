package dev.cburlacu.stash.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.cburlacu.stash.ui.theme.CardTones
import dev.cburlacu.stash.ui.theme.CategoryStyle
import dev.cburlacu.stash.ui.util.ImageBitmapCache

private const val HEADER_IMAGE_TARGET_PX = 600
private val HEADER_IMAGE_HEIGHT = 180.dp
private const val HEADER_SCRIM_ALPHA = 0.38f
private val GENERATED_TILE_HEIGHT = 108.dp

@Composable
internal fun CardHeaderImage(
    path: String?,
    cropBias: Float,
    style: CategoryStyle,
    tones: CardTones,
    domain: String,
    typeBadgeStyle: TextStyle,
    onOpenLink: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (path.isNullOrBlank()) {
        GeneratedHeaderTile(
            style = style,
            tones = tones,
            domain = domain,
            typeBadgeStyle = typeBadgeStyle,
            onOpenLink = onOpenLink,
            modifier = modifier,
        )
        return
    }

    val bitmap by produceState<ImageBitmap?>(initialValue = path.let(ImageBitmapCache::get), key1 = path) {
        value = ImageBitmapCache.load(path, HEADER_IMAGE_TARGET_PX)
    }

    val scrimColor = MaterialTheme.colorScheme.scrim

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_IMAGE_HEIGHT)
            .then(
                if (onOpenLink != null) {
                    Modifier.clickable(onClick = onOpenLink, onClickLabel = "Open link")
                } else Modifier
            ),
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
                modifier = Modifier
                    .fillMaxSize()
                    .height(HEADER_IMAGE_HEIGHT),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.74f to Color.Transparent,
                            1.00f to scrimColor.copy(alpha = HEADER_SCRIM_ALPHA),
                        ),
                    ),
                ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = tones.accent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = style.label.uppercase(),
                style = typeBadgeStyle,
                color = tones.accent,
            )
        }

        if (onOpenLink != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
internal fun GeneratedHeaderTile(
    style: CategoryStyle,
    tones: CardTones,
    domain: String,
    typeBadgeStyle: TextStyle,
    onOpenLink: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GENERATED_TILE_HEIGHT)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tones.accent.copy(alpha = 0.22f),
                        tones.container,
                        tones.accent.copy(alpha = 0.10f),
                    ),
                ),
            )
            .then(
                if (onOpenLink != null) {
                    Modifier.clickable(onClick = onOpenLink, onClickLabel = "Open link")
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = tones.accent.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = tones.accent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = style.label.uppercase(),
                style = typeBadgeStyle,
                color = tones.accent,
            )
        }
    }
}
