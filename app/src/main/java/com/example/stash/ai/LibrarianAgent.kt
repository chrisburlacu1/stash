package com.example.stash.ai

import com.example.stash.data.local.StashDao
import com.example.stash.data.local.TagNormalizer
import com.example.stash.data.local.TopicBackfillRow
import com.example.stash.util.StashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device background curator that assigns primary topics to items missing one.
 *
 * Runs on app startup (non-blocking) and can be triggered manually from Settings.
 * Uses Gemini Nano to infer a high-level topic from each item's title, summary,
 * and tags — guided by the user's existing library topics for consistency.
 */
class LibrarianAgent(
    private val dao: StashDao,
    private val summarizer: OnDeviceSummarizer,
) {
    /**
     * Backfills topics for all items that have an empty `topic` column.
     *
     * For each item missing a topic, sends its title + summary + tags context
     * to Gemini Nano with known library topics as guidance, then writes the
     * assigned topic back to Room.
     *
     * @return the number of items successfully assigned a topic.
     */
    suspend fun backfillTopics(): Int = withContext(Dispatchers.IO) {
        val availability = summarizer.availability()
        if (availability !is AiAvailability.Available) {
            StashLog.d(TAG, "Skipping topic backfill — AI unavailable")
            return@withContext 0
        }

        val pending = runCatching { dao.rowsForTopicBackfill() }.getOrNull().orEmpty()
        if (pending.isEmpty()) {
            StashLog.d(TAG, "No items need topic backfill")
            return@withContext 0
        }

        // Snapshot existing topics for consistent guidance across the batch
        val existingTopics = dao.allTopics()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        StashLog.d(TAG, "Backfilling topics for ${pending.size} items (${existingTopics.size} known topics)")

        var assigned = 0
        for (row in pending) {
            val topic = inferTopic(row, existingTopics)
            if (topic.isNotBlank()) {
                runCatching { dao.setTopic(row.id, topic) }
                assigned++
            }
        }

        StashLog.d(TAG, "Topic backfill complete: $assigned/${pending.size} items assigned")
        assigned
    }

    /**
     * Infers a topic for a single item. Tries AI inference first,
     * falls back to tag-based heuristic if that fails.
     */
    private suspend fun inferTopic(
        row: TopicBackfillRow,
        knownTopics: List<String>,
    ): String {
        // Try Gemini Nano inference via the interface method
        val inferred = summarizer.inferTopic(
            title = row.title,
            summary = row.summary,
            tags = row.tags,
            knownTopics = knownTopics,
        )

        val normalized = inferred?.let { TagNormalizer.normalize(it) }
            ?.replaceFirstChar(Char::uppercase)
            ?.ifBlank { null }

        if (normalized != null) {
            // Snap to existing topic if close enough for consistency
            return snapToExisting(normalized, knownTopics) ?: normalized
        }

        // Fallback: derive topic from tags heuristically
        return deriveTopicFromTags(row.tags, knownTopics)
    }

    /**
     * Snaps a topic to an existing library topic if they're close enough
     * (case-insensitive match or prefix match).
     */
    private fun snapToExisting(topic: String, knownTopics: List<String>): String? {
        // Exact case-insensitive match
        knownTopics.find { it.equals(topic, ignoreCase = true) }?.let { return it }

        // Prefix match (e.g. "Android Dev" → "Android")
        val topicLower = topic.lowercase()
        knownTopics.find {
            topicLower.startsWith(it.lowercase()) || it.lowercase().startsWith(topicLower)
        }?.let { return it }

        return null
    }

    /**
     * Heuristic fallback: picks the most likely topic from existing tags
     * by matching against known topics.
     */
    private fun deriveTopicFromTags(tags: String, knownTopics: List<String>): String {
        if (tags.isBlank()) return "General"

        val tagList = tags.split(TAG_SEPARATOR).map(String::trim).filter(String::isNotBlank)

        // Check if any tag matches a known topic
        for (tag in tagList) {
            knownTopics.find { it.equals(tag, ignoreCase = true) }?.let { return it }
        }

        // Use the first tag as a broad topic if nothing matches
        return tagList.firstOrNull()
            ?.let { TagNormalizer.normalize(it) }
            ?.replaceFirstChar(Char::uppercase)
            ?: "General"
    }

    companion object {
        private const val TAG = "LibrarianAgent"
        private const val TAG_SEPARATOR = " | "
    }
}
