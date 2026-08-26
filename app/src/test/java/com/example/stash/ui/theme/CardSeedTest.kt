package com.example.stash.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The contrast guarantee the redesign brief asks for, asserted across a spread of seed hues in
 * both themes.
 *
 * This is the test that makes per-card colour shippable. Derived tones are the one place in the
 * app where a colour pair is computed at runtime from data (a downloaded image) rather than picked
 * by hand, so nothing else can catch an unreadable combination — a yellow seed and a navy one
 * behave completely differently at the same Oklab lightness, and only measurement tells you which
 * one just produced grey-on-grey.
 *
 * These run on plain JVM: Oklab conversion and `luminance()` in ui-graphics are pure maths with no
 * framework calls, so no Robolectric is needed (verified before this file was written).
 */
class CardSeedTest {

    /** A full turn of hues at realistic saturations, plus the awkward extremes. */
    private val seedSpread = listOf(
        "red" to 0xFFD32F2F.toInt(),
        "orange" to 0xFFF57C00.toInt(),
        "amber" to 0xFFFFC107.toInt(),
        "yellow" to 0xFFFFEB3B.toInt(),
        "lime" to 0xFFCDDC39.toInt(),
        "green" to 0xFF4CAF50.toInt(),
        "teal" to 0xFF009688.toInt(),
        "cyan" to 0xFF00BCD4.toInt(),
        "blue" to 0xFF2196F3.toInt(),
        "indigo" to 0xFF3F51B5.toInt(),
        "deepPurple" to 0xFF673AB7.toInt(),
        "magenta" to 0xFFE7418F.toInt(),
        "brown" to 0xFF795548.toInt(),
        "nearBlackNavy" to 0xFF0A1128.toInt(),
        "nearWhiteCream" to 0xFFFFF8E1.toInt(),
        "vividPink" to 0xFFFF0090.toInt(),
        "pureRed" to 0xFFFF0000.toInt(),
    )

    @Test
    fun titleClearsAaAgainstItsContainerInBothThemes() {
        for ((name, seed) in seedSpread) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) Color(0xFF121212) else Color(0xFFFDFCFF)
                val tones = cardTones(seed, dark, surface)
                val ratio = contrastRatio(tones.onContainer, tones.container)
                assertTrue(
                    "title contrast for $name (dark=$dark) was $ratio, need >= $MIN_TITLE_CONTRAST",
                    ratio >= MIN_TITLE_CONTRAST,
                )
            }
        }
    }

    @Test
    fun accentClearsLargeTextAaAgainstItsContainerInBothThemes() {
        for ((name, seed) in seedSpread) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) Color(0xFF121212) else Color(0xFFFDFCFF)
                val tones = cardTones(seed, dark, surface)
                val ratio = contrastRatio(tones.accent, tones.container)
                assertTrue(
                    "accent contrast for $name (dark=$dark) was $ratio, need >= $MIN_ACCENT_CONTRAST",
                    ratio >= MIN_ACCENT_CONTRAST,
                )
            }
        }
    }

    /**
     * The fallback path must be as safe as the derived one: a card with no usable seed still draws
     * a title and an eyebrow, using the brand colour.
     */
    @Test
    fun noneSeedFallsBackToBrandAndStillClearsContrast() {
        for (dark in listOf(false, true)) {
            val surface = if (dark) Color(0xFF121212) else Color(0xFFFDFCFF)
            val tones = cardTones(CardSeed.NONE, dark, surface)
            assertTrue(
                "fallback title contrast (dark=$dark)",
                contrastRatio(tones.onContainer, tones.container) >= MIN_TITLE_CONTRAST,
            )
            assertTrue(
                "fallback accent contrast (dark=$dark)",
                contrastRatio(tones.accent, tones.container) >= MIN_ACCENT_CONTRAST,
            )
        }
    }

    @Test
    fun greyImageIsRejectedByTheSaturationFloor() {
        // A screenshot of a white page with grey chrome: populous, entirely neutral.
        val greys = IntArray(2500) { index ->
            val v = 0x40 + (index % 0x80)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertEquals(
            "a neutral image must produce no seed, so the card falls back to brand",
            CardSeed.NONE,
            seedFromPixels(greys),
        )
    }

    @Test
    fun smallSaturatedRegionBeatsLargeFlatBackground() {
        // 90% pale grey background, 10% vivid red logo. The population-times-saturation score is
        // what makes the red win; a naive most-common-colour pick would return the grey.
        val pixels = IntArray(1000) { index ->
            if (index < 900) 0xFFEDEDED.toInt() else 0xFFE53935.toInt()
        }
        val seed = seedFromPixels(pixels)
        assertNotEquals("expected a usable seed", CardSeed.NONE, seed)

        val lab = Color(seed).convert(ColorSpaces.Oklab)
        val chroma = sqrt(lab.component2() * lab.component2() + lab.component3() * lab.component3())
        assertTrue("expected the saturated red to win, got chroma $chroma", chroma > 0.08f)
        assertTrue("expected a red-dominant seed", ((seed shr 16) and 0xFF) > ((seed shr 8) and 0xFF))
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
        val seed = seedFromPixels(pixels)
        assertNotEquals(CardSeed.NONE, seed)
        assertTrue("expected the blue to win", (seed and 0xFF) > ((seed shr 16) and 0xFF))
    }

    @Test
    fun emptyInputYieldsNoSeed() {
        assertEquals(CardSeed.NONE, seedFromPixels(IntArray(0)))
    }

    /**
     * The point of the feature: a card's container must actually look like its own image's colour.
     *
     * This exists because of a real regression. Chasing "too pink" (which was really "all the same
     * pink", from a missing backfill) the container chroma was cut until every seed produced a
     * container within ~2% of white — derived from the image in principle, identical in practice.
     * A contrast test cannot catch that, because near-white containers pass contrast easily.
     */
    @Test
    fun differentSeedsProduceDistinguishableContainers() {
        val surface = Color(0xFFFDFCFF)
        val checked = listOf(
            "navy" to 0xFF082848.toInt(),
            "purple" to 0xFF281848.toInt(),
            "blue" to 0xFF3878F8.toInt(),
            "red" to 0xFFD32F2F.toInt(),
            "green" to 0xFF4CAF50.toInt(),
        )
        for (dark in listOf(false, true)) {
            val containers = checked.map { (name, seed) ->
                name to cardTones(seed, dark, surface).container
            }
            for (i in containers.indices) {
                for (j in i + 1 until containers.size) {
                    val (nameA, a) = containers[i]
                    val (nameB, b) = containers[j]
                    val distance = channelDistance(a, b)
                    assertTrue(
                        "$nameA and $nameB containers are near-identical (distance $distance, dark=$dark)",
                        distance > MIN_CONTAINER_SEPARATION,
                    )
                }
            }
        }
    }

    /**
     * A dark seed must tint as strongly as a bright one of the same hue.
     *
     * The failure this pins down: Oklab chroma shrinks with lightness, so deriving tones from a
     * seed's raw a/b gave a near-black navy almost no colour. A dark blue header image produced an
     * off-white card while a mid-blue image produced a blue one — and dark header images are the
     * common case in this app, not an edge case.
     */
    @Test
    fun darkAndBrightSeedsOfTheSameHueTintEqually() {
        val surface = Color(0xFFFDFCFF)
        val pairs = listOf(
            "blue" to (0xFF080818.toInt() to 0xFF3878F8.toInt()),
            "green" to (0xFF041008.toInt() to 0xFF4CAF50.toInt()),
        )
        for ((name, seeds) in pairs) {
            val (darkSeed, brightSeed) = seeds
            for (dark in listOf(false, true)) {
                val fromDark = cardTones(darkSeed, dark, surface).container
                val fromBright = cardTones(brightSeed, dark, surface).container
                val separationFromSurface = channelDistance(fromDark, surface)
                assertTrue(
                    "$name: a dark seed produced a container indistinguishable from the surface " +
                        "(distance $separationFromSurface, dark=$dark)",
                    dark || separationFromSurface > MIN_CONTAINER_SEPARATION,
                )
                // Same hue family, so the two containers should land close together.
                val betweenSeeds = channelDistance(fromDark, fromBright)
                assertTrue(
                    "$name: dark and bright seeds of one hue diverged too far ($betweenSeeds, dark=$dark)",
                    betweenSeeds < 0.10f,
                )
            }
        }
    }

    /** Largest single-channel difference between two colours, 0..1. */
    private fun channelDistance(a: Color, b: Color): Float = maxOf(
        kotlin.math.abs(a.red - b.red),
        kotlin.math.abs(a.green - b.green),
        kotlin.math.abs(a.blue - b.blue),
    )

    /**
     * How far apart two containers must sit to read as different colours rather than as the same
     * off-white. About 3.5 steps of 255 — deliberately a low bar, since it only has to catch the
     * collapse-to-white failure, not enforce a particular strength.
     */
    private val MIN_CONTAINER_SEPARATION = 0.014f

    /**
     * Builds a test image: a smooth background with one band of high-frequency noise standing in
     * for a line of burnt-in text.
     */
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
        val bias = cropBiasFromPixels(pixels, 120, 60)
        assertTrue("text high in frame should bias downward, got $bias", bias > 0.1f)
    }

    @Test
    fun cropBiasFramesAwayFromTextNearTheBottom() {
        val pixels = imageWithTextBand(width = 120, height = 60, bandStart = 48, bandEnd = 56)
        val bias = cropBiasFromPixels(pixels, 120, 60)
        assertTrue("text low in frame should bias upward, got $bias", bias < -0.1f)
    }

    /**
     * The guard that stops the crop chasing noise. A uniformly busy image — a photograph, a dense
     * screenshot — has no single text band, and moving the crop on that evidence would just pick a
     * random offset per card.
     */
    @Test
    fun uniformlyBusyImageStaysCentred() {
        val pixels = IntArray(120 * 60) { index ->
            if ((index % 2) == 0) 0xFF303030.toInt() else 0xFFD0D0D0.toInt()
        }
        assertEquals(0f, cropBiasFromPixels(pixels, 120, 60), 0.001f)
    }

    @Test
    fun degenerateImageSizesAreSafe() {
        assertEquals(0f, cropBiasFromPixels(IntArray(0), 0, 0), 0.001f)
        assertEquals(0f, cropBiasFromPixels(IntArray(9), 3, 3), 0.001f)
    }
}
