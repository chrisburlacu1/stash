package com.example.stash.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stash.ai.ModelOption
import com.example.stash.data.ModelChoice
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
    /** The main feed. Tag filters apply; the search query deliberately does not. */
    val items: List<StashItem> = emptyList(),
    val query: String = "",
    /** Search hits for [query]. Empty while the query is blank — the search surface starts bare. */
    val searchResults: List<StashItem> = emptyList(),
    /** Empty means no filter, i.e. show everything — there is no separate "All" option. */
    val selectedTags: Set<String> = emptySet(),
    val tags: List<TagCount> = emptyList(),
    val showAddUrl: Boolean = false,
    val modelVersion: String = "Gemini Nano (ML Kit)",
    val summaryEffort: SummaryEffort = SummaryEffort.Medium,
    val modelChoice: ModelChoice = ModelChoice.Automatic,
    val themeMode: ThemeMode = ThemeMode.System,
    val sortOrder: SortOrder = SortOrder.Newest,
    /** Empty until the picker is opened — probing costs one IPC round-trip per variant. */
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
    private val showAddUrl = MutableStateFlow(false)
    private val modelVersion = MutableStateFlow("Gemini Nano (ML Kit)")

    init {
        viewModelScope.launch {
            // Apply the user's variant choice before warming up, so warmup hits the model they
            // asked for rather than warming the default and tearing it down on the next line.
            val saved = settings.modelChoice.first()
            if (saved != ModelChoice.Automatic) {
                runCatching { repository.selectModel(saved) }
            }
            modelVersion.value = repository.getModelVersion()
        }
    }

    /**
     * The feed is driven by tag filters and the selected sort order.
     */
    private val feedItems = combine(selectedTags, settings.sortOrder) { tags, sort -> tags to sort }
        .flatMapLatest { (tags, sort) ->
            repository.observe(query = "", tags = tags, sortOrder = sort)
        }

    /**
     * Search results, independent of the feed's tag filter: a search covers the whole stash, not
     * whatever subset the feed happens to be showing. Debounced by 250ms to prevent rapid redundant
     * FTS queries while typing. A blank query short-circuits to empty rather than querying.
     */
    private val searchResults = query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.observe(q, emptySet())
        }

    /** Probed lazily when the model picker opens; see [refreshModels]. */
    private val modelOptions = MutableStateFlow<List<ModelOption>>(emptyList())
    private val isProbingModels = MutableStateFlow(false)

    // Chrome state (dialog, model label, preferences) folded first: combine tops out at five
    // flows and the item data already accounts for four.
    private val chrome = combine(
        combine(
            showAddUrl,
            modelVersion,
            settings.summaryEffort,
            settings.themeMode,
            settings.sortOrder,
        ) { show, version, effort, theme, sort ->
            Prefs(show, version, effort, theme, sort)
        },
        settings.modelChoice,
        modelOptions,
        isProbingModels,
    ) { prefs, choice, options, probing ->
        Chrome(prefs.showAddUrl, prefs.modelVersion, prefs.effort, choice, prefs.themeMode, prefs.sortOrder, options, probing)
    }

    // A flat combine, not flatMapLatest over (query, selectedTags): feedItems and searchResults
    // each re-query off their own trigger, so wrapping them would tear down and resubscribe the
    // feed on every keystroke — exactly the coupling this split removes.
    val uiState: StateFlow<FeedUiState> = combine(
        feedItems,
        searchResults,
        repository.observeTags(),
        chrome,
        combine(query, selectedTags) { q, t -> q to t },
    ) { items, results, tags, c, (q, t) ->
        FeedUiState(
            items = items,
            query = q,
            searchResults = results,
            selectedTags = t,
            tags = tags,
            showAddUrl = c.showAddUrl,
            modelVersion = c.modelVersion,
            summaryEffort = c.effort,
            modelChoice = c.modelChoice,
            themeMode = c.themeMode,
            sortOrder = c.sortOrder,
            modelOptions = c.modelOptions,
            isProbingModels = c.isProbingModels,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    /** The innermost combine's five preference flows, folded so the outer combine stays at four. */
    private data class Prefs(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val themeMode: ThemeMode,
        val sortOrder: SortOrder,
    )

    /** Preference/chrome flows folded together to stay under combine's five-flow ceiling. */
    private data class Chrome(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val modelChoice: ModelChoice,
        val themeMode: ThemeMode,
        val sortOrder: SortOrder,
        val modelOptions: List<ModelOption>,
        val isProbingModels: Boolean,
    )

    /** Applies to the next save, not to existing items. */
    fun setSummaryEffort(effort: SummaryEffort) {
        viewModelScope.launch { settings.setSummaryEffort(effort) }
    }

    /**
     * Probes the device for available model variants. Called when the picker opens rather than at
     * startup: each variant costs a ~330ms checkStatus() IPC, and most sessions never open it.
     * Results are cached in state, so reopening the menu does not re-probe.
     */
    fun refreshModels(force: Boolean = false) {
        // Never run two probes at once; otherwise the cache holds unless the caller forces a retry.
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

    /**
     * Switches the active model variant. Persisted so it survives app restarts; the summarizer
     * is updated immediately and re-warmed in the background.
     */
    fun setModelChoice(choice: ModelChoice) {
        viewModelScope.launch {
            settings.setModelChoice(choice)
            try {
                repository.selectModel(choice)
                modelVersion.value = repository.getModelVersion()
            } catch (e: Exception) {
                // If switching fails (e.g. download missing), fall back to Automatic so the app
                // stays functional rather than wedged on an unresolvable model.
                settings.setModelChoice(ModelChoice.Automatic)
                repository.selectModel(ModelChoice.Automatic)
                modelVersion.value = repository.getModelVersion()
            }
            // Update the probe list so the (Active) label moves immediately.
            refreshModels(force = true)
        }
    }

    /** Cycles System → Light → Dark → System. */
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

    class Factory(
        private val repository: StashRepository,
        private val settings: StashSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashFeedViewModel(repository, settings) as T
    }
}
