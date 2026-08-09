package com.example.stash.data

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
}
