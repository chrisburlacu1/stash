package dev.cburlacu.stash.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

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
                val tones = cardTones(seed, dark)
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
                val tones = cardTones(seed, dark)
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
            val tones = cardTones(CardSeed.NONE, dark)
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
    fun noneSeedFallsBackToCategorySeedWhenProvided() {
        val categoryColor = Color(0xFF0D8A5B) // Repo emerald green
        for (dark in listOf(false, true)) {
            val tones = cardTones(CardSeed.NONE, dark, fallbackSeed = categoryColor)
            assertTrue(
                "category fallback title contrast (dark=$dark)",
                contrastRatio(tones.onContainer, tones.container) >= MIN_TITLE_CONTRAST,
            )
            assertTrue(
                "category fallback accent contrast (dark=$dark)",
                contrastRatio(tones.accent, tones.container) >= MIN_ACCENT_CONTRAST,
            )
        }
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
        val checked = listOf(
            "navy" to 0xFF082848.toInt(),
            "purple" to 0xFF281848.toInt(),
            "blue" to 0xFF3878F8.toInt(),
            "red" to 0xFFD32F2F.toInt(),
            "green" to 0xFF4CAF50.toInt(),
        )
        for (dark in listOf(false, true)) {
            val containers = checked.map { (name, seed) ->
                name to cardTones(seed, dark).container
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
                val fromDark = cardTones(darkSeed, dark).container
                val fromBright = cardTones(brightSeed, dark).container
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
}
