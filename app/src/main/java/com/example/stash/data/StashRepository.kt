package com.example.stash.data

import com.example.stash.ai.ChatTurn
import com.example.stash.ai.ModelOption
import com.example.stash.models.StashItem
import kotlinx.coroutines.flow.Flow

interface StashRepository {
    fun observe(query: String, tags: Set<String>): Flow<List<StashItem>>
    fun observeTags(): Flow<List<String>>
    fun observeItem(id: String): Flow<StashItem?>
    suspend fun addUrl(url: String)
    suspend fun setRead(id: String, isRead: Boolean)
    suspend fun delete(id: String)
    suspend fun getModelVersion(): String

    /**
     * On-device chat about one saved item, streamed as text chunks. Cold: each collection runs
     * one inference. Grounded in the item's stored notes — the page itself is not re-fetched.
     */
    fun chat(item: StashItem, history: List<ChatTurn>, question: String): Flow<String>

    /** Which model variants this device offers, and their download state. */
    suspend fun probeModels(): List<ModelOption>

    /** Switches the active variant and re-warms it. */
    suspend fun selectModel(choice: ModelChoice)
}
