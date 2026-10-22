package dev.cburlacu.stash.data

import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.EmbeddingModelState
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.ai.NoOpOnDeviceEmbedder
import dev.cburlacu.stash.models.StashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class TagCount(
    val name: String,
    val count: Int,
)

data class TopicCount(
    val name: String,
    val count: Int,
)

data class SearchResults(
    val items: List<StashItem> = emptyList(),
    val semanticMatchIds: Set<String> = emptySet(),
)

interface StashRepository {
    fun observe(query: String, tags: Set<String>, sortOrder: SortOrder = SortOrder.Newest, topic: String? = null): Flow<List<StashItem>>
    fun search(query: String): Flow<SearchResults> =
        observe(query = query, tags = emptySet()).map { SearchResults(items = it) }
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

    /** Current state of the on-device EmbeddingGemma 2 model. */
    val embeddingModelState: StateFlow<EmbeddingModelState>
        get() = NoOpOnDeviceEmbedder.modelState

    /** Observes `(indexedCount, totalCount)` for library semantic search coverage. */
    fun observeIndexedCount(): Flow<Pair<Int, Int>> = flowOf(0 to 0)

    /** Downloads and initializes the on-device EmbeddingGemma 2 model. */
    suspend fun downloadEmbeddingModel(): Boolean = false

    /** Cancels an in-progress EmbeddingGemma 2 model download. */
    fun cancelEmbeddingDownload() = Unit

    /** Backfills semantic embeddings for any saved items missing one. */
    suspend fun backfillEmbeddings(): Int = 0
}
