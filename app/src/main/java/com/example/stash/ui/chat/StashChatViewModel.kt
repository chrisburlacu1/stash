package com.example.stash.ui.chat

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One message in an item chat. Kept in memory only: a chat is a conversation *about* a saved
 * item, not part of the item — persisting transcripts would turn every question into stored data
 * the user never asked to keep.
 */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
)

data class ChatUiState(
    /** Null only until the first Room emission lands. */
    val item: StashItem? = null,
    val messages: List<ChatMessage> = emptyList(),
    /**
     * True from send until the reply finishes streaming — the aura churns exactly while this
     * holds, so the light and the work always agree.
     */
    val isResponding: Boolean = false,
)

class StashChatViewModel(
    private val repository: StashRepository,
    itemId: String,
) : ViewModel() {
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val isResponding = MutableStateFlow(false)
    private var nextMessageId = 0L

    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeItem(itemId),
        messages,
        isResponding,
    ) { item, msgs, responding ->
        ChatUiState(item = item, messages = msgs, isResponding = responding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || isResponding.value) return
        val item = uiState.value.item ?: return

        // Snapshot the history before appending, so the prompt's transcript ends exactly where
        // the new question begins rather than repeating it.
        val history = messages.value.map { ChatTurn(it.fromUser, it.text) }
        messages.update { it + ChatMessage(nextMessageId++, fromUser = true, text = text) }
        isResponding.value = true

        val replyId = nextMessageId++
        viewModelScope.launch {
            var reply = ""
            repository.chat(item, history, text)
                // A failed inference falls through to the blank-reply fallback below rather than
                // crashing the collection — the chat surface has no other error channel.
                .catch { }
                .collect { chunk ->
                    reply += chunk
                    // The reply message is created on the first chunk and grown in place after,
                    // so the transcript streams rather than appearing all at once.
                    messages.update { current ->
                        if (current.lastOrNull()?.id == replyId) {
                            current.dropLast(1) + ChatMessage(replyId, fromUser = false, text = reply)
                        } else {
                            current + ChatMessage(replyId, fromUser = false, text = reply)
                        }
                    }
                }
            if (reply.isBlank()) {
                messages.update {
                    it + ChatMessage(
                        replyId,
                        fromUser = false,
                        text = "The on-device model couldn't answer that. Try rephrasing, or " +
                            "check that Gemini Nano is ready in the model menu.",
                    )
                }
            }
            isResponding.value = false
        }
    }

    class Factory(
        private val repository: StashRepository,
        private val itemId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StashChatViewModel(repository, itemId) as T
    }
}
