package dev.cburlacu.stash.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.StashSettings
import dev.cburlacu.stash.data.SummaryEffort
import dev.cburlacu.stash.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val modelVersion: String = "Gemini Nano",
    val summaryEffort: SummaryEffort = SummaryEffort.Medium,
    val modelChoice: ModelChoice = ModelChoice.Automatic,
    val modelOptions: List<ModelOption> = emptyList(),
    val isProbingModels: Boolean = false,
    val isCurating: Boolean = false,
    val curationResult: String? = null,
)

class StashSettingsViewModel(
    private val repository: StashRepository,
    private val settings: StashSettings,
) : ViewModel() {

    private val modelOptions = MutableStateFlow<List<ModelOption>>(emptyList())
    private val isProbingModels = MutableStateFlow(false)
    private val isCurating = MutableStateFlow(false)
    private val curationResult = MutableStateFlow<String?>(null)
    private val modelVersion = MutableStateFlow("Gemini Nano")

    init {
        viewModelScope.launch {
            modelVersion.value = repository.getModelVersion()
        }
    }

    private data class MainSettings(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val summaryEffort: SummaryEffort,
        val modelChoice: ModelChoice,
    )

    private val mainSettings = combine(
        settings.themeMode,
        settings.dynamicColor,
        settings.summaryEffort,
        settings.modelChoice,
    ) { theme, dynamic, effort, choice ->
        MainSettings(theme, dynamic, effort, choice)
    }

    private val auxState = combine(
        modelOptions,
        isProbingModels,
        isCurating,
        curationResult,
    ) { opts, probing, curating, result ->
        SettingsAux(opts, probing, curating, result)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        mainSettings,
        modelVersion,
        auxState,
    ) { main, version, aux ->
        SettingsUiState(
            themeMode = main.themeMode,
            dynamicColor = main.dynamicColor,
            modelVersion = version,
            summaryEffort = main.summaryEffort,
            modelChoice = main.modelChoice,
            modelOptions = aux.opts,
            isProbingModels = aux.probing,
            isCurating = aux.curating,
            curationResult = aux.result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setSummaryEffort(effort: SummaryEffort) {
        viewModelScope.launch { settings.setSummaryEffort(effort) }
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

    fun curateLibrary() {
        if (isCurating.value) return
        viewModelScope.launch {
            isCurating.value = true
            try {
                val count = repository.backfillTopics()
                curationResult.value = if (count > 0) {
                    "Organized $count item${if (count == 1) "" else "s"} into topics"
                } else {
                    "All items already organized!"
                }
            } catch (e: Exception) {
                curationResult.value = "Failed to organize: ${e.message}"
            } finally {
                isCurating.value = false
            }
        }
    }

    fun clearCurationResult() {
        curationResult.value = null
    }

    class Factory(
        private val repository: StashRepository,
        private val settings: StashSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashSettingsViewModel(repository, settings) as T
    }
}

private data class SettingsAux(
    val opts: List<ModelOption>,
    val probing: Boolean,
    val curating: Boolean,
    val result: String?,
)
