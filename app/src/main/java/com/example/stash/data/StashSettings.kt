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

/**
 * How much scraped page text to feed the summarizer, balancing inference time against summary depth.
 */
enum class SummaryEffort(val contentChars: Int, val label: String) {
    Low(2_500, "Low"),
    Medium(4_000, "Medium"),
    High(8_000, "High"),
}

/**
 * Display theme mode.
 */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Feed sorting preference.
 */
enum class SortOrder(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    UnreadFirst("Unread first"),
}

/**
 * Feed layout mode.
 */
enum class FeedView(val label: String) {
    List("List"),
    Gallery("Gallery"),
}

/**
 * Which Gemini Nano variant to use on device.
 */
enum class ModelChoice(val label: String, val description: String) {
    Automatic("Automatic", "Fastest available"),
    PreviewFast("Preview · Fast", "Lowest latency, needs AICore preview"),
    PreviewFull("Preview · Full", "Preview weights, accuracy-first"),
    StableFast("Stable · Fast", "Latency-first on the stable channel"),
    StableFull("Stable · Full", "Most accurate, slowest"),
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stash_settings")

/**
 * User preferences backed by DataStore.
 */
class StashSettings(private val context: Context) {

    val summaryEffort: Flow<SummaryEffort> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SummaryEffort.entries.firstOrNull { it.name == prefs[SummaryEffortKey] }
                ?: SummaryEffort.Medium
        }

    suspend fun setSummaryEffort(effort: SummaryEffort) {
        context.dataStore.edit { prefs ->
            prefs[SummaryEffortKey] = effort.name
        }
    }

    val modelChoice: Flow<ModelChoice> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ModelChoice.entries.firstOrNull { it.name == prefs[ModelChoiceKey] }
                ?: ModelChoice.Automatic
        }

    suspend fun setModelChoice(choice: ModelChoice) {
        context.dataStore.edit { prefs ->
            prefs[ModelChoiceKey] = choice.name
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ThemeMode.entries.firstOrNull { it.name == prefs[ThemeModeKey] } ?: ThemeMode.System
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[ThemeModeKey] = mode.name
        }
    }

    val sortOrder: Flow<SortOrder> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            SortOrder.entries.firstOrNull { it.name == prefs[SortOrderKey] } ?: SortOrder.Newest
        }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[SortOrderKey] = order.name
        }
    }

    val feedView: Flow<FeedView> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            FeedView.entries.firstOrNull { it.name == prefs[FeedViewKey] } ?: FeedView.List
        }

    suspend fun setFeedView(view: FeedView) {
        context.dataStore.edit { prefs ->
            prefs[FeedViewKey] = view.name
        }
    }

    /**
     * Whether to derive theme colors dynamically from the device wallpaper (Material You)
     * or use Stash's default brand palette. Enabled by default.
     */
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[DynamicColorKey] ?: true }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DynamicColorKey] = enabled
        }
    }

    private companion object {
        val SummaryEffortKey = stringPreferencesKey("summary_effort")
        val FeedViewKey = stringPreferencesKey("feed_view")
        val ModelChoiceKey = stringPreferencesKey("model_choice")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val SortOrderKey = stringPreferencesKey("sort_order")
        val DynamicColorKey = booleanPreferencesKey("dynamic_color")
    }
}
