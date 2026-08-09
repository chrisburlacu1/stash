package com.example.stash.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stash.data.StashRepository
import com.example.stash.models.StashItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<StashItem> = emptyList(),
    val query: String = "",
    /** Empty means no filter, i.e. show everything — there is no separate "All" option. */
    val selectedTags: Set<String> = emptySet(),
    val tags: List<String> = emptyList(),
    val showAddUrl: Boolean = false,
    val modelVersion: String = "Gemini Nano (ML Kit)",
)

@OptIn(ExperimentalCoroutinesApi::class)
class StashFeedViewModel(private val repository: StashRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val showAddUrl = MutableStateFlow(false)
    private val modelVersion = MutableStateFlow("Gemini Nano")

    init {
        viewModelScope.launch {
            modelVersion.value = repository.getModelVersion()
        }
    }

    val uiState: StateFlow<FeedUiState> = combine(query, selectedTags) { q, t -> q to t }
        .flatMapLatest { (q, t) ->
            combine(repository.observe(q, t), repository.observeTags(), showAddUrl, modelVersion) { items, tags, show, version ->
                FeedUiState(items, q, t, tags, show, version)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    fun setQuery(value: String) { query.value = value }

    /** Toggles a tag in the filter set; deselecting the last one restores the unfiltered feed. */
    fun toggleTag(value: String) {
        selectedTags.update { current ->
            if (value in current) current - value else current + value
        }
    }
    fun showAddUrl() { showAddUrl.value = true }
    fun dismissAddUrl() { showAddUrl.value = false }
    fun addUrl(url: String) {
        if (url.isBlank()) return
        showAddUrl.value = false
        viewModelScope.launch { repository.addUrl(url.trim()) }
    }

    fun setRead(id: String, isRead: Boolean) {
        viewModelScope.launch { repository.setRead(id, isRead) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    class Factory(private val repository: StashRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashFeedViewModel(repository) as T
    }
}
