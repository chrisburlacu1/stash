package dev.cburlacu.stash.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.cburlacu.stash.data.FeedView
import dev.cburlacu.stash.data.SortOrder
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.StashSettings
import dev.cburlacu.stash.data.TagCount
import dev.cburlacu.stash.data.TopicCount
import dev.cburlacu.stash.models.StashItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<StashItem> = emptyList(),
    val query: String = "",
    val searchResults: List<StashItem> = emptyList(),
    val selectedTopic: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val selectedItemIds: Set<String> = emptySet(),
    val topics: List<TopicCount> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val showAddUrl: Boolean = false,
    val sortOrder: SortOrder = SortOrder.Newest,
    val feedView: FeedView = FeedView.List,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StashFeedViewModel(
    private val repository: StashRepository,
    private val settings: StashSettings,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTopic = MutableStateFlow<String?>(null)
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val showAddUrl = MutableStateFlow(false)

    private val feedItems = combine(
        selectedTopic,
        selectedTags,
        settings.sortOrder,
    ) { topic, tags, sort ->
        Triple(topic, tags, sort)
    }.flatMapLatest { (topic, tags, sort) ->
        repository.observe(
            query = "",
            tags = tags,
            sortOrder = sort,
            topic = topic,
        )
    }

    private val searchResults = query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.observe(q, emptySet())
        }

    val uiState: StateFlow<FeedUiState> = combine(
        feedItems,
        searchResults,
        combine(repository.observeTopics(), repository.observeTags()) { topics, tags -> topics to tags },
        combine(showAddUrl, settings.sortOrder, settings.feedView) { show, sort, view -> Triple(show, sort, view) },
        combine(query, selectedTopic, selectedTags, selectedItemIds) { q, topic, t, s -> SelectionState(q, topic, t, s) },
    ) { items, results, (topics, tags), (show, sort, view), sel ->
        FeedUiState(
            items = items,
            query = sel.query,
            searchResults = results,
            selectedTopic = sel.topic,
            selectedTags = sel.tags,
            selectedItemIds = sel.selectedIds,
            topics = topics,
            tags = tags,
            showAddUrl = show,
            sortOrder = sort,
            feedView = view,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    private data class SelectionState(
        val query: String,
        val topic: String?,
        val tags: Set<String>,
        val selectedIds: Set<String>,
    )

    fun setSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(sortOrder) }
    }

    fun setFeedView(view: FeedView) {
        viewModelScope.launch { settings.setFeedView(view) }
    }

    fun setQuery(value: String) { query.value = value }

    fun selectTopic(topic: String?) {
        selectedTopic.value = if (selectedTopic.value == topic) null else topic
    }

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

    fun toggleSelectItem(id: String) {
        selectedItemIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        selectedItemIds.value = emptySet()
    }

    fun batchSetRead(isRead: Boolean) {
        val targetIds = selectedItemIds.value
        if (targetIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            targetIds.forEach { id -> repository.setRead(id, isRead) }
        }
    }

    fun batchDelete() {
        val targetIds = selectedItemIds.value
        if (targetIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            targetIds.forEach { id -> repository.delete(id) }
        }
    }

    fun setRead(id: String, isRead: Boolean) {
        viewModelScope.launch { repository.setRead(id, isRead) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    class Factory(
        private val repository: StashRepository,
        private val settings: StashSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashFeedViewModel(repository, settings) as T
    }
}
