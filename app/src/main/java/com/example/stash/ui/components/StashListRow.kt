package com.example.stash.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.theme.categoryStyle

/**
 * Shared-element scopes are nullable because this row is also rendered inside
 * ExpandedFullScreenContainedSearchBar, whose content lives in a Dialog — a separate window
 * from the SharedTransitionLayout. Applying a sharedElement across those hierarchies throws
 * "layouts are not part of the same hierarchy", so search results pass null and opt out.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StashListRow(
    item: StashItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleRead: (() -> Unit)? = null,
    nowMillis: Long = remember(item.id) { System.currentTimeMillis() },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val style = categoryStyle(item.category, isSystemInDarkTheme())
    val isSummarizing = item.aiState == AiState.Summarizing
    // Read rows keep full contrast; the state is carried by a label in the meta row instead of
    // dimming, which made read items look disabled rather than done.
    val contentAlpha = 1f

    val dotModifier = sharedModifier(sharedTransitionScope, animatedVisibilityScope, "category-dot-${item.id}")
    val titleModifier = sharedModifier(sharedTransitionScope, animatedVisibilityScope, "title-${item.id}", bounds = true)
    val domainModifier = sharedModifier(sharedTransitionScope, animatedVisibilityScope, "domain-${item.id}")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            // Symmetric insets: an asymmetric end padding left the trailing Read marker
            // sitting at a different offset than the text above it.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Category badge: color + icon both encode the type, so it reads without the label.
            Surface(
                color = style.container.copy(alpha = contentAlpha),
                shape = CircleShape,
                modifier = Modifier.then(dotModifier),
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = item.category,
                    tint = style.color.copy(alpha = contentAlpha),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }

            Spacer(Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.color.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier,
                )

                Spacer(Modifier.height(6.dp))

                // One line only: the AI-generated headline is written to fit, so this should
                // never ellipsize the way the full summary did.
                Text(
                    text = item.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))

                MetaRow(
                    item = item,
                    isSummarizing = isSummarizing,
                    nowMillis = nowMillis,
                    accent = style.color,
                    domainModifier = domainModifier,
                    isRead = item.isRead,
                    onToggleRead = onToggleRead,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MetaRow(
    item: StashItem,
    isSummarizing: Boolean,
    nowMillis: Long,
    accent: androidx.compose.ui.graphics.Color,
    domainModifier: Modifier,
    isRead: Boolean,
    onToggleRead: (() -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The meta text takes the leftover width so the Read marker keeps a fixed right-hand
        // column across every row; the tag ellipsizes inside here rather than pushing on it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (isSummarizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = accent,
                )
                Text(
                    text = "Summarizing…",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                return@Row
            }

            Text(
                text = item.domain,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = domainModifier,
            )
            MetaDot(muted)
            Text(
                text = relativeSavedLabel(item.savedAtEpochMillis, nowMillis),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )

            // Leading tag only: the AI returns them roughly most-relevant first, and one is
            // enough to hint at topic without turning the row back into a wall of text.
            item.tags.firstOrNull()?.let { tag ->
                MetaDot(muted)
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Only shown once read: an unread row needs no marker, and rendering a greyed-out one
        // made every item look like it had a disabled control.
        if (isRead && onToggleRead != null) {
            Spacer(Modifier.width(8.dp))
            // offset cancels ReadLabel's own touch-target padding so its text sits flush with
            // the row's 16dp inset rather than 6dp short of it.
            ReadLabel(
                accent = accent,
                onClick = onToggleRead,
                modifier = Modifier.offset(x = 6.dp),
            )
        }
    }
}

/** Quiet "Read" marker; tapping it returns the item to unread. */
@Composable
private fun ReadLabel(
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Mark as unread",
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Read",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetaDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(2.5.dp)
            .background(color.copy(alpha = 0.6f), CircleShape)
    )
}

/** Returns the shared-element modifier for [key], or an empty Modifier when scopes are absent. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun sharedModifier(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
    bounds: Boolean = false,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return Modifier
    return with(sharedTransitionScope) {
        val contentState = rememberSharedContentState(key = key)
        if (bounds) {
            Modifier.sharedBounds(contentState, animatedVisibilityScope = animatedVisibilityScope)
        } else {
            Modifier.sharedElement(contentState, animatedVisibilityScope = animatedVisibilityScope)
        }
    }
}
