package com.example.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.ui.adaptive.StashAdaptiveLayout
import com.example.stash.ui.theme.StashTheme
import com.example.stash.ai.GeminiNanoSummarizer
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.data.ThemeMode
import com.example.stash.data.local.RoomStashRepository
import com.example.stash.data.local.StashDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Outlives any single Activity so an in-flight share survives the user leaving the app. A
 * SupervisorJob keeps one failed save from cancelling the others.
 */
private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Held across Activity instances: the summarizer caches which Gemini Nano variant it resolved to,
 * and rebuilding it per Activity threw that away and re-paid a ~400ms checkStatus() each time.
 */
private var sharedRepository: StashRepository? = null

class MainActivity : ComponentActivity() {
    private lateinit var repository: StashRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = sharedRepository ?: RoomStashRepository(
            dao = StashDatabase.get(applicationContext).stashDao(),
            summarizer = GeminiNanoSummarizer(),
            summaryEffort = StashSettings(applicationContext).summaryEffort,
            // App-private storage: cached header images are not media the user picked, so they
            // stay out of shared collections and are removed with the app.
            imageDir = java.io.File(applicationContext.filesDir, "header_images"),
        ).also { sharedRepository = it }
        handleShareIntent(intent)
        setContent {
            val settings = remember(applicationContext) { StashSettings(applicationContext) }
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            StashTheme(darkTheme = darkTheme) {
                StashAdaptiveLayout(repository)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                // Deliberately NOT lifecycleScope: a save runs ~3s of on-device inference, and a
                // shared link often arrives while the user is on their way back to the other app.
                // Cancelling on destroy left the row stuck on "Summarizing…" forever.
                saveScope.launch {
                    repository.addUrl(sharedText)
                }
            }
        }
    }
}
