package com.example.stash.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Per-card color theming derived from a card's own header image.
 * Uses Oklab color space with lightness normalization to extract accurate hues from dark images.
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
 * Extracts a dominant color from ARGB [pixels], or [CardSeed.NONE] if nothing clears the saturation threshold.
 */
fun seedFromPixels(pixels: IntArray): Int {
    if (pixels.isEmpty()) return CardSeed.NONE

    val populations = HashMap<Int, Int>(512)
    for (pixel in pixels) {
        if ((pixel ushr 24) and 0xFF < 128) continue
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val lightness = (max(r, max(g, b)) + min(r, min(g, b))) / 510f
        if (lightness < MIN_PIXEL_LIGHTNESS || lightness > MAX_PIXEL_LIGHTNESS) continue

        val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        populations[bin] = (populations[bin] ?: 0) + 1
    }
    if (populations.isEmpty()) return CardSeed.NONE

    var bestBin = -1
    var bestScore = -1f
    for ((bin, population) in populations) {
        val r = (((bin shr 8) and 0xF) shl 4) or 0x8
        val g = (((bin shr 4) and 0xF) shl 4) or 0x8
        val b = ((bin and 0xF) shl 4) or 0x8
        val binColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val maxC = max(r, max(g, b))
        val saturation = if (maxC == 0) 0f else (maxC - min(r, min(g, b))).toFloat() / maxC
        if (saturation < MIN_BIN_SATURATION) continue

        val chroma = normalisedChromaOf(Color(binColor)).coerceIn(0f, 1f)
        val score = population * (0.30f + chroma * CHROMA_SCORE_WEIGHT)
        if (score > bestScore) {
            bestScore = score
            bestBin = binColor
        }
    }
    if (bestBin == -1) return CardSeed.NONE

    return if (normalisedChromaOf(Color(bestBin)) < MIN_SEED_CHROMA) CardSeed.NONE else bestBin
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
 * Derives the theme-aware tones for a seed color, ensuring accessible contrast against [surface].
 */
fun cardTones(seedArgb: Int, dark: Boolean, surface: Color): CardTones {
    val seed = if (seedArgb == CardSeed.NONE) BRAND_SEED else Color(seedArgb)
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

private fun normalisedChromaOf(color: Color): Float {
    val lab = color.convert(ColorSpaces.Oklab)
    val l = lab.component1()
    if (l <= 0.001f) return 0f
    val scale = CHROMA_PROBE_LIGHTNESS / l
    val a = lab.component2() * scale
    val b = lab.component3() * scale
    return sqrt(a * a + b * b)
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

private const val MIN_SEED_CHROMA = 0.045f
private const val MIN_BIN_SATURATION = 0.15f
private const val CHROMA_PROBE_LIGHTNESS = 0.62f
private const val CHROMA_SCORE_WEIGHT = 6.5f
private const val MIN_PIXEL_LIGHTNESS = 0.025f
private const val MAX_PIXEL_LIGHTNESS = 0.97f
private const val REFERENCE_CHROMA = 0.16f

private val BRAND_SEED = Color(0xFFE7418F)
private const val CROP_BANDS = 6
private const val CROP_TEXT_DOMINANCE = 1.55f
private const val CROP_BIAS_STRENGTH = 0.6f
private const val CONTRAST_STEPS = 40
private const val CONTRAST_STEP_SIZE = 0.02f
