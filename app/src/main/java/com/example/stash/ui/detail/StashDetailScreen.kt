package com.example.stash.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stash.data.StashRepository
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.components.KeyPoints
import com.example.stash.ui.components.MetaDot
import com.example.stash.ui.components.TagChip
import com.example.stash.ui.theme.categoryStyle
import com.example.stash.ui.util.ImageBitmapCache
import kotlinx.coroutines.launch

/**
 * Dedicated Material 3 Detail Screen for a saved Stash item.
 *
 * Implements M3 Container Transform from feed card to full view,
 * clean focused top bar, refined title scaling, and concise AI key points briefing.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun StashDetailScreen(
    itemId: String,
    repository: StashRepository,
    onBack: (() -> Unit)?,
    onOpenChat: (StashItem) -> Unit,
    initialItem: StashItem? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val item by repository.observeItem(itemId).collectAsState(initial = initialItem)
    val currentItem = item

    if (currentItem == null) {
        StashDetailPlaceholder(modifier = modifier)
        return
    }

    LaunchedEffect(currentItem.id) {
        if (!currentItem.isRead) {
            repository.setRead(currentItem.id, true)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = categoryStyle(currentItem.category, darkTheme)
    val nowMillis = remember(currentItem.id) { System.currentTimeMillis() }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val keyPoints = remember(currentItem.summary) {
        currentItem.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
    }

    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.geometry.Rect>()

    val cardBoundsModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card-${currentItem.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spatialSpec },
                clipInOverlayDuringTransition = OverlayClip(androidx.compose.ui.graphics.RectangleShape),
            )
        }
    } else Modifier

    Surface(
        modifier = modifier
            .fillMaxSize()
            .then(cardBoundsModifier),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        // Primary clean action: Open original article in browser
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.url))
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in browser",
                            )
                        }

                        // Overflow menu for secondary actions (Read toggle, Delete)
                        Box {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMenu = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (currentItem.isRead) "Mark unread" else "Mark read") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (currentItem.isRead) Icons.Outlined.CheckCircle else Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch { repository.setRead(currentItem.id, !currentItem.isRead) }
                                    },
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenChat(currentItem)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Ask on-device AI") },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header image if available
                if (!currentItem.imagePath.isNullOrBlank()) {
                    DetailHeaderImage(
                        path = currentItem.imagePath,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    // Category & Metadata Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(style.color.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = style.icon,
                                contentDescription = null,
                                tint = style.color,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = style.color,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        MetaDot()

                        Text(
                            text = relativeSavedLabel(currentItem.savedAtEpochMillis, nowMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = style.color,
                            fontWeight = FontWeight.Medium,
                        )

                        MetaDot()

                        Text(
                            text = currentItem.domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = style.color,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Scaled editorial title
                    Text(
                        text = currentItem.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 26.sp,
                    )

                    // AI Key Points Briefing Card
                    if (keyPoints.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Key Points",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(12.dp))
                                KeyPoints(points = keyPoints, accent = style.color)
                            }
                        }
                    }

                    // Topic Tags
                    if (currentItem.tags.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            currentItem.tags.forEach { tag ->
                                TagChip(tag = tag)
                            }
                        }
                    }

                    // Padding to clear FAB
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from Stash?") },
            text = { Text("\"${currentItem.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = false
                        scope.launch {
                            repository.delete(currentItem.id)
                            onBack?.invoke()
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailHeaderImage(
    path: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = ImageBitmapCache.get(path),
        key1 = path,
    ) {
        value = ImageBitmapCache.load(path)
    }

    bitmap?.let { img ->
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
        )
    }
}
