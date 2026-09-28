package dev.cburlacu.stash.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayCircle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying category label and icon mapping with legacy compatibility.
 */
class CategoryStyleTest {

    @Test
    fun `canonical categories resolve correct labels and icons`() {
        val article = categoryStyle("article")
        assertEquals("Article", article.label)
        assertEquals(Icons.AutoMirrored.Filled.Article, article.icon)

        val doc = categoryStyle("documentation")
        assertEquals("Documentation", doc.label)
        assertEquals(Icons.AutoMirrored.Filled.MenuBook, doc.icon)

        val repo = categoryStyle("repo")
        assertEquals("Repo", repo.label)
        assertEquals(Icons.Default.Code, repo.icon)

        val video = categoryStyle("video")
        assertEquals("Video", video.label)
        assertEquals(Icons.Default.PlayCircle, video.icon)

        val discussion = categoryStyle("discussion")
        assertEquals("Discussion", discussion.label)
        assertEquals(Icons.Default.Forum, discussion.icon)
    }

    @Test
    fun `legacy category names map to canonical types`() {
        assertEquals("Article", categoryStyle("blog").label)
        assertEquals("Article", categoryStyle("website").label)
        assertEquals("Repo", categoryStyle("code").label)
        assertEquals("Repo", categoryStyle("github repo").label)
        assertEquals("Discussion", categoryStyle("tweet").label)
    }

    @Test
    fun `casing and whitespace are normalized cleanly`() {
        val variants = listOf("Article", " article ", "ARTICLE", "ArTiCLe")
        for (v in variants) {
            assertEquals("Article", categoryStyle(v).label)
            assertEquals(Icons.AutoMirrored.Filled.Article, categoryStyle(v).icon)
        }
    }

    @Test
    fun `unknown category capitalized gracefully`() {
        val unknown = categoryStyle("podcast")
        assertEquals("Podcast", unknown.label)

        val blank = categoryStyle("")
        assertEquals("", blank.label)
    }
}
