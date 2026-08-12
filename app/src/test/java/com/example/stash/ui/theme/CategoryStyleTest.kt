package com.example.stash.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [categoryHueIndex] and `categoryStyle`'s hue selection must agree for every input, or the
 * colour changes at the mesh-to-glow handover (see the doc comments on both in
 * `CategoryStyle.kt`). `categoryStyle` itself is `@Composable` and cannot be called from a
 * plain JVM test, so this covers what is testable without Compose test infra: every known
 * category string maps to a valid, in-bounds hue index, unknown input never returns -1, and
 * the *set* of indices [categoryHueIndex] can return matches the size of the hue list it
 * indexes into.
 *
 * The set is the five categories workstream B collapsed to — Article, Documentation, Repo,
 * Video, Discussion — plus the pre-collapse strings ("blog", "website", "tweet", "code",
 * "github repo") that rows saved before the collapse still carry. Those legacy strings are
 * covered deliberately: they are live data, and letting one fall through to the unknown branch
 * would silently recolour a user's existing history.
 */
class CategoryStyleTest {

    /** Mirrors [categoryHues]' MeshHueOrder length without requiring a Composable call. */
    private val hueCount = 5

    private val knownCategories = listOf(
        "article", "documentation", "repo", "video", "discussion",
    )

    /** Strings written by pre-collapse builds. Still present in existing databases. */
    private val legacyCategories = listOf(
        "blog", "website", "tweet", "code", "github repo",
    )

    @Test
    fun `never returns -1 for any known category`() {
        for (category in knownCategories + legacyCategories) {
            val index = categoryHueIndex(category)
            assertTrue("categoryHueIndex(\"$category\") returned -1", index != -1)
        }
    }

    @Test
    fun `never returns -1 for junk or unknown input`() {
        val junkInputs = listOf(
            "",
            " ",
            "asdkjaslkdj",
            "🎉🎉🎉",
            "Website",
            "Unsorted",
            "null",
            "12345",
            "a".repeat(500),
        )
        for (input in junkInputs) {
            val index = categoryHueIndex(input)
            assertTrue("categoryHueIndex(\"$input\") returned -1", index != -1)
        }
    }

    @Test
    fun `every known category maps to an in-bounds hue index`() {
        for (category in knownCategories + legacyCategories) {
            val index = categoryHueIndex(category)
            assertTrue(
                "categoryHueIndex(\"$category\") = $index is out of bounds for $hueCount hues",
                index in 0 until hueCount,
            )
        }
    }

    @Test
    fun `junk input maps to an in-bounds hue index`() {
        val junkInputs = listOf("", " ", "unknown-category", "Website", "Unsorted")
        for (input in junkInputs) {
            val index = categoryHueIndex(input)
            assertTrue(
                "categoryHueIndex(\"$input\") = $index is out of bounds for $hueCount hues",
                index in 0 until hueCount,
            )
        }
    }

    @Test
    fun `unknown input resolves to the documented article fallback index`() {
        // "Unsorted" — what a row carries before the model answers and keeps if inference fails —
        // and anything the model invents outside the fixed set all resolve to the article hue.
        // Pinning the literal means a change to that fallback shows up as a visible diff here
        // rather than as a silent behaviour change.
        //
        // Note this fallback is shared with a real category (Article) rather than being a hue of
        // its own, so an unsorted card is not visually distinguishable from a correctly
        // categorised article. That is a known, accepted trade — workstream J surfaces failure
        // in text instead.
        val expectedFallbackIndex = 0
        assertEquals(expectedFallbackIndex, categoryHueIndex("Unsorted"))
        assertEquals(expectedFallbackIndex, categoryHueIndex("something the model invented"))
        assertEquals(expectedFallbackIndex, categoryHueIndex(""))
        // "Website" was a category before the collapse and now folds into Article explicitly,
        // landing on the same index by an explicit branch rather than by falling through.
        assertEquals(expectedFallbackIndex, categoryHueIndex("Website"))
    }

    @Test
    fun `is case and whitespace insensitive, matching categoryStyle's normalization`() {
        // categoryStyle() normalizes with category.lowercase().trim() before matching; a
        // disagreement here would mean "Article", " article ", and "ARTICLE" land on different
        // hues even though categoryStyle renders them identically.
        val variants = listOf("Article", " article ", "ARTICLE", "ArTiCLe", "article")
        val indices = variants.map(::categoryHueIndex).toSet()
        assertEquals("expected all casing/whitespace variants to map to one hue", 1, indices.size)
    }

    @Test
    fun `repo and its legacy spellings share one hue, matching categoryStyle's grouping`() {
        // categoryStyle folds "github repo" and "code" into Repo; categoryHueIndex must agree
        // they share an index, or a card saved under the old spelling would light a different
        // colour from an identical card saved after the collapse.
        assertEquals(categoryHueIndex("repo"), categoryHueIndex("github repo"))
        assertEquals(categoryHueIndex("repo"), categoryHueIndex("code"))
    }

    @Test
    fun `article absorbs blog and website, matching categoryStyle's grouping`() {
        // The collapse folded both into Article. Rows saved as "Blog" or "Website" must keep
        // rendering as Article rather than falling through to the unknown branch.
        assertEquals(categoryHueIndex("article"), categoryHueIndex("blog"))
        assertEquals(categoryHueIndex("article"), categoryHueIndex("website"))
    }

    @Test
    fun `tweet and discussion share the social hue, matching categoryStyle's grouping`() {
        assertEquals(categoryHueIndex("tweet"), categoryHueIndex("discussion"))
    }

    @Test
    fun `every category resolves to its documented index`() {
        // Pins the whole mapping, current and legacy spellings together. The five categories
        // occupy exactly indices 0..4 of MeshHueOrder; legacy strings fold onto those same five.
        val expected = mapOf(
            // The five current categories.
            "article" to 0,
            "documentation" to 1,
            "video" to 2,
            "discussion" to 3,
            "repo" to 4,
            // Pre-collapse spellings still present in existing databases.
            "blog" to 0,
            "website" to 0,
            "tweet" to 3,
            "code" to 4,
            "github repo" to 4,
        )
        for ((category, expectedIndex) in expected) {
            assertEquals("categoryHueIndex(\"$category\")", expectedIndex, categoryHueIndex(category))
        }
    }

    @Test
    fun `the five categories occupy every hue in MeshHueOrder`() {
        // The mesh blends all hues at once and contracts to the winner. A hue no category can
        // resolve to would be a colour the user sees while thinking but never as an answer,
        // and an index beyond the list would crash the shader's lookup.
        val occupied = knownCategories.map(::categoryHueIndex).toSet()
        assertEquals(
            "every hue in MeshHueOrder should be reachable by exactly one category",
            (0 until hueCount).toSet(),
            occupied,
        )
    }
}
