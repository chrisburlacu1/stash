package com.example.stash.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stash.ai.ModelOption
import com.example.stash.data.ModelChoice
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.data.SummaryEffort
import com.example.stash.data.ThemeMode
import com.example.stash.models.StashItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val tags: List<String> = emptyList(),
    val showAddUrl: Boolean = false,
    val modelVersion: String = "Gemini Nano (ML Kit)",
    val summaryEffort: SummaryEffort = SummaryEffort.Medium,
    val modelChoice: ModelChoice = ModelChoice.Automatic,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Empty until the picker is opened — probing costs one IPC round-trip per variant. */
    val modelOptions: List<ModelOption> = emptyList(),
    val isProbingModels: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StashFeedViewModel(
    private val repository: StashRepository,
    private val settings: StashSettings,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val showAddUrl = MutableStateFlow(false)
    private val modelVersion = MutableStateFlow("Gemini Nano")

    init {
        viewModelScope.launch {
            // Apply the persisted choice before reading the version, so the label describes the
            // variant the user actually picked rather than whatever Automatic would have resolved.
            // Automatic is the default and already the summarizer's own behaviour, so skip it and
            // avoid forcing an eager resolve on every cold start.
            val saved = settings.modelChoice.first()
            if (saved != ModelChoice.Automatic) {
                runCatching { repository.selectModel(saved) }
            }
            modelVersion.value = repository.getModelVersion()
        }
    }

    /**
     * The feed is driven by tag filters only. Typing in the search bar must not disturb it — the
     * search surface is its own screen now, not a filter over this list.
     */
    private val feedItems = selectedTags.flatMapLatest { tags ->
        repository.observe(query = "", tags = tags)
    }

    /**
     * Search results, independent of the feed's tag filter: a search covers the whole stash, not
     * whatever subset the feed happens to be showing. A blank query short-circuits to empty rather
     * than querying, since the repository treats "" as "everything" and the search surface should
     * stay bare until something is typed.
     */
    private val searchResults = query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else repository.observe(q, emptySet())
    }

    /** Probed lazily when the model picker opens; see [refreshModels]. */
    private val modelOptions = MutableStateFlow<List<ModelOption>>(emptyList())
    private val isProbingModels = MutableStateFlow(false)

    // Chrome state (dialog, model label, preferences) folded first: combine tops out at five
    // flows and the item data already accounts for four.
    private val chrome = combine(
        combine(showAddUrl, modelVersion, settings.summaryEffort, settings.themeMode) { show, version, effort, theme ->
            Prefs(show, version, effort, theme)
        },
        settings.modelChoice,
        modelOptions,
        isProbingModels,
    ) { prefs, choice, options, probing ->
        Chrome(prefs.showAddUrl, prefs.modelVersion, prefs.effort, choice, prefs.themeMode, options, probing)
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
            modelOptions = c.modelOptions,
            isProbingModels = c.isProbingModels,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    /** The innermost combine's four preference flows, folded so the outer combine stays at four. */
    private data class Prefs(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val themeMode: ThemeMode,
    )

    /** Preference/chrome flows folded together to stay under combine's five-flow ceiling. */
    private data class Chrome(
        val showAddUrl: Boolean,
        val modelVersion: String,
        val effort: SummaryEffort,
        val modelChoice: ModelChoice,
        val themeMode: ThemeMode,
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
        //
        // The empty case matters: a probe whose checkStatus() calls all threw returns an empty
        // list, which used to be cached exactly like a successful result. The menu then had nothing
        // to render and no way to recover for the rest of the process lifetime — reopening it hit
        // the `isNotEmpty()` guard and returned early. An empty result is a *failure*, not an
        // answer, so it must not be cached.
        if (isProbingModels.value) return
        if (!force && modelOptions.value.isNotEmpty()) return
        viewModelScope.launch {
            isProbingModels.value = true
            modelOptions.value = runCatching { repository.probeModels() }.getOrDefault(emptyList())
            isProbingModels.value = false
        }
    }

    /**
     * Switches the active model. Persists the choice first so it survives a restart even if the
     * re-resolve below fails, then swaps the live client — which closes the old one and warms the
     * new, so the next save does not pay the ~10s cold-inference cost.
     */
    fun selectModel(choice: ModelChoice) {
        viewModelScope.launch {
            settings.setModelChoice(choice)
            runCatching { repository.selectModel(choice) }
            // The label is derived from the resolved client, so refresh it after the swap.
            modelVersion.value = runCatching { repository.getModelVersion() }
                .getOrDefault(modelVersion.value)
        }
    }

    /** Cycles System → Light → Dark → System, so one tap always has a next state to land on. */
    fun toggleTheme() {
        viewModelScope.launch {
            val next = when (settings.themeMode.first()) {
                ThemeMode.System -> ThemeMode.Light
                ThemeMode.Light -> ThemeMode.Dark
                ThemeMode.Dark -> ThemeMode.System
            }
            settings.setThemeMode(next)
        }
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
