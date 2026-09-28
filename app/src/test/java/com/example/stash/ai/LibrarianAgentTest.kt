package com.example.stash.ai

import com.example.stash.data.ModelChoice
import com.example.stash.data.local.SeedBackfillRow
import com.example.stash.data.local.StashDao
import com.example.stash.data.local.StashEntity
import com.example.stash.data.local.StashListRow
import com.example.stash.data.local.StashSearchEntity
import com.example.stash.data.local.TagBackfillRow
import com.example.stash.data.local.TopicBackfillRow
import com.example.stash.data.local.TwitterImageBackfillRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarianAgentTest {

    @Test
    fun `backfillTopics skips when AI is unavailable`() = runBlocking {
        val fakeDao = FakeLibrarianDao()
        val fakeSummarizer = FakeLibrarianSummarizer(availability = AiAvailability.Unavailable)
        val agent = LibrarianAgent(dao = fakeDao, summarizer = fakeSummarizer)

        val count = agent.backfillTopics()

        assertEquals(0, count)
        assertEquals(0, fakeDao.updatedTopics.size)
    }

    @Test
    fun `backfillTopics infers topic and updates DAO`() = runBlocking {
        val fakeDao = FakeLibrarianDao(
            pending = listOf(
                TopicBackfillRow(
                    id = "1",
                    title = "Building Compose Layouts",
                    summary = "Declarative layouts in Jetpack Compose",
                    tags = "Compose | Android",
                    topic = "",
                )
            ),
            knownTopics = listOf("Android", "Design"),
        )
        val fakeSummarizer = FakeLibrarianSummarizer(
            availability = AiAvailability.Available,
            inferredTopic = "Android",
        )
        val agent = LibrarianAgent(dao = fakeDao, summarizer = fakeSummarizer)

        val count = agent.backfillTopics()

        assertEquals(1, count)
        assertEquals("Android", fakeDao.updatedTopics["1"])
    }

    @Test
    fun `backfillTopics snaps near match to existing library topic`() = runBlocking {
        val fakeDao = FakeLibrarianDao(
            pending = listOf(
                TopicBackfillRow(
                    id = "1",
                    title = "Figma Auto-layout Tricks",
                    summary = "Guide to auto layout in Figma",
                    tags = "Figma | UI",
                    topic = "",
                )
            ),
            knownTopics = listOf("Design System", "Android"),
        )
        val fakeSummarizer = FakeLibrarianSummarizer(
            availability = AiAvailability.Available,
            // AI returns "Design" which should snap to "Design System" via prefix match
            inferredTopic = "Design",
        )
        val agent = LibrarianAgent(dao = fakeDao, summarizer = fakeSummarizer)

        val count = agent.backfillTopics()

        assertEquals(1, count)
        assertEquals("Design System", fakeDao.updatedTopics["1"])
    }

    @Test
    fun `backfillTopics falls back to tag-based heuristic when AI inference returns null`() = runBlocking {
        val fakeDao = FakeLibrarianDao(
            pending = listOf(
                TopicBackfillRow(
                    id = "1",
                    title = "React 19 Server Actions",
                    summary = "Guide to actions",
                    tags = "React | Web",
                    topic = "",
                )
            ),
            knownTopics = listOf("React", "Android"),
        )
        val fakeSummarizer = FakeLibrarianSummarizer(
            availability = AiAvailability.Available,
            inferredTopic = null,
        )
        val agent = LibrarianAgent(dao = fakeDao, summarizer = fakeSummarizer)

        val count = agent.backfillTopics()

        assertEquals(1, count)
        assertEquals("React", fakeDao.updatedTopics["1"])
    }
}

private class FakeLibrarianDao(
    private val pending: List<TopicBackfillRow> = emptyList(),
    private val knownTopics: List<String> = emptyList(),
) : StashDao {
    val updatedTopics = mutableMapOf<String, String>()

    override suspend fun rowsForTopicBackfill(): List<TopicBackfillRow> = pending
    override suspend fun allTopics(): List<String> = knownTopics

    override suspend fun updateItemTopic(id: String, topic: String) {
        updatedTopics[id] = topic
    }

    override suspend fun updateSearchTopic(id: String, topic: String) {
        updatedTopics[id] = topic
    }

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
    override suspend fun count(): Int = 0
    override suspend fun rowsMissingSeed(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsWithImage(): List<SeedBackfillRow> = emptyList()
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

private class FakeLibrarianSummarizer(
    private val availability: AiAvailability,
    private val inferredTopic: String? = null,
) : OnDeviceSummarizer {
    override suspend fun availability(): AiAvailability = availability
    override suspend fun inferTopic(
        title: String,
        summary: String,
        tags: String,
        knownTopics: List<String>,
    ): String? = inferredTopic

    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
        knownTopics: List<String>,
    ): OrganizedContent? = null

    override suspend fun getModelVersion(): String = "test-nano"
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
}
