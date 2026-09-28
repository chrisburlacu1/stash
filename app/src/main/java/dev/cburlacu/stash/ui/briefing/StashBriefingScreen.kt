package dev.cburlacu.stash.ui.briefing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.components.RailGeometry
import dev.cburlacu.stash.ui.components.TimelineRailDefaults
import dev.cburlacu.stash.ui.components.drawTimelineRail
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashBriefingScreen(
    viewModel: StashBriefingViewModel,
    onBack: () -> Unit,
    onOpenItem: (StashItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    var showChatSheet by rememberSaveable { mutableStateOf(false) }

    val screenTitle = remember(state.topic, state.items.size) {
        when {
            !state.topic.isNullOrBlank() -> "Catch Up: ${state.topic}"
            state.items.isNotEmpty() -> "Topic Brief (${state.items.size} items)"
            else -> "Topic Briefing"
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopAppBar(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { }
                },
                title = {
                    Column {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${state.items.size} sources · On-device Gemini Nano",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.generateBriefing()
                        },
                        enabled = !state.isGeneratingBriefing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate briefing",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (state.briefingText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Stash Briefing", state.briefingText))
                                Toast.makeText(context, "Briefing copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy briefing",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showChatSheet = true
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Ask") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        BriefingTimelineContent(
            state = state,
            onOpenItem = onOpenItem,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (showChatSheet) {
        AskAboutBriefingSheet(
            state = state,
            onSend = viewModel::sendQuestion,
            onDismiss = { showChatSheet = false },
        )
    }
}

@Composable
private fun BriefingTimelineContent(
    state: BriefingUiState,
    onOpenItem: (StashItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val nodes = remember(state.briefingText) { parseBriefingToNodes(state.briefingText) }
    val prefixItemCount = if (state.items.isNotEmpty()) 3 else 0

    var previousActiveIndex by remember { mutableIntStateOf(prefixItemCount) }
    var userTargetIndex by remember { mutableStateOf<Int?>(null) }

    val scrollFocusedIndex by remember(listState, nodes, prefixItemCount) {
        derivedStateOf {
            if (nodes.isEmpty()) return@derivedStateOf prefixItemCount
            val firstNode = prefixItemCount
            val lastNode = prefixItemCount + nodes.size - 1

            if (listState.firstVisibleItemIndex < firstNode) {
                return@derivedStateOf firstNode
            }

            if (!listState.canScrollForward) {
                return@derivedStateOf lastNode
            }

            val currentIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val info = listState.layoutInfo
            val currentItem = info.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            val itemSize = currentItem?.size ?: 300

            val nodeOffset = currentIndex - firstNode
            when {
                nodeOffset < 0 -> firstNode
                nodeOffset < nodes.size - 1 -> {
                    if (offset > itemSize * 0.38f) currentIndex + 1 else currentIndex
                }
                else -> lastNode
            }
        }
    }

    val activeIndex = userTargetIndex ?: scrollFocusedIndex

    val animIndex = remember { Animatable(activeIndex.toFloat()) }
    LaunchedEffect(activeIndex) {
        animIndex.animateTo(
            targetValue = activeIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val trailGrowth = remember { Animatable(1f) }
    LaunchedEffect(activeIndex) {
        if (activeIndex != previousActiveIndex) {
            trailGrowth.snapTo(0f)
            trailGrowth.animateTo(1f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
            previousActiveIndex = activeIndex
        }
    }

    val geom = remember(density) {
        with(density) {
            RailGeometry(
                startX = TimelineRailDefaults.startX.toPx(),
                indent = TimelineRailDefaults.indent.toPx(),
                thickness = TimelineRailDefaults.thickness.toPx(),
                dotRadius = TimelineRailDefaults.dotRadius.toPx(),
                activeDotRadius = TimelineRailDefaults.activeDotRadius.toPx(),
                glowRadius = TimelineRailDefaults.glowRadius.toPx(),
                ringCorner = 14.dp.toPx(),
                ringInset = 4.dp.toPx(),
            )
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(modifier = modifier.fillMaxSize()) {
        if (nodes.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimelineRail(
                    listState = listState,
                    levelOf = { index ->
                        if (index < prefixItemCount) -1
                        else nodes.getOrNull(index - prefixItemCount)?.level ?: -1
                    },
                    yOffsetOf = { _, offset, _ -> offset + 22.dp.toPx() },
                    activeIndex = activeIndex,
                    previousIndex = previousActiveIndex,
                    animIndex = animIndex.value,
                    trailGrowth = trailGrowth.value,
                    geom = geom,
                    accent = accent,
                    railColor = railColor,
                    dotColor = dotColor,
                    ringColor = ringColor,
                    drawRing = false,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 360.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.items.isNotEmpty()) {
                item(key = "source_items_header") {
                    Text(
                        text = "SOURCES INCLUDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }

                item(key = "source_items_carousel") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(state.items, key = StashItem::id) { item ->
                            SourceItemMiniCard(
                                item = item,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenItem(item)
                                },
                            )
                        }
                    }
                }

                item(key = "divider") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            if (nodes.isEmpty() && state.isGeneratingBriefing) {
                item(key = "generating_placeholder") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Synthesizing briefing with on-device AI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            nodes.forEachIndexed { nodeIndex, node ->
                val listIndex = prefixItemCount + nodeIndex
                val isFocused = (listIndex == activeIndex)

                item(key = node.id) {
                    BriefingNodeRow(
                        node = node,
                        isFocused = isFocused,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                userTargetIndex = listIndex
                                scope.launch {
                                    val visible = listState.layoutInfo.visibleItemsInfo
                                    val targetItem = visible.firstOrNull { it.index == listIndex }
                                    val targetOffsetPx = with(density) { 16.dp.toPx() }
                                    if (targetItem != null) {
                                        val delta = targetItem.offset.toFloat() - targetOffsetPx
                                        listState.animateScrollBy(
                                            value = delta,
                                            animationSpec = tween(
                                                durationMillis = 650,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    } else {
                                        listState.animateScrollToItem(
                                            index = listIndex,
                                            scrollOffset = -targetOffsetPx.toInt(),
                                        )
                                    }
                                    userTargetIndex = null
                                }
                            },
                    )
                }
            }
        }
    }
}


