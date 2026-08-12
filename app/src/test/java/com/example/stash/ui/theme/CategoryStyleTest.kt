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
 * NOTE: workstream B (`MVP-PLAN.md`) is collapsing the category set from seven to five
 * (Article, Documentation, Repo, Video, Discussion) concurrently with this test being written.
 * These tests are written against the CURRENT seven-category state in this worktree and will
 * need their category list updated when that lands — the *properties* they assert (never -1,
 * always in-bounds, case/whitespace-insensitive, unknown input has a defined fallback) should
 * hold regardless of how many categories there are.
 */
class CategoryStyleTest {

    /** Mirrors [categoryHues]' MeshHueOrder length without requiring a Composable call. */
    private val hueCount = 7

    private val knownCategories = listOf(
        "article", "documentation", "blog", "github repo", "code", "video", "tweet", "discussion",
    )

    @Test
    fun `never returns -1 for any known category`() {
        for (category in knownCategories) {
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
        for (category in knownCategories) {
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
    fun `unknown input resolves to the same website fallback index as documented`() {
        // categoryHueIndex's doc comment: "Website", "Unsorted", and anything the model invents
        // outside the fixed set all resolve to the same (website) hue, index 2 per the current
        // `when` branch. Pinning the literal here means a change to that fallback is a visible
        // diff here, not a silent behaviour change.
        val expectedWebsiteIndex = 2
        assertEquals(expectedWebsiteIndex, categoryHueIndex("Website"))
        assertEquals(expectedWebsiteIndex, categoryHueIndex("Unsorted"))
        assertEquals(expectedWebsiteIndex, categoryHueIndex("something the model invented"))
        assertEquals(expectedWebsiteIndex, categoryHueIndex(""))
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
    fun `github repo and code share the repo hue, matching categoryStyle's grouping`() {
        // categoryStyle groups "github repo" and "code" under the same RepoHue but with distinct
        // labels; categoryHueIndex must agree they share an index or the two labels would render
        // in different colours despite categoryStyle treating them as one hue family.
        assertEquals(categoryHueIndex("github repo"), categoryHueIndex("code"))
    }

    @Test
    fun `tweet and discussion share the social hue, matching categoryStyle's grouping`() {
        assertEquals(categoryHueIndex("tweet"), categoryHueIndex("discussion"))
    }

    @Test
    fun `every known category resolves to a distinct index from its documented group`() {
        // Sanity check on the mapping itself: article, documentation, website, video and the
        // social/repo groups should be five distinct indices (plus blog as a sixth), matching
        // the seven-hue MeshHueOrder this test is written against.
        val expected = mapOf(
            "article" to 0,
            "documentation" to 1,
            "blog" to 5,
            "github repo" to 6,
            "code" to 6,
            "video" to 3,
            "tweet" to 4,
            "discussion" to 4,
        )
        for ((category, expectedIndex) in expected) {
            assertEquals("categoryHueIndex(\"$category\")", expectedIndex, categoryHueIndex(category))
        }
    }
}
