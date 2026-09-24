package com.example.stash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stash.ui.theme.StashTheme

/**
 * Stash brand logo rendering the 3-tier vault pills and Gemini spark.
 *
 * Drawn dynamically using the theme's [MaterialTheme.colorScheme.primary] and [MaterialTheme.colorScheme.onPrimary]
 * so it automatically responds to Android wallpaper dynamic colors (Material You) and Light/Dark theme.
 */
@Composable
fun StashLogo(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    sparkColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Canvas(modifier = modifier) {
        val baseWidth = 47f
        val baseHeight = 43f
        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
        val drawnWidth = baseWidth * scale
        val drawnHeight = baseHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f

        val pillW = 38f * scale
        val pillH = 12.5f * scale
        val cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)

        // Bottom Pill (45% alpha for solid left visual anchor)
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.45f),
            topLeft = Offset(offsetX, offsetY + 30.5f * scale),
            size = Size(pillW, pillH),
            cornerRadius = cornerRadius,
        )

        // Middle Pill (70% alpha)
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.70f),
            topLeft = Offset(offsetX + 4f * scale, offsetY + 15.25f * scale),
            size = Size(pillW, pillH),
            cornerRadius = cornerRadius,
        )

        // Top Pill (100% alpha active vault)
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(offsetX + 9f * scale, offsetY),
            size = Size(pillW, pillH),
            cornerRadius = cornerRadius,
        )

        // Gemini Spark
        val sparkPath = Path().apply {
            moveTo(offsetX + 37.5f * scale, offsetY + 2.65f * scale)
            cubicTo(
                offsetX + 37.9f * scale, offsetY + 4.95f * scale,
                offsetX + 38.8f * scale, offsetY + 5.85f * scale,
                offsetX + 41.1f * scale, offsetY + 6.25f * scale
            )
            cubicTo(
                offsetX + 38.8f * scale, offsetY + 6.65f * scale,
                offsetX + 37.9f * scale, offsetY + 7.55f * scale,
                offsetX + 37.5f * scale, offsetY + 9.85f * scale
            )
            cubicTo(
                offsetX + 37.1f * scale, offsetY + 7.55f * scale,
                offsetX + 36.2f * scale, offsetY + 6.65f * scale,
                offsetX + 33.9f * scale, offsetY + 6.25f * scale
            )
            cubicTo(
                offsetX + 36.2f * scale, offsetY + 5.85f * scale,
                offsetX + 37.1f * scale, offsetY + 4.95f * scale,
                offsetX + 37.5f * scale, offsetY + 2.65f * scale
            )
            close()
        }
        drawPath(sparkPath, color = sparkColor)
    }
}

@Preview(name = "Stash Logo - Light", showBackground = true)
@Preview(name = "Stash Logo - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StashLogoPreview() {
    StashTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            StashLogo(
                modifier = Modifier.size(width = 36.dp, height = 33.dp),
            )
        }
    }
}
