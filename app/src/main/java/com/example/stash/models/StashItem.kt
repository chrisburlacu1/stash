package com.example.stash.models

data class StashItem(
    val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val category: String,
    /** Short one-line blurb for the feed row; [summary] is the long form for the detail pane. */
    val headline: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    val readTime: String,
    val savedAtEpochMillis: Long,
    val isRead: Boolean = false,
    val aiState: AiState = AiState.Ready,
    /**
     * Absolute path to the cached header image on local disk, or null when there is none. Always
     * a local file — images are downloaded once at save time so rendering never hits the network.
     */
    val imagePath: String? = null,
)

val StashItem.tag: String get() = category.uppercase()

enum class AiState { Pending, Summarizing, Ready, Unavailable, Failed }
