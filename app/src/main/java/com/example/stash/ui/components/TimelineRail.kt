package com.example.stash.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/*  Model                                                              */
/* ------------------------------------------------------------------ */

data class TimelineEntry(
    val id: String,
    val label: String,
    /** 0 = date header (outer rail), 1 = entry (indented rail). Deeper levels work too. */
    val level: Int,
)

/** How the active row is chosen. */
enum class FocusMode {
    /** Row nearest a focal line in the viewport lights up as you scroll. */
    Scroll,

    /** Row lights up on tap. */
    Tap,
}

/* ------------------------------------------------------------------ */
/*  Tuning                                                             */
/* ------------------------------------------------------------------ */

object TimelineRailDefaults {
    val startX: Dp = 20.dp          // x of a level-0 dot
    val indent: Dp = 16.dp          // x delta per level
    val thickness: Dp = 1.5.dp
    val dotRadius: Dp = 3.5.dp
    val activeDotRadius: Dp = 5.dp
    val glowRadius: Dp = 16.dp
    val contentStart: Dp = 48.dp    // left padding on rows so text clears the rail

    /**
     * Control-point reach as a fraction of the vertical gap, for the S-curve between
     * two different indent levels. 0.5 keeps the tangent perfectly vertical at both
     * dots — that verticality is what makes the bend read as smooth rather than kinked.
     */
    const val BEND = 0.5f

    /** Fraction of the incoming segment the trail covers when fully grown. */
    const val TRAIL_LENGTH = 0.7f
}

data class RailGeometry(
    val startX: Float,
    val indent: Float,
    val thickness: Float,
    val dotRadius: Float,
    val activeDotRadius: Float,
    val glowRadius: Float,
    val ringCorner: Float,
    val ringInset: Float,
)

data class TimelineNode(val index: Int, val x: Float, val y: Float)

/* ------------------------------------------------------------------ */
/*  Public composable                                                  */
/* ------------------------------------------------------------------ */

@Composable
fun Timeline(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
    focusMode: FocusMode = FocusMode.Scroll,
    accent: Color = Color(0xFFFF2E88),
    railColor: Color = Color.White.copy(alpha = 0.16f),
    dotColor: Color = Color.White.copy(alpha = 0.32f),
    ringColor: Color = Color.White.copy(alpha = 0.85f),
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
) {
    val listState = rememberLazyListState()
    var tapped by remember { mutableIntStateOf(0) }

    val scrollFocused by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf 0
            val focalY = (info.viewportSize.height) * 0.33f
            visible.minByOrNull { abs((it.offset + it.size / 2f) - focalY) }!!.index
        }
    }

    val activeIndex = if (focusMode == FocusMode.Scroll) scrollFocused else tapped

    val animIndex = remember { Animatable(activeIndex.toFloat()) }
    LaunchedEffect(activeIndex) {
        animIndex.animateTo(
            targetValue = activeIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val trailGrowth = remember { Animatable(1f) }
    LaunchedEffect(activeIndex) {
        trailGrowth.snapTo(0f)
        trailGrowth.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }

    val density = LocalDensity.current
    val geom = remember(density) {
        with(density) {
            RailGeometry(
                startX = TimelineRailDefaults.startX.toPx(),
                indent = TimelineRailDefaults.indent.toPx(),
                thickness = TimelineRailDefaults.thickness.toPx(),
                dotRadius = TimelineRailDefaults.dotRadius.toPx(),
                activeDotRadius = TimelineRailDefaults.activeDotRadius.toPx(),
                glowRadius = TimelineRailDefaults.glowRadius.toPx(),
                ringCorner = 12.dp.toPx(),
                ringInset = 2.dp.toPx(),
            )
        }
    }

    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawTimelineRail(
                listState = listState,
                levelOf = { i -> entries.getOrNull(i)?.level ?: 0 },
                yOffsetOf = { _, offset, size -> offset + size / 2f },
                activeIndex = activeIndex,
                animIndex = animIndex.value,
                trailGrowth = trailGrowth.value,
                geom = geom,
                accent = accent,
                railColor = railColor,
                dotColor = dotColor,
                ringColor = ringColor,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(entries) { index, entry ->
                val isActive = index == activeIndex
                val labelColor by animateColorAsState(
                    targetValue = when {
                        isActive && entry.level == 0 -> accent
                        isActive -> Color.White
                        entry.level == 0 -> Color.White.copy(alpha = 0.45f)
                        else -> Color.White.copy(alpha = 0.62f)
                    },
                    animationSpec = tween(220),
                    label = "labelColor",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (entry.level == 0) 40.dp else 38.dp)
                        .then(
                            if (focusMode == FocusMode.Tap) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { tapped = index }
                            } else Modifier
                        )
                        .padding(start = TimelineRailDefaults.contentStart, end = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = entry.label,
                        color = labelColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Drawing Function                                                   */
/* ------------------------------------------------------------------ */

fun DrawScope.drawTimelineRail(
    listState: LazyListState,
    levelOf: (Int) -> Int,
    yOffsetOf: (index: Int, itemOffset: Int, itemSize: Int) -> Float,
    activeIndex: Int,
    previousIndex: Int = activeIndex - 1,
    animIndex: Float,
    trailGrowth: Float,
    geom: RailGeometry,
    accent: Color,
    railColor: Color,
    dotColor: Color,
    ringColor: Color,
    drawRing: Boolean = true,
) {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return

    val nodes = visible.mapNotNull { item ->
        val level = levelOf(item.index)
        if (level < 0) null
        else TimelineNode(
            index = item.index,
            x = geom.startX + level * geom.indent,
            y = yOffsetOf(item.index, item.offset, item.size),
        )
    }

    if (nodes.isEmpty()) return

    val stroke = Stroke(width = geom.thickness, cap = StrokeCap.Round)

    // --- 1. the rail itself, one path per gap ------------------------------
    val segments = ArrayList<Path>(nodes.size)
    for (i in 1 until nodes.size) {
        val a = nodes[i - 1]
        val b = nodes[i]
        val p = Path().apply {
            moveTo(a.x, a.y)
            if (abs(a.x - b.x) < 0.5f) {
                lineTo(b.x, b.y)
            } else {
                val dy = b.y - a.y
                cubicTo(
                    a.x, a.y + dy * TimelineRailDefaults.BEND,
                    b.x, b.y - dy * TimelineRailDefaults.BEND,
                    b.x, b.y,
                )
            }
        }
        segments += p
        drawPath(p, color = railColor, style = stroke)
    }

    // Stub above the first visible dot and below the last
    nodes.firstOrNull()?.let { drawLine(railColor, Offset(it.x, 0f), Offset(it.x, it.y), geom.thickness) }
    nodes.lastOrNull()?.let { drawLine(railColor, Offset(it.x, it.y), Offset(it.x, size.height), geom.thickness) }

    // --- 2. the gradient beam traveling to the active dot & settled tail ---
    val activePos = nodes.indexOfFirst { it.index == activeIndex }
    if (activePos >= 0 && segments.isNotEmpty()) {
        val isScrollingDown = activeIndex >= previousIndex
        if (isScrollingDown && activePos > 0 && activePos - 1 < segments.size) {
            val seg = segments[activePos - 1]
            val measure = PathMeasure().apply { setPath(seg, false) }
            val len = measure.length
            val maxTailLen = (len * 0.35f).coerceAtMost(52.dp.toPx())

            val head = len * trailGrowth
            val currentTailLen = maxTailLen * trailGrowth
            val tail = (head - currentTailLen).coerceAtLeast(0f)

            val trail = Path()
            if (measure.getSegment(tail, head, trail, true)) {
                val a = nodes[activePos - 1]
                val b = nodes[activePos]
                drawPath(
                    path = trail,
                    brush = Brush.verticalGradient(
                        0f to accent.copy(alpha = 0f),
                        0.45f to accent.copy(alpha = 0.55f),
                        1f to accent,
                        startY = a.y + (b.y - a.y) * (tail / len),
                        endY = a.y + (b.y - a.y) * (head / len),
                    ),
                    style = stroke,
                )
            }
        } else if (!isScrollingDown && activePos < segments.size) {
            val seg = segments[activePos]
            val measure = PathMeasure().apply { setPath(seg, false) }
            val len = measure.length
            val maxTailLen = (len * 0.35f).coerceAtMost(52.dp.toPx())

            val head = len * (1f - trailGrowth)
            val currentTailLen = maxTailLen * trailGrowth
            val tail = (head + currentTailLen).coerceAtMost(len)

            val trail = Path()
            if (measure.getSegment(head, tail, trail, true)) {
                val a = nodes[activePos]
                val b = nodes[activePos + 1]
                drawPath(
                    path = trail,
                    brush = Brush.verticalGradient(
                        0f to accent,
                        0.55f to accent.copy(alpha = 0.55f),
                        1f to accent.copy(alpha = 0f),
                        startY = a.y + (b.y - a.y) * (head / len),
                        endY = a.y + (b.y - a.y) * (tail / len),
                    ),
                    style = stroke,
                )
            }
        }
    }

    // --- 3. dots -----------------------------------------------------------
    nodes.forEach { node ->
        val isActive = node.index == activeIndex
        if (isActive) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = 0.45f),
                    0.5f to accent.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                    center = Offset(node.x, node.y),
                    radius = geom.glowRadius,
                ),
                radius = geom.glowRadius,
                center = Offset(node.x, node.y),
            )
            drawCircle(accent, geom.activeDotRadius, Offset(node.x, node.y))
        } else {
            drawCircle(dotColor, geom.dotRadius, Offset(node.x, node.y))
        }
    }

    // --- 4. the outline ring -----------------------------------------------
    if (drawRing) {
        val lo = floor(animIndex).toInt()
        val f = animIndex - lo
        val loItem = visible.firstOrNull { it.index == lo }
        val hiItem = visible.firstOrNull { it.index == lo + 1 }
        val top: Float?
        val height: Float?
        when {
            loItem != null && hiItem != null -> {
                top = loItem.offset + (hiItem.offset - loItem.offset) * f
                height = loItem.size + (hiItem.size - loItem.size) * f
            }
            loItem != null -> {
                top = loItem.offset.toFloat(); height = loItem.size.toFloat()
            }
            hiItem != null -> {
                top = hiItem.offset.toFloat(); height = hiItem.size.toFloat()
            }
            else -> {
                val near = visible.minByOrNull { abs(it.index - animIndex.roundToInt()) }
                top = near?.offset?.toFloat(); height = near?.size?.toFloat()
            }
        }
        if (top != null && height != null) {
            drawRoundRect(
                color = ringColor,
                topLeft = Offset(geom.ringInset, top + geom.ringInset),
                size = Size(size.width - geom.ringInset * 2, height - geom.ringInset * 2),
                cornerRadius = CornerRadius(geom.ringCorner),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Helper                                                             */
/* ------------------------------------------------------------------ */

private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<TimelineEntry>,
    crossinline content: @Composable (Int, TimelineEntry) -> Unit,
) = items(count = items.size, key = { items[it].id }) { i -> content(i, items[i]) }