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
 * Regression coverage for [RoomStashRepository.articleText]'s selector cascade, per
 * `CLAUDE.md`: this has broken twice already, silently, because a degraded extraction still
 * "succeeds" and just produces a thin summary.
 *
 * `articleText()` is a private extension function on `Document`; it is widened to
 * `internal` + `@VisibleForTesting` (see the function's doc comment in
 * [RoomStashRepository]) purely so these fixtures can drive it directly, without pulling in
 * Room or a real summarizer.
 */
class ArticleTextExtractionTest {

    /** No dao/summarizer methods are ever invoked — [articleText] only touches its receiver. */
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
        // Regression fixture for the "286 chars of navigation menu" bug (CLAUDE.md): the old
        // regex extractor grabbed whatever text block came first in the raw HTML, which on this
        // page shape is a real-looking site nav with ~20 plausible-sounding links, well ahead of
        // the actual ~60-paragraph article body. jsoup's chrome removal (`nav` is stripped
        // outright) plus paragraph-density scoring must land on the article, not the menu.
        val text = extract("nav_heavy_article.html")

        // The old bug's signature was a few hundred characters of boilerplate. The real article
        // body here is tens of thousands of characters, so this both confirms real content was
        // found and guards against a regression back to a tiny nav-only extract.
        assertTrue("expected substantial article text, got ${text.length} chars", text.length > 15_000)

        assertFalse("nav link text leaked into the extraction", text.contains("Section 1:"))
        assertFalse("nav link text leaked into the extraction", text.contains("Section 19:"))
        assertTrue("expected article prose to be present", text.contains("paragraph 1 of"))
        assertTrue("expected article prose to be present", text.contains("paragraph 60 of"))
    }

    @Test
    fun `article held only header and TOC while prose sat in a sibling div`() {
        // Regression fixture for the Hashnode/Next.js-shaped bug (CLAUDE.md): <article> is
        // present and non-empty (title, hero image, table of contents), so trusting "the first
        // <article> tag" looks like it worked but actually returns only the TOC. The real prose
        // sits in a sibling <div class="prose">. Only the density comparison across candidates
        // (article, main, [class*=prose], ...) catches this.
        val text = extract("article_with_sibling_prose.html")

        assertTrue("expected substantial article text, got ${text.length} chars", text.length > 15_000)

        // The TOC entries are short <li> lines ("Heading 1".."Heading 8") inside <article>; the
        // real prose paragraphs reference "paragraph N of". If the naive answer (the <article>
        // element alone) had won, none of the paragraph prose would be present at all.
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

/** Minimal no-op [StashDao]; [articleText] never touches the dao, so every method is unused. */
private class FakeStashDao : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(null)
    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun setRead(id: String, isRead: Boolean) = Unit
    override suspend fun deleteItem(id: String) = Unit
    override suspend fun upsertItem(item: StashEntity) = Unit
    override suspend fun upsertSearch(item: StashSearchEntity) = Unit
    override suspend fun deleteSearch(id: String) = Unit
}

/** Minimal no-op [OnDeviceSummarizer]; [articleText] never touches the summarizer. */
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
}
