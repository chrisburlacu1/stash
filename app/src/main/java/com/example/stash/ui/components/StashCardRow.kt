package com.example.stash.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.theme.categoryStyle

/**
 * Card-style feed row, modelled on how Google Keep renders a saved link: the title dominates,
 * the summary sits under it, and a visually distinct strip at the bottom previews the link
 * itself.
 *
 * Sits alongside [StashListRow] as a user-selectable alternative rather than a replacement — the
 * choice is persisted in [com.example.stash.data.StashSettings] and toggled from the top bar.
 *
 * One departure from the Keep design: Keep shows the page's OpenGraph thumbnail in the bottom
 * strip. We store no images — extraction reads `og:title`/`og:description` only, and fetching
 * remote images would mean a network request per row, which cuts against the app's on-device
 * privacy stance. The category icon and hue stand in for it: same "what kind of thing is this"
 * signal, no network, and consistent with the colour language used elsewhere.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StashCardRow(
    item: StashItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleRead: (() -> Unit)? = null,
    onOpenLink: (() -> Unit)? = null,
    nowMillis: Long = remember(item.id) { System.currentTimeMillis() },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val style = categoryStyle(item.category, isSystemInDarkTheme())
    val isSummarizing = item.aiState == AiState.Summarizing

    val titleModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "title-${item.id}", bounds = true,
    )
    val domainModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "domain-${item.id}",
    )
    val dotModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "category-dot-${item.id}",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        onClick = onClick,
    ) {
        Column {
            // --- Title + summary ------------------------------------------------------------
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier,
                )

                // The headline is written to fit one line; fall back to nothing rather than
                // showing a clipped paragraph while summarizing.
                if (item.headline.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))
                CardMetaRow(
                    item = item,
                    isSummarizing = isSummarizing,
                    nowMillis = nowMillis,
                    accent = style.color,
                    onToggleRead = onToggleRead,
                )
            }

            // --- Link preview strip ---------------------------------------------------------
            // The tonal shift plus the icon tile is what makes this read as "the link" rather
            // than more body copy, which is the part of the Keep layout worth borrowing.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onOpenLink != null) Modifier.clickable(onClick = onOpenLink)
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    // Stands in for Keep's OG thumbnail — see the KDoc above.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .then(dotModifier)
                            .clip(MaterialTheme.shapes.small)
                            .background(style.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = item.category,
                            tint = style.color,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = style.color,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = domainModifier,
                        )
                    }

                    if (onOpenLink != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Saved-time, leading tag, and read state — the row's quiet metadata line. */
@Composable
private fun CardMetaRow(
    item: StashItem,
    isSummarizing: Boolean,
    nowMillis: Long,
    accent: Color,
    onToggleRead: (() -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
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
            text = relativeSavedLabel(item.savedAtEpochMillis, nowMillis),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )

        // Leading tag only: the AI returns them roughly most-relevant first, and one keeps the
        // line scannable.
        item.tags.firstOrNull()?.let { tag ->
            Box(
                modifier = Modifier
                    .size(2.5.dp)
                    .background(muted.copy(alpha = 0.6f), CircleShape),
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Spacer(Modifier.weight(1f))

        if (item.isRead && onToggleRead != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggleRead)
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
    }
}

/** Mirrors [StashListRow]'s shared-element handling: null scopes opt out entirely. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun cardSharedModifier(
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
