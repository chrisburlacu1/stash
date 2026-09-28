package dev.cburlacu.stash.data.local

import dev.cburlacu.stash.ai.AiAvailability
import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.ai.OnDeviceSummarizer
import dev.cburlacu.stash.ai.OrganizedContent
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.TopicCount
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TopicSchemaTest {

    @Test
    fun `stash item preserves topic field`() {
        val item = StashItem(
            id = "test-1",
            url = "https://example.com/compose",
            title = "Jetpack Compose",
            domain = "example.com",
            category = "Article",
            headline = "Modern UI toolkit",
            summary = "Compose simplifies UI",
            readTime = "3 min",
            savedAtEpochMillis = 1000L,
            topic = "Android",
        )

        assertEquals("Android", item.topic)
    }

    @Test
    fun `observeTopics groups counts and sorts by frequency descending then alphabetical`() = runBlocking {
        val fakeDao = object : FakeDaoForTopicSchema() {
            override fun observeAllTopics(): Flow<List<String>> = flowOf(
                listOf("Design", "Android", "Design", "React", "Android", "Design", "AI")
            )
        }

        val repository = RoomStashRepository(dao = fakeDao, summarizer = FakeTopicSummarizer())
        val topics = repository.observeTopics().first()

        assertEquals(4, topics.size)
        // Design: 3, Android: 2, AI: 1, React: 1 (AI before React alphabetically)
        assertEquals(TopicCount("Design", 3), topics[0])
        assertEquals(TopicCount("Android", 2), topics[1])
        assertEquals(TopicCount("AI", 1), topics[2])
        assertEquals(TopicCount("React", 1), topics[3])
    }

    @Test
    fun `observeTopics filters out blank topics`() = runBlocking {
        val fakeDao = object : FakeDaoForTopicSchema() {
            override fun observeAllTopics(): Flow<List<String>> = flowOf(
                listOf("Design", "", "   ", "Android")
            )
        }

        val repository = RoomStashRepository(dao = fakeDao, summarizer = FakeTopicSummarizer())
        val topics = repository.observeTopics().first()

        assertEquals(2, topics.size)
        assertEquals(TopicCount("Android", 1), topics[0])
        assertEquals(TopicCount("Design", 1), topics[1])
    }
}

private open class FakeDaoForTopicSchema : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTopic(topic: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(null)
    override fun observeItems(ids: List<String>): Flow<List<StashEntity>> = flowOf(emptyList())
    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override fun observeAllTopics(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTopics(): List<String> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun rowsMissingSeed(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsWithImage(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsForTagBackfill(): List<TagBackfillRow> = emptyList()
    override suspend fun rowsForTopicBackfill(): List<TopicBackfillRow> = emptyList()
    override suspend fun rowsMissingTwitterImage(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun allTwitterRows(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun setImageData(id: String, imageFile: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun setSeedAndCrop(id: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun updateItemTags(id: String, tags: String) = Unit
    override suspend fun updateSearchTags(id: String, tags: String) = Unit
    override suspend fun updateItemTopic(id: String, topic: String) = Unit
    override suspend fun updateSearchTopic(id: String, topic: String) = Unit
    override suspend fun setRead(id: String, isRead: Boolean) = Unit
    override suspend fun deleteItem(id: String) = Unit
    override suspend fun upsertItem(item: StashEntity) = Unit
    override suspend fun upsertSearch(item: StashSearchEntity) = Unit
    override suspend fun deleteSearch(id: String) = Unit
}

private class FakeTopicSummarizer : OnDeviceSummarizer {
    override suspend fun availability(): AiAvailability = AiAvailability.Unavailable
    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
        knownTopics: List<String>,
    ): OrganizedContent? = null
    override suspend fun getModelVersion(): String = "fake"
    override suspend fun probeModels(): List<ModelOption> = emptyList()
    override suspend fun selectModel(choice: ModelChoice) = Unit
    override fun chatStream(itemContext: String, history: List<ChatTurn>, question: String): Flow<String> = flowOf()
    override fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = flowOf()
    override suspend fun inferTopic(
        title: String,
        summary: String,
        tags: String,
        knownTopics: List<String>,
    ): String? = null
}
