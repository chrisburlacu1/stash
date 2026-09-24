package com.example.stash.data.local

import com.example.stash.ai.AiAvailability
import com.example.stash.ai.ChatTurn
import com.example.stash.ai.ModelOption
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.OrganizedContent
import com.example.stash.data.ModelChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RoomStashRepository.articleText] selector cascade.
 */
class ArticleTextExtractionTest {

    private val repository = RoomStashRepository(dao = FakeStashDao(), summarizer = FakeSummarizer())

    private fun extract(resourceName: String): String {
        val html = requireNotNull(javaClass.getResourceAsStream("/articles/$resourceName")) {
            "Missing fixture: $resourceName"
        }.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(html, "https://example.com/post")
        return with(repository) { doc.articleText() }
    }

    @Test
    fun `nav-heavy page returns the article prose, not the nav menu`() {
        val text = extract("nav_heavy_article.html")
        assertTrue("expected substantial article text, got ${text.length} chars", text.length > 15_000)
        assertFalse("nav link text leaked into the extraction", text.contains("Section 1:"))
        assertFalse("nav link text leaked into the extraction", text.contains("Section 19:"))
        assertTrue("expected article prose to be present", text.contains("paragraph 1 of"))
        assertTrue("expected article prose to be present", text.contains("paragraph 60 of"))
    }

    @Test
    fun `article held only header and TOC while prose sat in a sibling div`() {
        val text = extract("article_with_sibling_prose.html")
        assertTrue("expected substantial article text, got ${text.length} chars", text.length > 15_000)
        assertTrue("expected article prose to be present", text.contains("paragraph 1 of"))
        assertTrue("expected article prose to be present", text.contains("paragraph 60 of"))
    }

    @Test
    fun `empty document returns empty string rather than throwing`() {
        val doc = Jsoup.parse("<html><body></body></html>", "https://example.com")
        val text = with(repository) { doc.articleText() }
        assertEquals("", text)
    }
}

private class FakeStashDao : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(null)
    override fun observeItems(ids: List<String>): Flow<List<StashEntity>> = flowOf(emptyList())
    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun rowsMissingSeed(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsForTagBackfill(): List<TagBackfillRow> = emptyList()
    override suspend fun rowsMissingTwitterImage(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun allTwitterRows(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun setImageData(id: String, imageFile: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun setSeedAndCrop(id: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun updateItemTags(id: String, tags: String) = Unit
    override suspend fun updateSearchTags(id: String, tags: String) = Unit
    override suspend fun setRead(id: String, isRead: Boolean) = Unit
    override suspend fun deleteItem(id: String) = Unit
    override suspend fun upsertItem(item: StashEntity) = Unit
    override suspend fun upsertSearch(item: StashSearchEntity) = Unit
    override suspend fun deleteSearch(id: String) = Unit
}

private class FakeSummarizer : OnDeviceSummarizer {
    override suspend fun availability(): AiAvailability = AiAvailability.Unavailable
    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
    ): OrganizedContent? = null
    override suspend fun getModelVersion(): String = "fake"
    override suspend fun probeModels(): List<ModelOption> = emptyList()
    override suspend fun selectModel(choice: ModelChoice) = Unit
    override fun chatStream(itemContext: String, history: List<ChatTurn>, question: String): Flow<String> =
        flowOf()
    override fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = flowOf()
}
