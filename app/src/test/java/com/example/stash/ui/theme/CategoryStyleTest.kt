package com.example.stash.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying category hue mapping and legacy category compatibility.
 */
class CategoryStyleTest {

    private val hueCount = 5

    private val knownCategories = listOf(
        "article", "documentation", "repo", "video", "discussion",
    )

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
        val expectedFallbackIndex = 0
        assertEquals(expectedFallbackIndex, categoryHueIndex("Unsorted"))
        assertEquals(expectedFallbackIndex, categoryHueIndex("something the model invented"))
        assertEquals(expectedFallbackIndex, categoryHueIndex(""))
        assertEquals(expectedFallbackIndex, categoryHueIndex("Website"))
    }

    @Test
    fun `is case and whitespace insensitive, matching categoryStyle's normalization`() {
        val variants = listOf("Article", " article ", "ARTICLE", "ArTiCLe", "article")
        val indices = variants.map(::categoryHueIndex).toSet()
        assertEquals("expected all casing/whitespace variants to map to one hue", 1, indices.size)
    }

    @Test
    fun `repo and its legacy spellings share one hue, matching categoryStyle's grouping`() {
        assertEquals(categoryHueIndex("repo"), categoryHueIndex("github repo"))
        assertEquals(categoryHueIndex("repo"), categoryHueIndex("code"))
    }

    @Test
    fun `article absorbs blog and website, matching categoryStyle's grouping`() {
        assertEquals(categoryHueIndex("article"), categoryHueIndex("blog"))
        assertEquals(categoryHueIndex("article"), categoryHueIndex("website"))
    }

    @Test
    fun `tweet and discussion share the social hue, matching categoryStyle's grouping`() {
        assertEquals(categoryHueIndex("tweet"), categoryHueIndex("discussion"))
    }

    @Test
    fun `every category resolves to its documented index`() {
        val expected = mapOf(
            "article" to 0,
            "documentation" to 1,
            "video" to 2,
            "discussion" to 3,
            "repo" to 4,
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
        val occupied = knownCategories.map(::categoryHueIndex).toSet()
        assertEquals(
            "every hue should be reachable by exactly one category",
            (0 until hueCount).toSet(),
            occupied,
        )
    }
}
