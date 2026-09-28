package dev.cburlacu.stash.data.local

import dev.cburlacu.stash.ai.AiAvailability
import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.ai.OnDeviceSummarizer
import dev.cburlacu.stash.ai.OrganizedContent
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingContextTest {

    private fun repositoryWith(rows: List<StashEntity> = emptyList()) = RoomStashRepository(
        dao = FakeDaoForBriefingTest(rows),
        summarizer = FakeSummarizerForBriefingTest(),
    )

    @Test
    fun `formats multi-item briefing context with titles, domains, takeaways, and key points`() {
        val item1 = StashItem(
            id = "1",
            url = "https://example.com/tool-a",
            title = "Tool A for CLI Automation",
            domain = "example.com",
            category = "Repo",
            headline = "Blazing fast CLI runner with subagent orchestration",
            summary = "Key point 1\nKey point 2",
            tags = listOf("CLI", "AI Agents"),
            readTime = "2 min",
            savedAtEpochMillis = 1000L,
            aiState = AiState.Ready,
        )
        val item2 = StashItem(
            id = "2",
            url = "https://other.org/tool-b",
            title = "Tool B: Headless Execution Engine",
            domain = "other.org",
            category = "Documentation",
            headline = "Configurable headless agent harness",
            summary = "Feature A\nFeature B",
            tags = listOf("CLI", "Automation"),
            readTime = "3 min",
            savedAtEpochMillis = 2000L,
            aiState = AiState.Ready,
        )

        val context = repositoryWith().itemsBriefingContext(listOf(item1, item2))

        assertTrue("Should include Item 1 title", context.contains("Tool A for CLI Automation"))
        assertTrue("Should include Item 2 title", context.contains("Tool B: Headless Execution Engine"))
        assertTrue("Should include Item 1 domain", context.contains("example.com"))
        assertTrue("Should include Item 1 takeaway", context.contains("Blazing fast CLI runner"))
        assertTrue("Should include Item 2 takeaway", context.contains("Configurable headless agent"))
        assertTrue("Should include Item 1 key points", context.contains("Key point 1"))
        assertTrue("Should include Item 2 key points", context.contains("Feature A"))
    }

    /**
     * The bug this guards: `observeItems` used to be built on the feed's list-row query, which
     * omits `content` by design. Every briefing therefore ran on titles and stored bullets with the
     * excerpt branch silently dead. Asserting on a hand-built [StashItem] cannot catch that — the
     * item has to come through the DAO.
     */
    @Test
    fun `items loaded for a briefing carry the stored page body`() = runBlocking {
        val repository = repositoryWith(listOf(entity(id = "1", content = "The full scraped page body.")))

        val items = repository.observeItems(listOf("1")).first()

        assertEquals(1, items.size)
        assertEquals("The full scraped page body.", items.single().content)
        assertTrue(
            "Excerpt should reach the prompt",
            repository.itemsBriefingContext(items).contains("Excerpt: The full scraped page body."),
        )
    }

    @Test
    fun `items are returned in the order they were selected, not the order the query found them`() = runBlocking {
        val repository = repositoryWith(
            listOf(entity(id = "a"), entity(id = "b"), entity(id = "c")),
        )

        val items = repository.observeItems(listOf("c", "a", "b")).first()

        assertEquals(listOf("c", "a", "b"), items.map { it.id })
    }

    @Test
    fun `excerpt budget is shared across items rather than spent per item`() {
        val body = "x".repeat(10_000)
        val repository = repositoryWith()

        val two = repository.itemsBriefingContext(List(2) { item(id = "$it", content = body) })
        val eight = repository.itemsBriefingContext(List(8) { item(id = "$it", content = body) })

        // Each excerpt shrinks as the set grows, so total prompt size stays roughly flat.
        assertTrue("Two sources should each get a longer look", two.length < eight.length * 2)
        assertTrue("Eight sources should still each contribute an excerpt", eight.contains("Excerpt: "))
    }

    @Test
    fun `briefing reads at most the item cap`() {
        val context = repositoryWith().itemsBriefingContext(List(12) { item(id = "$it") })

        assertTrue("Should include the 8th item", context.contains("### Item 8:"))
        assertTrue("Should not include a 9th item", !context.contains("### Item 9:"))
    }

    private fun entity(id: String, content: String = "") = StashEntity(
        id = id,
        url = "https://example.com/$id",
        title = "Title $id",
        domain = "example.com",
        category = "Article",
        headline = "Headline $id",
        summary = "Point one\nPoint two",
        tags = "CLI",
        readTime = "2 min",
        savedAtEpochMillis = 1000L,
        aiState = AiState.Ready.name,
        content = content,
    )

    private fun item(id: String, content: String = "") = StashItem(
        id = id,
        url = "https://example.com/$id",
        title = "Title $id",
        domain = "example.com",
        category = "Article",
        headline = "Headline $id",
        summary = "Point one\nPoint two",
        tags = listOf("CLI"),
        readTime = "2 min",
        savedAtEpochMillis = 1000L,
        aiState = AiState.Ready,
        content = content,
    )
}

private class FakeDaoForBriefingTest(private val rows: List<StashEntity> = emptyList()) : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(rows.firstOrNull { it.id == id })

    /** Mirrors SQL `IN`: matches the ids, in the table's own order rather than the caller's. */
    override fun observeItems(ids: List<String>): Flow<List<StashEntity>> =
        flowOf(rows.filter { it.id in ids.toSet() })

    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun rowsMissingSeed(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsWithImage(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsForTagBackfill(): List<TagBackfillRow> = emptyList()
    override suspend fun rowsMissingTwitterImage(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun allTwitterRows(): List<TwitterImageBackfillRow> = emptyList()
    override fun observeTopic(topic: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeAllTopics(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTopics(): List<String> = emptyList()
    override suspend fun rowsForTopicBackfill(): List<TopicBackfillRow> = emptyList()
    override suspend fun updateItemTopic(id: String, topic: String) = Unit
    override suspend fun updateSearchTopic(id: String, topic: String) = Unit
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

private class FakeSummarizerForBriefingTest : OnDeviceSummarizer {
    override suspend fun availability(): AiAvailability = AiAvailability.Unavailable
    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
        knownTopics: List<String>,
    ): OrganizedContent? = null
    override suspend fun getModelVersion(): String = "fake"
    override suspend fun inferTopic(title: String, summary: String, tags: String, knownTopics: List<String>): String? = null
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
