package dev.cburlacu.stash

import android.app.Application
import dev.cburlacu.stash.ai.GeminiNanoSummarizer
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.StashSettings
import dev.cburlacu.stash.data.local.RoomStashRepository
import dev.cburlacu.stash.data.local.StashDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StashApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: StashRepository by lazy {
        RoomStashRepository(
            dao = StashDatabase.get(this).stashDao(),
            summarizer = GeminiNanoSummarizer(),
            summaryEffort = StashSettings(this).summaryEffort,
            imageDir = File(filesDir, "header_images"),
        ).also { repo ->
            applicationScope.launch {
                repo.backfillSeedColors()
                repo.backfillNormalizedTags()
                repo.backfillTwitterImages()
                repo.backfillTopics()
            }
        }
    }
}
