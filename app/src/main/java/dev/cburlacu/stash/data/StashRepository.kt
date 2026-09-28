package dev.cburlacu.stash.data

import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.models.StashItem
import kotlinx.coroutines.flow.Flow

data class TagCount(
    val name: String,
    val count: Int,
)

data class TopicCount(
    val name: String,
    val count: Int,
)

interface StashRepository {
    fun observe(query: String, tags: Set<String>, sortOrder: SortOrder = SortOrder.Newest, topic: String? = null): Flow<List<StashItem>>
    fun observeTags(): Flow<List<TagCount>>
    fun observeTopics(): Flow<List<TopicCount>>
    fun observeItem(id: String): Flow<StashItem?>
    fun observeItems(ids: List<String>): Flow<List<StashItem>>
    suspend fun addUrl(url: String)
    suspend fun setRead(id: String, isRead: Boolean)
    suspend fun delete(id: String)
    suspend fun getModelVersion(): String

    /**
     * On-device chat about one saved item, streamed as text chunks. Cold: each collection runs
     * one inference. Grounded in the item's stored notes — the page itself is not re-fetched.
     */
    fun chat(item: StashItem, history: List<ChatTurn>, question: String): Flow<String>

    /**
     * Multi-item executive briefing and comparative analysis, streamed as text chunks on-device.
     */
    fun briefing(
        items: List<StashItem>,
        topic: String? = null,
        history: List<ChatTurn> = emptyList(),
        question: String? = null,
    ): Flow<String>

    /** Which model variants this device offers, and their download state. */
    suspend fun probeModels(): List<ModelOption>

    /** Switches the active variant and re-warms it. */
    suspend fun selectModel(choice: ModelChoice)

    /**
     * Backfills topics for items missing one using the on-device LibrarianAgent.
     * @return count of items assigned topics.
     */
    suspend fun backfillTopics(): Int
}
