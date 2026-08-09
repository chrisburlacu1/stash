package com.example.stash.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "StashSummarizer"

sealed interface AiAvailability {
    data object Available : AiAvailability
    data object Unavailable : AiAvailability
}

/** Domains whose content type is unambiguous, so the model never gets to guess it wrong. */
private val DOMAIN_CATEGORIES = mapOf(
    "x.com" to "Tweet",
    "twitter.com" to "Tweet",
    "bsky.app" to "Tweet",
    "threads.net" to "Tweet",
    "mastodon.social" to "Tweet",
    "github.com" to "GitHub Repo",
    "gitlab.com" to "GitHub Repo",
    "youtube.com" to "Video",
    "youtu.be" to "Video",
    "vimeo.com" to "Video",
    "reddit.com" to "Discussion",
    "news.ycombinator.com" to "Discussion",
    "stackoverflow.com" to "Discussion",
)

internal fun categoryForDomain(url: String): String? {
    val host = runCatching { java.net.URI(url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.lowercase()
        ?: return null
    return DOMAIN_CATEGORIES[host]
        ?: DOMAIN_CATEGORIES.entries.firstOrNull { host.endsWith(".${it.key}") }?.value
}

interface OnDeviceSummarizer {
    suspend fun availability(): AiAvailability
    suspend fun organize(url: String, content: String, knownTags: List<String>): OrganizedContent?
    suspend fun getModelVersion(): String
}

data class OrganizedContent(
    val title: String,
    /** One short line for the feed row, so the list never shows a truncated paragraph. */
    val headline: String,
    val summary: String,
    val category: String,
    val tags: List<String>,
)

@Serializable
private data class OrganizedResponse(
    val title: String,
    val headline: String = "",
    val summary: String,
    val category: String,
    val tags: List<String>,
)

class GeminiNanoSummarizer : OnDeviceSummarizer {
    /**
     * The default client is `STABLE` + `FULL` (accuracy-first), which measured at 8-9s per
     * summary on a Pixel 10 Pro XL — inference was ~92% of the whole save. `FAST` is the
     * variant the docs recommend for "latency-sensitive apps", and it only exists on the
     * `PREVIEW` release stage, which requires AICore Developer Preview enrolment and is not
     * on every device. So we ask for PREVIEW+FAST, verify it actually reports AVAILABLE, and
     * fall back to the default client when it does not.
     */
    private val modelMutex = Mutex()
    private var resolvedModel: GenerativeModel? = null

    /** Which variant [model] resolved to, for display in the top bar. */
    private var activeModelLabel: String = "resolving…"

    /**
     * Resolves the client once, on first suspending use. Deliberately NOT a `by lazy` block:
     * selecting the variant needs `checkStatus()`, a ~330ms IPC round-trip, and blocking on that
     * inside `lazy` froze the main thread hard enough to trip "top resumed state loss timeout"
     * before the first frame. The mutex keeps concurrent saves from building two clients.
     */
    private suspend fun model(): GenerativeModel = modelMutex.withLock {
        resolvedModel?.let { return@withLock it }

        val fast = Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = ModelReleaseStage.PREVIEW
                    preference = ModelPreference.FAST
                }
            }
        )
        val fastStatus = runCatching { fast.checkStatus() }.getOrNull()
        val chosen = when (fastStatus) {
            FeatureStatus.AVAILABLE -> {
                activeModelLabel = "preview/fast"
                fast
            }
            // The variant is offered for this device/enrolment but its weights are not on disk.
            // Fetch them in the background and keep serving from stable/full meanwhile; the next
            // resolve (after `reset()`, or the next process) picks up the fast client.
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                startFastDownload(fast)
                activeModelLabel = "stable/full · fast downloading"
                Generation.getClient()
            }
            else -> {
                runCatching { fast.close() }
                android.util.Log.d(TAG, "preview/fast unavailable (status=$fastStatus), using stable/full")
                activeModelLabel = "stable/full"
                Generation.getClient()
            }
        }
        android.util.Log.d(TAG, "model resolved -> $activeModelLabel")
        resolvedModel = chosen
        // checkStatus() answered AVAILABLE to get here, so cache it: it is a ~400ms IPC call and
        // was previously re-paid on every single save.
        if (fastStatus == FeatureStatus.AVAILABLE) knownAvailable = true
        warmup(chosen)
        chosen
    }

    /**
     * Preloads the model so the first real save does not pay for it. Measured: the first inference
     * after a variant becomes active took 10.6s versus ~2.3s for every subsequent one, so this is
     * worth ~8s on the first save. Fire-and-forget — a save that arrives mid-warmup simply waits
     * on the same underlying model rather than being blocked by us.
     */
    private fun warmup(model: GenerativeModel) {
        if (!warmupStarted.compareAndSet(false, true)) return
        downloadScope.launch {
            runCatching { model.warmup() }
                .onFailure { android.util.Log.w(TAG, "warmup failed", it) }
        }
    }

    private val warmupStarted = AtomicBoolean(false)

    /**
     * Set once checkStatus() has reported AVAILABLE. Availability does not flip back while the
     * process lives — a model cannot un-download itself — so re-querying per save was pure cost.
     */
    @Volatile private var knownAvailable = false

    /**
     * Downloads the PREVIEW/FAST weights without blocking saves. Runs on its own scope because it
     * outlives the caller's coroutine, and closes the client on any terminal state so a failed
     * download does not leak it.
     */
    private fun startFastDownload(fast: GenerativeModel) {
        if (!fastDownloadStarted.compareAndSet(false, true)) return
        downloadScope.launch {
            runCatching {
                fast.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted ->
                            android.util.Log.d(TAG, "fast download started: ${status.bytesToDownload / 1_048_576}MB")
                        is DownloadStatus.DownloadProgress ->
                            android.util.Log.d(TAG, "fast download progress: ${status.totalBytesDownloaded / 1_048_576}MB")
                        is DownloadStatus.DownloadCompleted -> {
                            android.util.Log.d(TAG, "fast download COMPLETE — will be used after reset()/restart")
                            fastDownloadComplete = true
                        }
                        is DownloadStatus.DownloadFailed ->
                            android.util.Log.w(TAG, "fast download FAILED", status.e)
                        else -> android.util.Log.d(TAG, "fast download status: $status")
                    }
                }
            }.onFailure { android.util.Log.w(TAG, "fast download threw", it) }
            runCatching { fast.close() }
        }
    }

    /** True once the fast weights finished downloading; [reset] then swaps the client over. */
    @Volatile private var fastDownloadComplete = false
    private val fastDownloadStarted = AtomicBoolean(false)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Drops the cached client so the next use re-resolves the variant. Call after the fast weights
     * land, to switch over without restarting the process.
     */
    suspend fun resetIfFastReady(): Boolean = modelMutex.withLock {
        if (!fastDownloadComplete || resolvedModel == null) return@withLock false
        runCatching { resolvedModel?.close() }
        resolvedModel = null
        fastDownloadComplete = false
        // The replacement client is a different model instance, so it needs its own warmup and
        // availability check rather than inheriting the outgoing one's.
        warmupStarted.set(false)
        knownAvailable = false
        true
    }

    /** Releases the active client's native resources. */
    fun close() {
        runCatching { resolvedModel?.close() }
        resolvedModel = null
        downloadScope.cancel()
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun getModelVersion(): String = runCatching {
        // Resolve first so activeModelLabel is populated before it is read.
        val base = model().getBaseModelName()
        "$base · $activeModelLabel"
    }.getOrDefault("Gemini Nano")

    override suspend fun availability(): AiAvailability = runCatching {
        val model = model()
        // Resolving the model already established availability, and a model cannot become
        // unavailable while the process lives, so skip the ~400ms IPC round-trip on every save
        // after the first.
        if (knownAvailable) return@runCatching AiAvailability.Available

        val status = model.checkStatus()
        if (status == FeatureStatus.AVAILABLE) {
            knownAvailable = true
            AiAvailability.Available
        } else if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            // Not cached: these are transitional, and the next save may find it ready.
            AiAvailability.Available
        } else {
            AiAvailability.Unavailable
        }
    }.getOrDefault(AiAvailability.Unavailable)

    override suspend fun organize(
        url: String,
        content: String,
        knownTags: List<String>,
    ): OrganizedContent? {
        // No availability() check here: every caller already gates on it, and checkStatus() is
        // a ~330ms IPC round-trip, so repeating it cost that much on every single save.

        // Measured: prompt length is NOT the driver of inference time — a 1,314-char prompt took
        // longer than a 2,072-char one. The cap stays as a guard against pathological pages, not
        // as a latency lever.
        val boundedContent = content.take(1_200)

        // categoryForDomain() settles known hosts deterministically, so the prompt only needs
        // the category list — the per-domain rules would be tokens spent re-deriving it.
        val knownCategory = categoryForDomain(url)
        val categoryGuidance = if (knownCategory != null) {
            """"category" MUST be exactly "$knownCategory"."""
        } else {
            """"category" MUST be one of: Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website."""
        }

        // Only the closest existing tags are offered; sending the whole vocabulary grows the
        // prompt without improving reuse.
        val tagGuidance = if (knownTags.isEmpty()) {
            "Give 1-3 broad topic tags."
        } else {
            "Give 1-3 broad topic tags, reusing these exactly where they fit: " +
                knownTags.take(12).joinToString(", ")
        }

        val prompt = """
            Summarize this saved link. Reply with ONLY this JSON:
            {"title":"","headline":"","summary":"","category":"","tags":[]}
            title: the real title of the page or post.
            headline: max 10 words, no period, must not repeat the title.
            summary: two sentences.
            $categoryGuidance
            $tagGuidance
            Use only the content below. If it is empty or unclear, say so rather than guessing.

            URL: $url
            Content: $boundedContent
        """.trimIndent()
        return runCatching {
            val raw = model().generateContent(prompt).candidates.firstOrNull()?.text?.trim().orEmpty()
            val jsonText = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            jsonParser.decodeFromString<OrganizedResponse>(jsonText).let { response ->
                OrganizedContent(
                    title = response.title.trim(),
                    // Falls back to the first sentence of the summary when the model omits or
                    // over-runs the headline, so the feed still gets a short line.
                    headline = response.headline.trim()
                        .ifBlank { response.summary.trim().substringBefore('.') }
                        .removeSuffix(".")
                        .take(90),
                    summary = response.summary.trim(),
                    // The domain is ground truth where we have it; the model otherwise labels
                    // short social posts as "Article" because they read like prose.
                    category = categoryForDomain(url)
                        ?: response.category.trim().take(32).ifBlank { "Unsorted" },
                    tags = response.tags.map(String::trim).filter(String::isNotBlank).distinct().take(8),
                )
            }
        }.getOrNull()
    }
}
