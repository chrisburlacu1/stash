package com.example.stash.ui.briefing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.models.StashItem
import com.example.stash.ui.components.RailGeometry
import com.example.stash.ui.components.TimelineRailDefaults
import com.example.stash.ui.components.drawTimelineRail
import com.example.stash.ui.theme.categoryStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    // Deterministic scroll focal tracking: natural progression through all 4 sections
    val scrollFocusedIndex by remember(listState, nodes, prefixItemCount) {
        derivedStateOf {
            if (nodes.isEmpty()) return@derivedStateOf prefixItemCount
            val firstNode = prefixItemCount
            val lastNode = prefixItemCount + nodes.size - 1

            // 1. If at top of the scroll, first node is active
            if (listState.firstVisibleItemIndex < firstNode) {
                return@derivedStateOf firstNode
            }

            // 2. If scrolled to the bottom (can't scroll forward anymore), last node is active
            if (!listState.canScrollForward) {
                return@derivedStateOf lastNode
            }

            // 3. Threshold-based progression through sections
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

    // Animated drivers
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
        // Background Canvas: Connected animated rail lines & active glowing dot
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
                bottom = 360.dp, // Generous bottom scroll room so every section is fully reachable
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Source Items Section (level -1)
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

            // Generating initial placeholder
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

            // Timeline Nodes (The Big Picture, Key Takeaways, Comparisons & Trade-offs, The Bottom Line)
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

@Composable
private fun BriefingNodeRow(
    node: BriefingNode,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.45f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "nodeAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.985f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "nodeScale",
    )
    val eyebrowColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        animationSpec = tween(300),
        label = "eyebrowColor",
    )
    val bulletColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "bulletColor",
    )

    Column(
        modifier = modifier
            .padding(start = TimelineRailDefaults.contentStart + (TimelineRailDefaults.indent * node.level))
            .scale(scale)
            .alpha(alpha)
            .padding(vertical = 4.dp),
    ) {
        when (node) {
            is BriefingNode.BigPicture -> {
                Text(
                    text = "THE BIG PICTURE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                node.lines.forEach { line ->
                    Text(
                        text = parseMarkdownBold(line),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                    )
                }
            }

            is BriefingNode.KeyTakeaways -> {
                Text(
                    text = "KEY TAKEAWAYS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                node.lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    val isIndented = rawLine.startsWith("  ") || rawLine.startsWith("\t")

                    when {
                        line.isBlank() -> {}
                        line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") -> {
                            val bulletText = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isIndented) 12.dp else 0.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(if (isIndented) 3.5.dp else 4.5.dp)
                                        .clip(CircleShape)
                                        .background(bulletColor),
                                )
                                Text(
                                    text = parseMarkdownBold(bulletText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = parseMarkdownBold(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            is BriefingNode.Comparison -> {
                Text(
                    text = "COMPARISONS & TRADE-OFFS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                node.lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    val isIndented = rawLine.startsWith("  ") || rawLine.startsWith("\t")

                    when {
                        line.isBlank() -> {}
                        isSubheader(line) -> {
                            val title = cleanSubheaderText(line)
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") -> {
                            val bulletText = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isIndented) 12.dp else 0.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(if (isIndented) 3.5.dp else 4.5.dp)
                                        .clip(CircleShape)
                                        .background(bulletColor),
                                )
                                Text(
                                    text = parseMarkdownBold(bulletText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = parseMarkdownBold(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            is BriefingNode.BottomLine -> {
                Text(
                    text = "THE BOTTOM LINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                node.lines.forEach { line ->
                    Text(
                        text = parseMarkdownBold(line),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                    )
                }
            }
        }
    }
}

private sealed interface BriefingNode {
    val id: String
    val level: Int

    data class BigPicture(
        override val id: String = "node_big_picture",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class KeyTakeaways(
        override val id: String = "node_takeaways",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class Comparison(
        override val id: String = "node_comparison",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class BottomLine(
        override val id: String = "node_bottom_line",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode
}

private fun parseBriefingToNodes(rawText: String): List<BriefingNode> {
    if (rawText.isBlank()) return emptyList()

    val lines = rawText.lines().filterNot { line ->
        val trimmed = line.trim()
        trimmed.startsWith("Executive Briefing", ignoreCase = true) ||
            trimmed.startsWith("**Executive Briefing", ignoreCase = true) ||
            trimmed.startsWith("# Executive Briefing", ignoreCase = true)
    }

    val nodes = mutableListOf<BriefingNode>()
    val bigPictureLines = mutableListOf<String>()
    val takeawaysLines = mutableListOf<String>()
    val comparisonLines = mutableListOf<String>()
    val bottomLineLines = mutableListOf<String>()

    var currentSection = 0 // 0 = Big Picture, 1 = Takeaways, 2 = Comparison, 3 = Bottom Line

    for (rawLine in lines) {
        val trimmed = rawLine.trim()
        val cleaned = trimmed.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
        val lower = cleaned.lowercase()

        when {
            (lower.contains("big picture") || lower.contains("overview")) && cleaned.length <= 40 -> {
                currentSection = 0
            }
            (lower.contains("comparison") || lower.contains("trade-off") || lower.contains("tradeoffs") || lower.contains("vs ") || lower.contains("differences")) && cleaned.length <= 50 -> {
                currentSection = 2
            }
            (lower.contains("key takeaway") || lower.contains("takeaway") || lower.contains("takeaways") || lower.contains("key points") || lower.contains("core insights") || lower.contains("insights")) && cleaned.length <= 50 -> {
                currentSection = 1
            }
            (lower.contains("bottom line") || lower.contains("conclusion") || lower.contains("verdict") || lower.contains("recommendation")) && cleaned.length <= 40 -> {
                currentSection = 3
            }
            else -> {
                when (currentSection) {
                    0 -> if (trimmed.isNotBlank()) bigPictureLines.add(rawLine)
                    1 -> if (trimmed.isNotBlank()) takeawaysLines.add(rawLine)
                    2 -> if (trimmed.isNotBlank()) comparisonLines.add(rawLine)
                    3 -> if (trimmed.isNotBlank()) bottomLineLines.add(rawLine)
                }
            }
        }
    }

    if (bigPictureLines.isNotEmpty()) {
        nodes.add(BriefingNode.BigPicture(lines = bigPictureLines))
    }
    if (takeawaysLines.isNotEmpty()) {
        nodes.add(BriefingNode.KeyTakeaways(lines = takeawaysLines))
    }
    if (comparisonLines.isNotEmpty()) {
        nodes.add(BriefingNode.Comparison(lines = comparisonLines))
    }
    if (bottomLineLines.isNotEmpty()) {
        nodes.add(BriefingNode.BottomLine(lines = bottomLineLines))
    }

    return nodes
}

private fun isSubheader(line: String): Boolean {
    val cleaned = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
    if (cleaned.startsWith("#")) return true
    if (cleaned.startsWith("**") && (cleaned.endsWith("**:") || cleaned.endsWith(":**") || cleaned.endsWith("**:")) && cleaned.length <= 80) {
        return true
    }
    if (cleaned.endsWith(":") && cleaned.length <= 60 && !cleaned.contains(". ")) {
        return true
    }
    return false
}

private fun cleanSubheaderText(line: String): String {
    val cleaned = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
    return cleaned.trimStart('#', ' ')
        .removePrefix("**")
        .removeSuffix("**:")
        .removeSuffix(":**")
        .removeSuffix("**")
        .removeSuffix(":")
        .trim()
}

private fun parseMarkdownBold(text: String) = buildAnnotatedString {
    val regex = Regex("""\*\*(.*?)\*\*""")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
private fun SourceItemMiniCard(
    item: StashItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = categoryStyle(item.category, isDark)

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = style.color.copy(alpha = 0.35f),
        ),
        modifier = modifier.height(68.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(style.color.copy(alpha = 0.15f)),
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.color,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 180.dp),
            ) {
                Text(
                    text = item.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskAboutBriefingSheet(
    state: BriefingUiState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val canSend = !state.isResponding && text.isNotBlank()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Ask about this briefing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Conversation Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        ) {
                            Text(
                                text = "Ask questions or compare details across these sources.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    items(state.messages, key = BriefingMessage::id) { message ->
                        if (message.fromUser) {
                            BriefingUserBubble(text = message.text)
                        } else {
                            BriefingAssistantBubble(text = message.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Input Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Ask a question...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (canSend) {
                                        val toSend = text
                                        text = ""
                                        onSend(toSend)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (canSend) {
                            val toSend = text
                            text = ""
                            onSend(toSend)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send question",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefingUserBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun BriefingAssistantBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
