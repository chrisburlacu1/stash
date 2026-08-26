package com.example.stash.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stash.ai.ModelOption
import com.example.stash.data.ModelChoice
import com.example.stash.data.FeedView
import com.example.stash.data.SortOrder
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.data.SummaryEffort
import com.example.stash.data.TagCount
import com.example.stash.data.ThemeMode
import com.example.stash.models.StashItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<StashItem> = emptyList(),
    val query: String = "",
    val searchResults: List<StashItem> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val selectedItemIds: Set<String> = emptySet(),
    val tags: List<TagCount> = emptyList(),
    val showAddUrl: Boolean = false,
    val modelVersion: String = "Gemini Nano (ML Kit)",
    val summaryEffort: SummaryEffort = SummaryEffort.Medium,
    val modelChoice: ModelChoice = ModelChoice.Automatic,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val sortOrder: SortOrder = SortOrder.Newest,
    val feedView: FeedView = FeedView.List,
    val modelOptions: List<ModelOption> = emptyList(),
    val isProbingModels: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StashFeedViewModel(
    private val repository: StashRepository,
    private val settings: StashSettings,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val showAddUrl = MutableStateFlow(false)
    private val modelVersion = MutableStateFlow("Gemini Nano (ML Kit)")

    init {
        viewModelScope.launch {
            val saved = settings.modelChoice.first()
            if (saved != ModelChoice.Automatic) {
                runCatching { repository.selectModel(saved) }
            }
            modelVersion.value = repository.getModelVersion()
        }
    }

    private val feedItems = combine(selectedTags, settings.sortOrder) { tags, sort -> tags to sort }
        .flatMapLatest { (tags, sort) ->
            repository.observe(query = "", tags = tags, sortOrder = sort)
        }

    private val searchResults = query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.observe(q, emptySet())
        }

    private val modelOptions = MutableStateFlow<List<ModelOption>>(emptyList())
    private val isProbingModels = MutableStateFlow(false)

    private val chrome = combine(
        combine(
            showAddUrl,
            modelVersion,
            settings.summaryEffort,
            settings.themeMode,
            combine(settings.dynamicColor, settings.feedView) { dynamic, view -> dynamic to view },
        ) { show, version, effort, theme, (dynamic, view) ->
            Prefs(show, version, effort, theme, dynamic, view)
        },
        settings.sortOrder,
        settings.modelChoice,
        modelOptions,
        isProbingModels,
    ) { prefs, sort, choice, options, probing ->
        Chrome(prefs.showAddUrl, prefs.modelVersion, prefs.effort, choice, prefs.themeMode, prefs.dynamicColor, sort, prefs.feedView, options, probing)
    }

    val uiState: StateFlow<FeedUiState> = combine(
        feedItems,
        searchResults,
        repository.observeTags(),
        chrome,
        combine(query, selectedTags, selectedItemIds) { q, t, s -> Triple(q, t, s) },
    ) { items, results, tags, c, (q, t, s) ->
        FeedUiState(
            items = items,
            query = q,
            searchResults = results,
            selectedTags = t,
            selectedItemIds = s,
            tags = tags,
            showAddUrl = c.showAddUrl,
            modelVersion = c.modelVersion,
            summaryEffort = c.effort,
            modelChoice = c.modelChoice,
            themeMode = c.themeMode,
            dynamicColor = c.dynamicColor,
            sortOrder = c.sortOrder,
            feedView = c.feedView,
            modelOptions = c.modelOptions,
            isProbingModels = c.isProbingModels,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    private data class Prefs(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val feedView: FeedView,
    )

    private data class Chrome(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val modelChoice: ModelChoice,
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val sortOrder: SortOrder,
        val feedView: FeedView,
        val modelOptions: List<ModelOption>,
        val isProbingModels: Boolean,
    )

    fun setSummaryEffort(effort: SummaryEffort) {
        viewModelScope.launch { settings.setSummaryEffort(effort) }
    }

    fun refreshModels(force: Boolean = false) {
        if (isProbingModels.value || (modelOptions.value.isNotEmpty() && !force)) return

        viewModelScope.launch {
            isProbingModels.value = true
            try {
                modelOptions.value = repository.probeModels()
            } finally {
                isProbingModels.value = false
            }
        }
    }

    fun setModelChoice(choice: ModelChoice) {
        viewModelScope.launch {
            settings.setModelChoice(choice)
            try {
                repository.selectModel(choice)
                modelVersion.value = repository.getModelVersion()
            } catch (e: Exception) {
                settings.setModelChoice(ModelChoice.Automatic)
                repository.selectModel(ModelChoice.Automatic)
                modelVersion.value = repository.getModelVersion()
            }
            refreshModels(force = true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun toggleTheme() {
        val next = when (uiState.value.themeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        viewModelScope.launch { settings.setThemeMode(next) }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(sortOrder) }
    }

    fun setFeedView(view: FeedView) {
        viewModelScope.launch { settings.setFeedView(view) }
    }

    fun setQuery(value: String) { query.value = value }

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
