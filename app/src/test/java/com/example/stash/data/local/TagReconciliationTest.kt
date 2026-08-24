package com.example.stash.data.local

import com.example.stash.ai.AiAvailability
import com.example.stash.ai.ChatTurn
import com.example.stash.ai.ModelOption
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.OrganizedContent
import com.example.stash.data.ModelChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class TagReconciliationTest {

    private val repository = RoomStashRepository(
        dao = FakeDaoForTagTest(),
        summarizer = FakeSummarizerForTagTest(),
    )

    @Test
    fun `formats tags to clean title casing while preserving granular terms`() {
        val rawTags = listOf("ai agent", "terminal UI", "agent harness", "agentic", "command line")
        val reconciled = repository.reconcileTags(rawTags, knownTags = emptyList())

        assertEquals(listOf("Ai Agent", "Terminal UI", "Agent Harness", "Agentic", "Command Line"), reconciled)
    }

    @Test
    fun `snaps to existing library tags when matching ignoring case and punctuation`() {
        val rawTags = listOf("jetpack compose", "coroutines", "room database")
        val knownTags = listOf("Jetpack Compose", "Coroutines", "Room Database")

        val reconciled = repository.reconcileTags(rawTags, knownTags = knownTags)

        assertEquals(listOf("Jetpack Compose", "Coroutines", "Room Database"), reconciled)
    }

    @Test
    fun `caps tags at MAX_TAGS_PER_ITEM`() {
        val rawTags = listOf("Tag1", "Tag2", "Tag3", "Tag4", "Tag5", "Tag6", "Tag7", "Tag8")
        val reconciled = repository.reconcileTags(rawTags, knownTags = emptyList())

        assertEquals(6, reconciled.size)
    }
}

private class FakeDaoForTagTest : StashDao {
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

private class FakeSummarizerForTagTest : OnDeviceSummarizer {
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
