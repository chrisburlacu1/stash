package com.example.stash.ui.components

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

/**
 * Google's Gemini spark, as an [ImageVector].
 *
 * A vector rather than a bundled PNG so it stays crisp at any size, and because the mark is four
 * fills of one shape — a blue base with three linear gradients fading out over it, which is what
 * gives the spark its colour shift. Rasterising that would throw away the gradients' precision at
 * the 24dp this draws at.
 *
 * Drawn in the brand's own colours and never tinted: this identifies a specific third-party app, so
 * it is deliberately the one icon in the app that ignores the category palette. Recolouring a
 * company's mark to match a surface is both wrong visually and wrong as trademark use.
 *
 * Note this bundles Google's trademark. Fine for personal and internal builds; check Google's brand
 * guidelines before shipping publicly, since permitted use is narrower than "it identifies Gemini".
 */
val GeminiMark: ImageVector
    @Composable
    get() = remember { buildGeminiMark() }

/**
 * The spark's outline, shared by all four fills.
 *
 * Transcribed from the official 24x24 SVG. Every layer uses this identical path — the mark's colour
 * comes entirely from what is painted through it, not from separate shapes — so it is built once
 * and replayed rather than repeated four times.
 */
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
    // Layer order matters and matches the source SVG: a solid blue base, then three gradients each
    // fading to transparent, which tint one region of the spark without hiding the blue elsewhere.
    geminiSparkPath(SolidColor(GeminiBlue))

    // Green, lower-left.
    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiGreen,
            1f to Color.Transparent,
            start = Offset(7f, 15.5f),
            end = Offset(11f, 12f),
        ),
    )

    // Red, upper-left.
    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiRed,
            1f to Color.Transparent,
            start = Offset(8f, 5.5f),
            end = Offset(11.5f, 11f),
        ),
    )

    // Amber, sweeping left to right. Its stop sits at 0.46 rather than 1.0, so the fade completes
    // partway across and the right arm of the spark stays blue.
    geminiSparkPath(
        Brush.linearGradient(
            0f to GeminiAmber,
            0.46f to Color.Transparent,
            start = Offset(3.5f, 13.5f),
            end = Offset(17.5f, 12f),
        ),
    )
}.build()

// Brand colours, taken from the official asset. Not theme tokens on purpose — see the note above.
private val GeminiBlue = Color(0xFF3186FF)
private val GeminiGreen = Color(0xFF08B962)
private val GeminiRed = Color(0xFFF94543)
private val GeminiAmber = Color(0xFFFABC12)
