package dev.cburlacu.stash.data.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ImageAnalyzerTest {

    @Test
    fun greyImageIsRejectedByTheSaturationFloor() {
        // A screenshot of a white page with grey chrome: populous, entirely neutral.
        val greys = IntArray(2500) { index ->
            val v = 0x40 + (index % 0x80)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertEquals(
            "a neutral image must produce no seed, so the card falls back to category/brand",
            ImageAnalyzer.NONE_SEED,
            ImageAnalyzer.seedFromPixels(greys),
        )
    }

    @Test
    fun smallSaturatedRegionBeatsLargeFlatBackground() {
        // 90% pale grey background, 10% vivid red logo. Material Color Utilities'
        // perceptual scoring ensures the chromatic accent wins over flat background.
        val pixels = IntArray(1000) { index ->
            if (index < 900) 0xFFEDEDED.toInt() else 0xFFE53935.toInt()
        }
        val seed = ImageAnalyzer.seedFromPixels(pixels)
        assertNotEquals("expected a usable seed", ImageAnalyzer.NONE_SEED, seed)

        val lab = Color(seed).convert(ColorSpaces.Oklab)
        val chroma = sqrt(lab.component2() * lab.component2() + lab.component3() * lab.component3())
        assertTrue("expected the saturated red to win, got chroma $chroma", chroma > 0.08f)
        assertTrue("expected a red-dominant seed", ((seed shr 16) and 0xFF) > ((seed shr 8) and 0xFF))
    }

    @Test
    fun githubRepoWithLanguageBarRejectsEdgeBarAndProducesNone() {
        val width = 100
        val height = 100
        val pixels = IntArray(width * height) { index ->
            val y = index / width
            if (y >= 97) 0xFF3178C6.toInt() // 3% bottom TypeScript language bar
            else 0xFFF6F8FA.toInt() // White canvas
        }
        val seed = ImageAnalyzer.seedFromPixels(pixels, width, height)
        assertEquals(
            "monochrome repo with bottom edge language bar must reject the bar and return ImageAnalyzer.NONE_SEED",
            ImageAnalyzer.NONE_SEED,
            seed,
        )
    }

    @Test
    fun darkSlateBackgroundWithLowChromaIsRejected() {
        // GitHub dark mode canvas #161B22: tone ~11, chroma ~9.
        // It has a faint cool tint, but chroma < 18 must be rejected as neutral sludge.
        val pixels = IntArray(1000) { 0xFF161B22.toInt() }
        val seed = ImageAnalyzer.seedFromPixels(pixels)
        assertEquals(
            "dark slate background must be rejected as neutral",
            ImageAnalyzer.NONE_SEED,
            seed,
        )
    }

    @Test
    fun darkSlateBackgroundWithVividLogoPicksLogoNotBackground() {
        // Simulates Google AI Studio header: 90% dark slate/navy gradient (#0A0F1E),
        // 10% vivid coral/red logo (#E53935). The background tone < 10 must be filtered
        // out so the vibrant focal accent wins.
        val pixels = IntArray(1000) { index ->
            if (index < 900) 0xFF0A0F1E.toInt() else 0xFFE53935.toInt()
        }
        val seed = ImageAnalyzer.seedFromPixels(pixels)
        assertNotEquals("expected a usable seed", ImageAnalyzer.NONE_SEED, seed)
        assertEquals("expected the vivid red logo to win over dark background", 0xFFE53935.toInt(), seed)
    }

    @Test
    fun whiteBackgroundWithMultipleTilesPicksDominantChromaTile() {
        // Simulates Google Research: 85% white canvas, 5% green, 5% blue, 5% purple.
        val pixels = IntArray(1000) { index ->
            when {
                index < 850 -> 0xFFFFFFFF.toInt() // white canvas (tone > 92)
                index < 900 -> 0xFF0D8A5B.toInt() // emerald green
                index < 950 -> 0xFF1E88E5.toInt() // blue
                else -> 0xFF6D4BB8.toInt() // purple
            }
        }
        val seed = ImageAnalyzer.seedFromPixels(pixels)
        assertNotEquals("expected a usable seed", ImageAnalyzer.NONE_SEED, seed)
        assertNotEquals("white background must not be chosen", 0xFFFFFFFF.toInt(), seed)
    }

    @Test
    fun transparentAndBlownOutPixelsAreIgnored() {
        val pixels = IntArray(300) { index ->
            when {
                index < 100 -> 0x00FF0000 // transparent red: must not count
                index < 200 -> 0xFFFFFFFF.toInt() // blown-out white: above the lightness ceiling
                else -> 0xFF1E88E5.toInt() // the only real colour present
            }
        }
        val seed = ImageAnalyzer.seedFromPixels(pixels)
        assertNotEquals(ImageAnalyzer.NONE_SEED, seed)
        assertTrue("expected the blue to win", (seed and 0xFF) > ((seed shr 16) and 0xFF))
    }

    @Test
    fun emptyInputYieldsNoSeed() {
        assertEquals(ImageAnalyzer.NONE_SEED, ImageAnalyzer.seedFromPixels(IntArray(0)))
    }

    private fun imageWithTextBand(
        width: Int,
        height: Int,
        bandStart: Int,
        bandEnd: Int,
    ): IntArray = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (y in bandStart until bandEnd && x % 2 == 0) {
            0xFFFFFFFF.toInt() // alternating pixels: maximum local detail
        } else if (y in bandStart until bandEnd) {
            0xFF000000.toInt()
        } else {
            // Smooth vertical gradient: real detail, but low frequency.
            val v = 0x40 + (y * 60 / height)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    @Test
    fun cropBiasFramesAwayFromTextNearTheTop() {
        val pixels = imageWithTextBand(width = 120, height = 60, bandStart = 4, bandEnd = 12)
        val bias = ImageAnalyzer.cropBiasFromPixels(pixels, 120, 60)
        assertTrue("text high in frame should bias downward, got $bias", bias > 0.1f)
    }

    @Test
    fun cropBiasFramesAwayFromTextNearTheBottom() {
        val pixels = imageWithTextBand(width = 120, height = 60, bandStart = 48, bandEnd = 56)
        val bias = ImageAnalyzer.cropBiasFromPixels(pixels, 120, 60)
        assertTrue("text low in frame should bias upward, got $bias", bias < -0.1f)
    }

    @Test
    fun uniformlyBusyImageStaysCentred() {
        val pixels = IntArray(120 * 60) { index ->
            if ((index % 2) == 0) 0xFF303030.toInt() else 0xFFD0D0D0.toInt()
        }
        assertEquals(0f, ImageAnalyzer.cropBiasFromPixels(pixels, 120, 60), 0.001f)
    }

    @Test
    fun degenerateImageSizesAreSafe() {
        assertEquals(0f, ImageAnalyzer.cropBiasFromPixels(IntArray(0), 0, 0), 0.001f)
        assertEquals(0f, ImageAnalyzer.cropBiasFromPixels(IntArray(9), 3, 3), 0.001f)
    }
}
