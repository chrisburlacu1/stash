package dev.cburlacu.stash.data.image

import android.graphics.BitmapFactory
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import java.io.File
import kotlin.math.abs

data class ImageAnalysis(val seedColor: Int, val cropBias: Float)

object ImageAnalyzer {
    const val NONE_SEED = 0

    private const val CROP_BANDS = 5
    private const val CROP_TEXT_DOMINANCE = 1.35f
    private const val CROP_BIAS_STRENGTH = 0.55f
    private const val ANALYSIS_SAMPLE_EDGE_PX = 160

    /**
     * Extracts a dominant, vibrant seed color from ARGB [pixels] using Google's Material Color Utilities.
     * Insets the sampling bounds slightly away from outer borders, filters out extreme canvas tones
     * and desaturated neutral sludge.
     */
    fun seedFromPixels(pixels: IntArray, width: Int = 0, height: Int = 0): Int {
        if (pixels.isEmpty()) return NONE_SEED

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
            if (idx == 0) return NONE_SEED
            if (idx == buffer.size) buffer else buffer.copyOf(idx)
        } else {
            var opaqueCount = 0
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) >= 128) opaqueCount++
            }
            if (opaqueCount == 0) return NONE_SEED
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
        if (sampled.isEmpty()) return NONE_SEED

        val quantized = QuantizerCelebi.quantize(sampled, 128)
        if (quantized.isEmpty()) return NONE_SEED

        val filtered = quantized.filter { (argb, _) ->
            val hct = Hct.fromInt(argb)
            hct.tone in 12.0..88.0 && hct.chroma >= 18.0
        }

        val chromaticPixels = filtered.values.sum()
        if (chromaticPixels < sampled.size * 0.004) return NONE_SEED

        val ranked = Score.score(filtered, 4, NONE_SEED, true)
        return ranked.firstOrNull { it != NONE_SEED } ?: NONE_SEED
    }

    /**
     * Finds the horizontal band holding an image's headline text and returns a vertical bias
     * framing away from it: -1 (top), 0 (center), +1 (bottom).
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
     * Samples image bytes down to a thumbnail and computes seed color and crop bias.
     */
    fun analyzeImage(bytes: ByteArray): ImageAnalysis = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching ImageAnalysis(NONE_SEED, 0f)

        val options = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, longest / ANALYSIS_SAMPLE_EDGE_PX)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@runCatching ImageAnalysis(NONE_SEED, 0f)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()

        ImageAnalysis(
            seedColor = seedFromPixels(pixels, width, height),
            cropBias = cropBiasFromPixels(pixels, width, height),
        )
    }.getOrDefault(ImageAnalysis(NONE_SEED, 0f))

    /**
     * Checks if a cached image file is low resolution (<= 200px on any dimension).
     */
    fun isLowResolutionImage(file: File): Boolean = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth in 1..200 || options.outHeight in 1..200
    }.getOrDefault(false)
}
