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
import java.io.File

private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            imageDir = File(applicationContext.filesDir, "header_images"),
        ).also { sharedRepository = it }

        (repository as? RoomStashRepository)?.let { room ->
            saveScope.launch {
                room.backfillSeedColors()
                room.backfillNormalizedTags()
            }
        }

        handleShareIntent(intent)
        setContent {
            val settings = remember(applicationContext) { StashSettings(applicationContext) }
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(true)
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            StashTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
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
                saveScope.launch {
                    repository.addUrl(sharedText)
                }
            }
        }
    }
}
