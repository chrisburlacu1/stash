package com.example.stash.ai

import com.example.stash.data.ModelChoice
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
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

/**
 * What a single [ModelChoice] reports on this device, discovered by building a client for it and
 * calling `checkStatus()`. There is no ML Kit API that lists models, so this is the only way to
 * know — and the answer is device- and enrolment-specific.
 */
enum class ModelStatus {
    /** Weights are on disk; selecting this takes effect immediately. */
    Ready,

    /** Offered for this device but not downloaded yet. Selecting it triggers the download. */
    Downloadable,

    /** Currently fetching its weights. */
    Downloading,

    /** Not offered on this device — usually a missing AICore preview enrolment. */
    Unavailable,
}

/** A [ModelChoice] paired with what it currently reports. */
data class ModelOption(val choice: ModelChoice, val status: ModelStatus)

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
    /**
     * [contentChars] caps how much page text is sent, trading save latency for summary depth.
     *
     * Note there is no `knownTags` parameter: showing the model the existing tag vocabulary made
     * tagging markedly worse. Deduplication against existing tags happens after the fact, in the
     * repository.
     */
    suspend fun organize(
        url: String,
        content: String,
        contentChars: Int = 4_000,
    ): OrganizedContent?
    suspend fun getModelVersion(): String

    /**
     * Probes every [ModelChoice] on this device. Costs one `checkStatus()` IPC per variant
     * (~330ms each), so call it when the picker opens rather than eagerly.
     */
    suspend fun probeModels(): List<ModelOption>

    /**
     * Switches the active variant, closing and re-warming the client. Safe to call with the
     * already-selected choice; it re-resolves rather than assuming anything changed.
     */
    suspend fun selectModel(choice: ModelChoice)
}

data class OrganizedContent(
    val title: String,
    /** One short line for the feed row, so the list never shows a truncated paragraph. */
    val headline: String,
    val summary: String,
    val category: String,
    val tags: List<String>,
)

/**
 * Structured-output schema for a saved link.
 *
 * The `@Guide` descriptions are the same rules the prompt used to state in prose. Stating them here
 * lets the model see a real schema — `enumValues` in particular finally *enforces* the closed
 * category set that was previously only requested, and `minItems`/`maxItems` replace asking nicely
 * for a tag count. A KSP processor (`genai-schema-compiler`) generates the provider that turns this
 * class into that schema, which is why it needs the `ksp(...)` dependency and not just a library.
 *
 * Kept `@Serializable` too: the prompt-JSON path is still the fallback when structured output is
 * unavailable, and it decodes into this same class.
 *
 * Must be public: the generated provider is a public class exposing this type, so `private` or
 * `internal` fails to compile with "public property exposes its internal type argument".
 */
@Serializable
@Generable(description = "Metadata extracted from a saved link so it can be rediscovered later")
data class OrganizedResponse(
    @Guide(description = "The real title of the page or post")
    val title: String,
    @Guide(
        description = "Max 12 words on why this is worth remembering. " +
            "No trailing period. Must not repeat the title.",
    )
    val takeaway: String = "",
    @Guide(
        description = "Short bullets of the actual substance: specifics, names, numbers, " +
            "conclusions. No filler such as 'the article explains the details'.",
        minItems = 3,
        maxItems = 5,
    )
    val keyPoints: List<String> = emptyList(),
    @Guide(
        description = "The content type of the link",
        enumValues = [
            "Article", "Blog", "Tweet", "GitHub Repo",
            "Video", "Discussion", "Documentation", "Website",
        ],
    )
    val category: String = "",
    @Guide(
        description = "Tags naming the actual technologies or topics discussed, using terms that " +
            "appear in the content. No generic words like Development or Design.",
        minItems = 1,
        maxItems = 3,
    )
    val tags: List<String> = emptyList(),
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

    /** The user's pick. [ModelChoice.Automatic] preserves the original probe-and-fall-back path. */
    @Volatile private var selectedChoice: ModelChoice = ModelChoice.Automatic

    /**
     * Builds a client for one explicit variant. Does not check status or cache anything — callers
     * decide what to do with it, and must close it if they are only probing.
     */
    private fun clientFor(choice: ModelChoice): GenerativeModel {
        val (stage, pref) = when (choice) {
            ModelChoice.PreviewFast -> ModelReleaseStage.PREVIEW to ModelPreference.FAST
            ModelChoice.PreviewFull -> ModelReleaseStage.PREVIEW to ModelPreference.FULL
            ModelChoice.StableFast -> ModelReleaseStage.STABLE to ModelPreference.FAST
            ModelChoice.StableFull -> ModelReleaseStage.STABLE to ModelPreference.FULL
            // Automatic has no single config; callers handle it before reaching here.
            ModelChoice.Automatic -> return Generation.getClient()
        }
        return Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = stage
                    preference = pref
                }
            }
        )
    }

    override suspend fun probeModels(): List<ModelOption> = ModelChoice.entries.map { choice ->
        if (choice == ModelChoice.Automatic) {
            // Automatic resolves to whatever is best at save time, so it is always selectable.
            return@map ModelOption(choice, ModelStatus.Ready)
        }
        // A throwaway client per probe: checkStatus() is the only way to ask, and holding these
        // open would leak native resources for variants the user never selects.
        val client = clientFor(choice)
        val status = runCatching { client.checkStatus() }.getOrNull()
        runCatching { client.close() }
        ModelOption(
            choice,
            when (status) {
                FeatureStatus.AVAILABLE -> ModelStatus.Ready
                FeatureStatus.DOWNLOADABLE -> ModelStatus.Downloadable
                FeatureStatus.DOWNLOADING -> ModelStatus.Downloading
                else -> ModelStatus.Unavailable
            },
        )
    }

    override suspend fun selectModel(choice: ModelChoice) {
        modelMutex.withLock {
            selectedChoice = choice
            // The outgoing client holds native resources and its own warmed state; the replacement
            // is a different instance, so both the warmup latch and the availability cache must
            // reset or the new variant inherits claims made about the old one.
            runCatching { resolvedModel?.close() }
            resolvedModel = null
            warmupStarted.set(false)
            knownAvailable = false
            activeModelLabel = "resolving…"
        }
        // Re-resolve outside the lock — model() takes it itself, and re-entering a non-reentrant
        // Mutex here would deadlock. Doing it now rather than lazily means the warmup cost lands
        // on this switch instead of on whichever save comes first.
        runCatching { model() }
            .onFailure { android.util.Log.w(TAG, "re-resolve after model switch failed", it) }
    }

    /**
     * Resolves the client once, on first suspending use. Deliberately NOT a `by lazy` block:
     * selecting the variant needs `checkStatus()`, a ~330ms IPC round-trip, and blocking on that
     * inside `lazy` froze the main thread hard enough to trip "top resumed state loss timeout"
     * before the first frame. The mutex keeps concurrent saves from building two clients.
     */
    private suspend fun model(): GenerativeModel = modelMutex.withLock {
        resolvedModel?.let { return@withLock it }

        // An explicit choice skips the probe-and-fall-back dance entirely: the user asked for a
        // specific variant, so build exactly that. Only Automatic keeps the original behaviour.
        val choice = selectedChoice
        if (choice != ModelChoice.Automatic) {
            val client = clientFor(choice)
            val status = runCatching { client.checkStatus() }.getOrNull()
            if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                // Selecting an undownloaded variant kicks off its fetch. Serving from it anyway is
                // correct — the first inference triggers the download and simply takes longer.
                android.util.Log.d(TAG, "${choice.label} not yet downloaded (status=$status)")
            }
            activeModelLabel = choice.label.lowercase()
            resolvedModel = client
            if (status == FeatureStatus.AVAILABLE) knownAvailable = true
            warmup(client)
            android.util.Log.d(TAG, "model resolved -> $activeModelLabel (explicit)")
            return@withLock client
        }

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

    /**
     * Asks for scannable key points plus one takeaway line, per the "rediscovery" goal in
     * DESIGN.md — a neutral two-sentence abstract was the thing that read as pointless in the feed.
     */
    private fun richPrompt(url: String, content: String): String {
        val knownCategory = categoryForDomain(url)
        val categoryGuidance = if (knownCategory != null) {
            """"category" MUST be exactly "$knownCategory"."""
        } else {
            """"category" MUST be one of: Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website."""
        }
        // Deliberately does NOT list the existing tag vocabulary. Doing so produced badly wrong
        // tags: offering "Android Development, UI/UX, Gemini AI, ..." alongside a Node.js article
        // biased the model into picking from the list rather than reading the content, and a
        // memory-management post came back tagged "Android Development" and "UI/UX". Tags are
        // derived from the content only; RoomStashRepository.reconcileTags() afterwards snaps a
        // new tag onto an existing one when they are near-identical, which is the safe direction
        // to deduplicate in.
        val tagGuidance = "tags: 1-3 tags naming the actual technologies or topics discussed. " +
            "Use terms that appear in the content. No generic words like Development or Design."
        return """
            Summarize this saved link so it can be rediscovered later. Reply with ONLY this JSON:
            {"title":"","takeaway":"","keyPoints":["",""],"category":"","tags":[]}
            title: the real title of the page or post.
            takeaway: max 12 words, why this is worth remembering, no period, must not repeat the title.
            keyPoints: 3 to 5 short bullets of the substance — specifics, names, numbers, conclusions.
            $categoryGuidance
            $tagGuidance
            Use only the content below. Do not use outside knowledge. If it is empty or unclear,
            say so rather than guessing.

            URL: $url
            Content: $content
        """.trimIndent()
    }

    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
    ): OrganizedContent? {
        // No availability() check here: every caller already gates on it, and checkStatus() is
        // a ~330ms IPC round-trip, so repeating it cost that much on every single save.

        val bounded = content.take(contentChars)
        // Structured output is Alpha inside a Beta artifact, so it is tried and not assumed: on
        // failure or where the device does not support it, the prompt-JSON path below still runs.
        structuredOrganize(url, bounded)?.let { return it }
        return promptJsonOrganize(url, bounded)
    }

    /**
     * Asks for the schema directly, so category and tag-count rules are enforced rather than
     * requested, and no JSON is parsed by hand.
     */
    private suspend fun structuredOrganize(url: String, content: String): OrganizedContent? {
        if (structuredSupported == false) return null
        return runCatching {
            val m = model()
            if (structuredSupported == null) {
                structuredSupported = m.isStructuredOutputFeatureAvailable()
                android.util.Log.d(TAG, "structured output available: $structuredSupported")
                if (structuredSupported != true) return null
            }
            val request = generateContentRequest(TextPart(schemaPrompt(url, content))) {}
            val typed = m.generateContent(
                generateTypedContentRequest(
                    generateContentRequest = request,
                    outputClass = OrganizedResponse::class,
                    includeSchemaInPrompt = true,
                )
            )
            typed.candidates.firstOrNull()?.response?.toOrganizedContent(url)
        }.onFailure {
            android.util.Log.w(TAG, "structured output failed, falling back to prompt JSON", it)
        }.getOrNull()
    }

    /** The original path: ask for JSON in prose and parse it. Fallback when the schema path can't run. */
    private suspend fun promptJsonOrganize(url: String, content: String): OrganizedContent? {
        val prompt = richPrompt(url, content)
        return runCatching {
            val raw = model().generateContent(prompt).candidates.firstOrNull()?.text?.trim().orEmpty()
            val jsonText = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            jsonParser.decodeFromString<OrganizedResponse>(jsonText).toOrganizedContent(url)
        }.getOrNull()
    }

    /** Shared mapping so both paths normalise identically. */
    private fun OrganizedResponse.toOrganizedContent(url: String): OrganizedContent {
        val points = keyPoints.map(String::trim).filter(String::isNotBlank)
        return OrganizedContent(
            title = title.trim(),
            // Falls back to the first key point when the model omits the takeaway, so the
            // feed row still gets a short line.
            headline = takeaway.trim()
                .ifBlank { points.firstOrNull().orEmpty() }
                .removeSuffix(".")
                .take(90),
            // Bullets are stored as newline-separated text: the detail pane renders them
            // as a list, and FTS still indexes every point for search.
            summary = points.joinToString("\n") { it.removePrefix("- ").trim() },
            // The domain is ground truth where we have it; the model otherwise labels
            // short social posts as "Article" because they read like prose.
            category = categoryForDomain(url)
                ?: category.trim().take(32).ifBlank { "Unsorted" },
            tags = tags.map(String::trim).filter(String::isNotBlank).distinct().take(8),
        )
    }

    /**
     * Null until probed, then cached: `isStructuredOutputFeatureAvailable()` is an IPC call and the
     * answer cannot change while the process lives.
     */
    @Volatile private var structuredSupported: Boolean? = null

    /**
     * Prompt for the structured path. Deliberately shorter than [richPrompt] — the field rules now
     * live in the schema's `@Guide` descriptions, so repeating them here would only spend tokens.
     */
    private fun schemaPrompt(url: String, content: String): String = """
        Summarize this saved link so it can be rediscovered later.
        Use only the content below. Do not use outside knowledge.

        URL: $url
        Content: $content
    """.trimIndent()
}
