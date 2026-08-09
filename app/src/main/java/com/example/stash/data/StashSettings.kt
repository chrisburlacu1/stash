package com.example.stash.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** How a saved item is drawn in the feed. Both layouts are supported; this is a user preference. */
enum class FeedLayout {
    /** Dense single-line-per-field rows — most items visible per screen. */
    Compact,

    /** Keep-style cards: large title, summary, and a distinct link strip at the bottom. */
    Card,
}

/**
 * How much page text to hand the summarizer, trading save time against summary depth.
 *
 * The character counts come from measuring the active Gemini Nano variant on a real article
 * (Pixel 10 Pro XL): quality climbs steeply to ~2,500 chars as concrete numbers and named
 * entities start appearing, then tapers, while latency keeps rising roughly linearly at
 * ~2.7ms per 100 chars. [High] is genuinely more detailed, not just slower.
 */
enum class SummaryEffort(val contentChars: Int, val label: String) {
    /** ~3.3s per save. Key specifics, no padding. */
    Low(2_500, "Low"),

    /** ~4.0s per save. Adds supporting detail and comparisons. */
    Medium(4_000, "Medium"),

    /** ~5.7s per save. Fullest bullets — most names, numbers and conclusions. */
    High(8_000, "High"),
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stash_settings")

/**
 * User preferences, backed by DataStore. Reads are a Flow so the UI updates without re-reading,
 * and writes are suspending — no `commit()`-on-main-thread trap.
 */
class StashSettings(private val context: Context) {

    val feedLayout: Flow<FeedLayout> = context.dataStore.data
        // A corrupt or unreadable preferences file should fall back to the default layout rather
        // than taking the feed down with it.
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            if (prefs[UseCardLayout] == true) FeedLayout.Card else FeedLayout.Compact
        }

    suspend fun setFeedLayout(layout: FeedLayout) {
        context.dataStore.edit { prefs ->
            prefs[UseCardLayout] = layout == FeedLayout.Card
        }
    }

    val summaryEffort: Flow<SummaryEffort> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            // Stored by name so adding or reordering levels later cannot silently reinterpret an
            // existing preference the way an ordinal would.
            SummaryEffort.entries.firstOrNull { it.name == prefs[SummaryEffortKey] }
                ?: SummaryEffort.Medium
        }

    suspend fun setSummaryEffort(effort: SummaryEffort) {
        context.dataStore.edit { prefs ->
            prefs[SummaryEffortKey] = effort.name
        }
    }

    private companion object {
        val UseCardLayout = booleanPreferencesKey("use_card_layout")
        val SummaryEffortKey = stringPreferencesKey("summary_effort")
    }
}
