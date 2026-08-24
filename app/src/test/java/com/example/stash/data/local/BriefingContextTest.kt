package com.example.stash.data.local

import com.example.stash.ai.AiAvailability
import com.example.stash.ai.ChatTurn
import com.example.stash.ai.ModelOption
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.OrganizedContent
import com.example.stash.data.ModelChoice
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingContextTest {

    private val repository = RoomStashRepository(
        dao = FakeDaoForBriefingTest(),
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

        val context = repository.itemsBriefingContext(listOf(item1, item2))

        assertTrue("Should include Item 1 title", context.contains("Tool A for CLI Automation"))
        assertTrue("Should include Item 2 title", context.contains("Tool B: Headless Execution Engine"))
        assertTrue("Should include Item 1 domain", context.contains("example.com"))
        assertTrue("Should include Item 1 takeaway", context.contains("Blazing fast CLI runner"))
        assertTrue("Should include Item 2 takeaway", context.contains("Configurable headless agent"))
        assertTrue("Should include Item 1 key points", context.contains("Key point 1"))
        assertTrue("Should include Item 2 key points", context.contains("Feature A"))
    }
}

private class FakeDaoForBriefingTest : StashDao {
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

private class FakeSummarizerForBriefingTest : OnDeviceSummarizer {
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
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = flowOf()
}
