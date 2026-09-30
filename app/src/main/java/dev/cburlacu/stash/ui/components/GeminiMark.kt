package dev.cburlacu.stash.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GeminiMark: ImageVector
    @Composable
    get() = remember { buildGeminiMark() }

private fun ImageVector.Builder.geminiSparkPath(
    brush: Brush,
) {
    path(fill = brush, pathFillType = PathFillType.NonZero) {
        moveTo(20.616f, 10.835f)
        curveToRelative(-1.654f, -0.716f, -3.148f, -1.729f, -4.45f, -3.001f)
        curveToRelative(-1.696f, -1.827f, -2.943f, -4.017f, -3.678f, -6.452f)
        arcToRelative(0.503f, 0.503f, 0f, false, false, -0.975f, 0f)
        curveToRelative(-0.735f, 2.435f, -1.983f, 4.625f, -3.679f, 6.452f)
        curveToRelative(-1.302f, 1.272f, -2.796f, 2.285f, -4.45f, 3.001f)
        curveToRelative(-0.65f, 0.28f, -1.318f, 0.505f, -2.002f, 0.678f)
        arcToRelative(0.502f, 0.502f, 0f, false, false, 0f, 0.975f)
        curveToRelative(0.684f, 0.172f, 1.35f, 0.397f, 2.002f, 0.677f)
        curveToRelative(1.654f, 0.716f, 3.148f, 1.729f, 4.45f, 3.001f)
        curveToRelative(1.696f, 1.827f, 2.944f, 4.018f, 3.679f, 6.453f)
        arcToRelative(0.502f, 0.502f, 0f, false, false, 0.975f, 0f)
        curveToRelative(0.172f, -0.685f, 0.397f, -1.351f, 0.677f, -2.003f)
        curveToRelative(0.716f, -1.654f, 1.729f, -3.148f, 3.001f, -4.45f)
        curveToRelative(1.827f, -1.696f, 4.018f, -2.943f, 6.453f, -3.678f)
        arcToRelative(0.503f, 0.503f, 0f, false, false, 0f, -0.975f)
        arcToRelative(13.245f, 13.245f, 0f, false, true, -2.003f, -0.678f)
        close()
    }
}

private fun buildGeminiMark(): ImageVector = ImageVector.Builder(
    name = "GeminiMark",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    geminiSparkPath(SolidColor(GeminiBlue))

    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiGreen,
            1f to Color.Transparent,
            start = Offset(7f, 15.5f),
            end = Offset(11f, 12f),
        ),
    )

    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiRed,
            1f to Color.Transparent,
            start = Offset(8f, 5.5f),
            end = Offset(11.5f, 11f),
        ),
    )

    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiAmber,
            0.46f to Color.Transparent,
            start = Offset(3.5f, 13.5f),
            end = Offset(17.5f, 12f),
        ),
    )
}.build()

private val GeminiBlue = Color(0xFF3186FF)
private val GeminiGreen = Color(0xFF08B962)
private val GeminiRed = Color(0xFFF94543)
private val GeminiAmber = Color(0xFFFABC12)
