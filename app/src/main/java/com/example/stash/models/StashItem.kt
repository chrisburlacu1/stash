package com.example.stash.models

data class StashItem(
    val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val category: String,
    /** Short one-line takeaway for the feed row; [summary] holds the multi-point bullet breakdown. */
    val headline: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    val readTime: String,
    val savedAtEpochMillis: Long,
    val isRead: Boolean = false,
    val aiState: AiState = AiState.Ready,
    /** Absolute path to the cached header image on local disk, or null if none. */
    val imagePath: String? = null,
    /** Scraped page body text saved at save time for full-context on-device chat. */
    val content: String = "",
    /**
     * Dominant colour extracted from the header image at save time (sRGB ARGB int),
     * or 0 if none was extracted. Used by `cardTones()` to theme the card.
     */
    val seedColor: Int = 0,
    /** Vertical crop bias for the header image (-1 top to +1 bottom, 0 centered). */
    val cropBias: Float = 0f,
    /** Primary high-level subject domain (e.g. "Design", "Android", "React", "AI"). */
    val topic: String = "",
)

val StashItem.tag: String get() = category.uppercase()

enum class AiState { Pending, Summarizing, Ready, Unavailable, Failed }
