package dev.cburlacu.stash.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import kotlin.math.abs
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

/**
 * Extracts a dominant, vibrant seed color from ARGB [pixels] using Google's Material Color Utilities.
 * Insets the sampling bounds slightly away from outer borders (rejecting GitHub bottom language bars
 * and edge compression noise), filters out extreme canvas tones (tone < 12 or tone > 88) and
 * desaturated neutral sludge (chroma < 18.0 in HCT space).
 * Returns [CardSeed.NONE] if no color qualifies with sufficient chroma, allowing clean category fallback.
 */
fun seedFromPixels(pixels: IntArray, width: Int = 0, height: Int = 0): Int {
    if (pixels.isEmpty()) return CardSeed.NONE

    val sampled = if (width > 32 && height > 32 && pixels.size >= width * height) {
        val insetX = (width * 0.03).toInt().coerceAtLeast(1)
        val insetTop = (height * 0.03).toInt().coerceAtLeast(1)
        val insetBottom = (height * 0.06).toInt().coerceAtLeast(2)
        val endY = (height - insetBottom).coerceAtLeast(insetTop + 1)
        val endX = width - insetX
        val count = (endX - insetX) * (endY - insetTop)
        val buffer = IntArray(count)
        var idx = 0
        for (y in insetTop until endY) {
            val rowOffset = y * width
            for (x in insetX until endX) {
                val p = pixels[rowOffset + x]
                if (((p ushr 24) and 0xFF) >= 128) {
                    buffer[idx++] = p
                }
            }
        }
        if (idx == 0) return CardSeed.NONE
        if (idx == buffer.size) buffer else buffer.copyOf(idx)
    } else {
        var opaqueCount = 0
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) >= 128) opaqueCount++
        }
        if (opaqueCount == 0) return CardSeed.NONE
        if (opaqueCount == pixels.size) {
            pixels
        } else {
            val opaque = IntArray(opaqueCount)
            var idx = 0
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) >= 128) opaque[idx++] = p
            }
            opaque
        }
    }
    if (sampled.isEmpty()) return CardSeed.NONE

    val quantized = QuantizerCelebi.quantize(sampled, 128)
    if (quantized.isEmpty()) return CardSeed.NONE

    // Filter out extreme lightness/darkness backgrounds (e.g. #080C16 slate or #FFFFFF canvas)
    // and desaturated neutral sludge (chroma < 18.0 in HCT space).
    val filtered = quantized.filter { (argb, _) ->
        val hct = Hct.fromInt(argb)
        hct.tone in 12.0..88.0 && hct.chroma >= 18.0
    }

    // Require at least 0.4% chromatic pixel mass across the sampled image to prevent tiny stray artifacts
    val chromaticPixels = filtered.values.sum()
    if (chromaticPixels < sampled.size * 0.004) return CardSeed.NONE

    val ranked = Score.score(filtered, 4, CardSeed.NONE, true)
    return ranked.firstOrNull { it != CardSeed.NONE } ?: CardSeed.NONE
}

/**
 * Finds the horizontal band holding an image's headline text and returns a vertical bias
 * framing away from it: -1 (top), 0 (center), +1 (bottom). Feeds [androidx.compose.ui.BiasAlignment].
 */
fun cropBiasFromPixels(pixels: IntArray, width: Int, height: Int): Float {
    if (width < 8 || height < 8 || pixels.size < width * height) return 0f
    val bandHeight = height / CROP_BANDS
    if (bandHeight < 2) return 0f

    val bands = FloatArray(CROP_BANDS)
    for (band in 0 until CROP_BANDS) {
        val yStart = band * bandHeight
        val yEnd = if (band == CROP_BANDS - 1) height else (band + 1) * bandHeight
        var total = 0L
        var samples = 0
        for (y in yStart until yEnd) {
            val row = y * width
            for (x in 1 until width) {
                val a = (pixels[row + x] shr 8) and 0xFF
                val b = (pixels[row + x - 1] shr 8) and 0xFF
                total += abs(a - b).toLong()
                samples++
            }
        }
        bands[band] = if (samples == 0) 0f else total.toFloat() / samples
    }

    val noisiest = bands.indices.maxByOrNull { bands[it] } ?: return 0f
    val mean = bands.average().toFloat()
    if (mean <= 0f || bands[noisiest] < mean * CROP_TEXT_DOMINANCE) return 0f

    val bandCentre = (noisiest + 0.5f) / CROP_BANDS
    return ((0.5f - bandCentre) * 2f * CROP_BIAS_STRENGTH).coerceIn(-0.75f, 0.75f)
}

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
