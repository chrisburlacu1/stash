package dev.cburlacu.stash.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Per-card color theming derived from a card's own header image.
 * Uses Google's Material Color Utilities (QuantizerCelebi + Score) to extract vibrant focal accents
 * in HCT color space, and Oklab with lightness normalization to generate accessible container and
 * text tones in both light and dark themes.
 */
object CardSeed {
    const val NONE = 0
}

@Immutable
data class CardTones(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

fun seedFromPixels(pixels: IntArray, width: Int = 0, height: Int = 0): Int =
    dev.cburlacu.stash.data.image.ImageAnalyzer.seedFromPixels(pixels, width, height)

fun cropBiasFromPixels(pixels: IntArray, width: Int, height: Int): Float =
    dev.cburlacu.stash.data.image.ImageAnalyzer.cropBiasFromPixels(pixels, width, height)

/**
 * Derives the theme-aware tones for a seed color, ensuring accessible contrast between
 * container and text/accent elements in both light and dark themes.
 */
fun cardTones(
    seedArgb: Int,
    dark: Boolean,
    fallbackSeed: Color = BRAND_SEED,
): CardTones {
    val seed = if (seedArgb == CardSeed.NONE) fallbackSeed else Color(seedArgb)
    val lab = seed.convert(ColorSpaces.Oklab)

    val rawChroma = sqrt(lab.component2() * lab.component2() + lab.component3() * lab.component3())
    val (a, b) = if (rawChroma < 1e-4f) {
        0f to 0f
    } else {
        val scale = REFERENCE_CHROMA / rawChroma
        (lab.component2() * scale) to (lab.component3() * scale)
    }

    val container = oklab(
        l = if (dark) 0.245f else 0.972f,
        a = a * if (dark) 0.42f else 0.22f,
        b = b * if (dark) 0.42f else 0.22f,
    )
    val accent = oklab(
        l = if (dark) 0.80f else 0.52f,
        a = a * if (dark) 0.85f else 1.0f,
        b = b * if (dark) 0.85f else 1.0f,
    )
    val onContainer = oklab(
        l = if (dark) 0.94f else 0.18f,
        a = a * 0.25f,
        b = b * 0.25f,
    )

    return CardTones(
        container = container,
        onContainer = ensureContrast(onContainer, container, MIN_TITLE_CONTRAST, dark),
        accent = ensureContrast(accent, container, MIN_ACCENT_CONTRAST, dark),
    )
}

private fun oklab(l: Float, a: Float, b: Float): Color = Color(
    red = l.coerceIn(0f, 1f),
    green = a.coerceIn(-0.5f, 0.5f),
    blue = b.coerceIn(-0.5f, 0.5f),
    alpha = 1f,
    colorSpace = ColorSpaces.Oklab,
).convert(ColorSpaces.Srgb)

private fun ensureContrast(color: Color, background: Color, minRatio: Float, dark: Boolean): Color {
    if (contrastRatio(color, background) >= minRatio) return color

    val lab = color.convert(ColorSpaces.Oklab)
    val a = lab.component2()
    val b = lab.component3()
    val direction = if (dark) 1f else -1f
    var l = lab.component1()

    repeat(CONTRAST_STEPS) {
        l = (l + direction * CONTRAST_STEP_SIZE).coerceIn(0f, 1f)
        val candidate = oklab(l, a, b)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    return if (dark) Color.White else Color.Black
}

fun contrastRatio(foreground: Color, background: Color): Float {
    val f = foreground.luminance()
    val b = background.luminance()
    return (max(f, b) + 0.05f) / (min(f, b) + 0.05f)
}

const val MIN_TITLE_CONTRAST = 4.5f
const val MIN_ACCENT_CONTRAST = 3f

private const val REFERENCE_CHROMA = 0.16f
private val BRAND_SEED = Color(0xFFE7418F)
private const val CROP_BANDS = 6
private const val CROP_TEXT_DOMINANCE = 1.55f
private const val CROP_BIAS_STRENGTH = 0.6f
private const val CONTRAST_STEPS = 40
private const val CONTRAST_STEP_SIZE = 0.02f
