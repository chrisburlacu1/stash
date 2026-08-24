package com.example.stash.ui.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stash.ai.ChatTurn
import com.example.stash.data.StashRepository
import com.example.stash.models.StashItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BriefingMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
)

data class BriefingUiState(
    val items: List<StashItem> = emptyList(),
    val topic: String? = null,
    val briefingText: String = "",
    val isGeneratingBriefing: Boolean = false,
    val messages: List<BriefingMessage> = emptyList(),
    val isResponding: Boolean = false,
)

class StashBriefingViewModel(
    private val repository: StashRepository,
    private val itemIds: List<String>,
    private val topic: String? = null,
) : ViewModel() {
    private val briefingText = MutableStateFlow("")
    private val isGeneratingBriefing = MutableStateFlow(false)
    private val messages = MutableStateFlow<List<BriefingMessage>>(emptyList())
    private val isResponding = MutableStateFlow(false)
    private var nextMessageId = 0L
    private var hasStartedInitialBriefing = false

    val uiState: StateFlow<BriefingUiState> = combine(
        repository.observeItems(itemIds),
        briefingText,
        isGeneratingBriefing,
        messages,
        isResponding,
    ) { items, brief, generating, msgs, responding ->
        BriefingUiState(
            items = items,
            topic = topic,
            briefingText = brief,
            isGeneratingBriefing = generating,
            messages = msgs,
            isResponding = responding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BriefingUiState(topic = topic))

    init {
        viewModelScope.launch {
            // Wait for initial emission of items from Room
            val items = repository.observeItems(itemIds).first { it.isNotEmpty() }
            if (!hasStartedInitialBriefing) {
                hasStartedInitialBriefing = true
                generateBriefing(items)
            }
        }
    }

    fun generateBriefing(items: List<StashItem> = uiState.value.items) {
        if (items.isEmpty() || isGeneratingBriefing.value) return
        isGeneratingBriefing.value = true
        briefingText.value = ""

        viewModelScope.launch {
            var fullBriefing = ""
            repository.briefing(items = items, topic = topic)
                .catch { }
                .collect { chunk ->
                    fullBriefing += chunk
                    briefingText.value = fullBriefing
                }

            if (fullBriefing.isBlank()) {
                briefingText.value = "Couldn't generate briefing from these items. Check that Gemini Nano is ready in Settings."
            }
            isGeneratingBriefing.value = false
        }
    }

    fun sendQuestion(question: String) {
        val text = question.trim()
        if (text.isEmpty() || isResponding.value || isGeneratingBriefing.value) return
        val items = uiState.value.items
        if (items.isEmpty()) return

        val history = messages.value.map { ChatTurn(it.fromUser, it.text) }
        messages.update { it + BriefingMessage(nextMessageId++, fromUser = true, text = text) }
        isResponding.value = true

        val replyId = nextMessageId++
        viewModelScope.launch {
            var reply = ""
            repository.briefing(
                items = items,
                topic = topic,
                history = history,
                question = text,
            )
                .catch { }
                .collect { chunk ->
                    reply += chunk
                    messages.update { current ->
                        if (current.lastOrNull()?.id == replyId) {
                            current.dropLast(1) + BriefingMessage(replyId, fromUser = false, text = reply)
                        } else {
                            current + BriefingMessage(replyId, fromUser = false, text = reply)
                        }
                    }
                }

            if (reply.isBlank()) {
                messages.update {
                    it + BriefingMessage(
                        replyId,
                        fromUser = false,
                        text = "The on-device model couldn't answer that question.",
                    )
                }
            }
            isResponding.value = false
        }
    }

    class Factory(
        private val repository: StashRepository,
        private val itemIds: List<String>,
        private val topic: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashBriefingViewModel(repository, itemIds, topic) as T
    }
}
