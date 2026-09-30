package dev.cburlacu.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.ThemeMode
import dev.cburlacu.stash.data.extract.extractFirstUrl
import dev.cburlacu.stash.ui.adaptive.StashAdaptiveLayout
import dev.cburlacu.stash.ui.theme.StashTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: StashApplication get() = application as StashApplication
    private val repository: StashRepository get() = app.repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)
        setContent {
            val settings = app.settings
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
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val urlToSave = extractFirstUrl(sharedText)
                app.applicationScope.launch {
                    repository.addUrl(urlToSave)
                }
            }
        }
    }
}
