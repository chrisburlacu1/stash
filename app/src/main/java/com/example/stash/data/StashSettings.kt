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

/**
 * Display theme. [System] follows the device setting; [Light]/[Dark] pin it regardless.
 */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Which Gemini Nano variant to use.
 *
 * ML Kit has no API that enumerates models: `ModelReleaseStage` and `ModelPreference` are
 * `@IntDef` annotation constants, not enums, so the full set is fixed at compile time and there
 * are exactly four combinations. Availability is discovered per-combination by building a client
 * and calling `checkStatus()` — see `GeminiNanoSummarizer.probeModels()`.
 *
 * [Automatic] is the historical behaviour and stays the default: prefer preview/fast, fall back to
 * stable/full where the preview stage is not enrolled.
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
 * User preferences, backed by DataStore. Reads are a Flow so the UI updates without re-reading,
 * and writes are suspending — no `commit()`-on-main-thread trap.
 */
class StashSettings(private val context: Context) {

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

    /**
     * Whether [StashTheme][com.example.stash.ui.theme.StashTheme] derives its `ColorScheme` from
     * the device wallpaper (Material You) instead of the app's own bespoke seed. Off by default:
     * the seed is a deliberate brand choice, and wallpaper-derived colour should be something the
     * user opts into rather than a surprise on first launch.
     *
     * Stored as a boolean rather than name-keyed, unlike [themeMode]/[modelChoice] — those are
     * enums, where storing by name protects a future reorder from silently reinterpreting a stored
     * ordinal. A boolean has no such failure mode, so `booleanPreferencesKey` is the plain choice.
     */
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[DynamicColorKey] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DynamicColorKey] = enabled
        }
    }

    private companion object {
        // "use_card_layout" was written here when the feed had a compact/card toggle. It is left
        // in DataStore rather than migrated away — an orphaned boolean costs nothing, and nothing
        // reads it.
        val SummaryEffortKey = stringPreferencesKey("summary_effort")
        val ModelChoiceKey = stringPreferencesKey("model_choice")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val DynamicColorKey = booleanPreferencesKey("dynamic_color")
    }
}
