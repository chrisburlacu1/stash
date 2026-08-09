package com.example.stash.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

    private companion object {
        val UseCardLayout = booleanPreferencesKey("use_card_layout")
    }
}
