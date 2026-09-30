package dev.cburlacu.stash.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cburlacu.stash.ai.cleanTitle
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.models.relativeSavedLabel
import dev.cburlacu.stash.ui.components.MetaDot
import dev.cburlacu.stash.ui.components.TagChip
import dev.cburlacu.stash.ui.theme.cardTones
import dev.cburlacu.stash.ui.theme.categoryStyle
import dev.cburlacu.stash.ui.theme.contrastRatio
import dev.cburlacu.stash.ui.components.ImageBitmapCache
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive detail screen for a saved Stash item.
 *
 * Colors come from the item's own [cardTones], harmonizing with the feed card.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun StashDetailScreen(
    itemId: String,
    repository: StashRepository,
    onBack: (() -> Unit)?,
    onOpenChat: (StashItem) -> Unit,
    initialItem: StashItem? = null,
    modifier: Modifier = Modifier,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

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
    val style = categoryStyle(currentItem.category)
    val nowMillis = remember(currentItem.id) { System.currentTimeMillis() }

    val tones = remember(currentItem.seedColor, darkTheme) {
        cardTones(currentItem.seedColor, darkTheme)
    }

    val backgroundColor = tones.container
    val onBackgroundColor = tones.onContainer
    val mutedColor = tones.onContainer.copy(alpha = MUTED_ALPHA)
    val resolvedAccent = tones.accent
    val controlContainerColor = tones.onContainer.copy(alpha = CONTROL_FILL_ALPHA)

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val keyPoints = remember(currentItem.summary) {
        currentItem.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
    }

    val displayTitle = remember(currentItem.title, currentItem.url) {
        cleanTitle(currentItem.title, currentItem.url).ifBlank { currentItem.title }
    }

    val hasImage = !currentItem.imagePath.isNullOrBlank()

    val fabContainerColor = tones.accent
    val fabContentColor = remember(tones.accent) {
        if (contrastRatio(Color.White, tones.accent) >= contrastRatio(Color.Black, tones.accent)) {
            Color.White
        } else {
            Color.Black
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        contentColor = onBackgroundColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    DetailCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        container = controlContainerColor,
                        content = onBackgroundColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                    )
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailCircleButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in browser",
                        container = controlContainerColor,
                        content = onBackgroundColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.url))
                            runCatching { context.startActivity(intent) }
                        },
                    )

                    Box {
                        DetailCircleButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            container = controlContainerColor,
                            content = onBackgroundColor,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMenu = true
                            },
                        )

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
                }
            }
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
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = {
                    Text(
                        text = "Ask about this",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                shape = CircleShape,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (hasImage) {
                DetailHeaderImage(
                    path = currentItem.imagePath.orEmpty(),
                    cropBias = currentItem.cropBias,
                    modifier = Modifier.padding(horizontal = 10.dp)
                        .fillMaxWidth()
                        .height(HEADER_IMAGE_HEIGHT).clip(MaterialTheme.shapes.extraLarge),
                )
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    MetaDot()

                    Text(
                        text = relativeSavedLabel(currentItem.savedAtEpochMillis, nowMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        fontWeight = FontWeight.Medium,
                    )

                    MetaDot()

                    Text(
                        text = currentItem.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = resolvedAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.url))
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 25.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = onBackgroundColor,
                )

                if (currentItem.headline.isNotBlank() && currentItem.headline != displayTitle) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = currentItem.headline,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                        ),
                        color = mutedColor,
                    )
                }

                if (keyPoints.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = resolvedAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Key Points",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = resolvedAccent,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        keyPoints.forEachIndexed { index, point ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = resolvedAccent.copy(alpha = BADGE_FILL_ALPHA),
                                    contentColor = resolvedAccent,
                                    modifier = Modifier.size(52.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = String.format("%02d", index + 1),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = onBackgroundColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (currentItem.tags.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        currentItem.tags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                accent = resolvedAccent,
                                onContainer = mutedColor,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(96.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from Stash?") },
            text = { Text("\"${displayTitle}\" will be removed from this device.") },
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
    cropBias: Float,
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
            alignment = remember(cropBias) {
                BiasAlignment(horizontalBias = 0f, verticalBias = cropBias)
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun DetailCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val HEADER_IMAGE_HEIGHT = 220.dp
private const val MUTED_ALPHA = 0.75f
private const val CONTROL_FILL_ALPHA = 0.10f
private const val BADGE_FILL_ALPHA = 0.14f
