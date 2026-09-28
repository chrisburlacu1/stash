package dev.cburlacu.stash.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An animated blurred edge light using theme accent colors that rotates smoothly
 * around the card perimeter while summarizing.
 */
@Composable
fun CardEdgeBlurEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 8.dp,
    blurRadius: Dp = 8.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseColors = listOf(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.primaryContainer,
        colorScheme.tertiaryContainer,
    )

    val infiniteTransition = rememberInfiniteTransition(label = "CardEdgeBlurEffect")

    // Full 360-degree perimeter phase rotation so all 5 colors travel evenly around every edge
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edgePhase",
    )

    // Breathing luminescence pulse
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "edgePulse",
    )

    // Tight breathing blur expansion
    val blurPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "edgeBlurPulse",
    )

    val density = LocalDensity.current
    val baseBlurPx = with(density) { blurRadius.toPx() }
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .graphicsLayer {
                val currentBlurPx = (baseBlurPx * blurPulse).coerceAtLeast(1f)
                renderEffect = BlurEffect(
                    radiusX = currentBlurPx,
                    radiusY = currentBlurPx,
                    edgeTreatment = TileMode.Clamp,
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Generate phase-rotated color stops for continuous, seamless rotation of all 5 hues
            val numStops = 10
            val rotatingColors = List(numStops + 1) { i ->
                val frac = (i.toFloat() / numStops + phase) % 1.0f
                val pos = frac * baseColors.size
                val i0 = pos.toInt() % baseColors.size
                val i1 = (i0 + 1) % baseColors.size
                val t = pos - pos.toInt()
                lerp(baseColors[i0], baseColors[i1], t).copy(alpha = 1.0f * pulse)
            }

            val sweepBrush = Brush.sweepGradient(
                colors = rotatingColors,
                center = Offset(w / 2f, h / 2f),
            )

            val currentStrokePx = strokeWidthPx * blurPulse

            // 1. Base falloff stroke
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = currentStrokePx),
            )

            // 2. Concentrated edge core for crisp, high-intensity rapid inward falloff
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = currentStrokePx * 0.5f),
            )
        }
    }
}

/**
 * Compact animated blur gradient for small accents like dialog headers, using category hues.
 */
@Composable
fun AnimatedBlurGradient(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 24.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseColors = listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        colorScheme.primaryContainer,
    )

    val infiniteTransition = rememberInfiniteTransition(label = "AnimatedBlurGradient")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blurPhase",
    )

    val density = LocalDensity.current
    val baseBlurPx = with(density) { blurRadius.toPx() }

    Box(
        modifier = modifier.graphicsLayer {
            renderEffect = BlurEffect(
                radiusX = baseBlurPx,
                radiusY = baseBlurPx,
                edgeTreatment = TileMode.Clamp,
            )
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val numStops = 8
            val rotatingColors = List(numStops) { i ->
                val frac = (i.toFloat() / numStops + phase) % 1.0f
                val pos = frac * baseColors.size
                val i0 = pos.toInt() % baseColors.size
                val i1 = (i0 + 1) % baseColors.size
                val t = pos - pos.toInt()
                lerp(baseColors[i0], baseColors[i1], t).copy(alpha = 0.85f)
            }

            drawRect(
                brush = Brush.linearGradient(
                    colors = rotatingColors,
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                )
            )
        }
    }
}
