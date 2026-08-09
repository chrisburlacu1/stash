package com.example.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.stash.ui.adaptive.StashAdaptiveLayout
import com.example.stash.ui.theme.StashTheme
import com.example.stash.ai.GeminiNanoSummarizer
import com.example.stash.data.StashRepository
import com.example.stash.data.local.RoomStashRepository
import com.example.stash.data.local.StashDatabase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: StashRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = RoomStashRepository(
            dao = StashDatabase.get(this).stashDao(),
            summarizer = GeminiNanoSummarizer(),
        )
        handleShareIntent(intent)
        setContent {
            StashTheme {
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
                lifecycleScope.launch {
                    repository.addUrl(sharedText)
                }
            }
        }
    }
}
