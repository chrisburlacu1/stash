package com.example.stash.ai

import com.example.stash.data.ModelChoice
import com.example.stash.util.StashLog
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "StashSummarizer"
private const val MAX_CHAT_HISTORY_TURNS = 12

sealed interface AiAvailability {
    data object Available : AiAvailability
    data object Unavailable : AiAvailability
}

enum class ModelStatus {
    Ready,
    Downloadable,
    Downloading,
    Unavailable,
}

data class ModelOption(val choice: ModelChoice, val status: ModelStatus)

private val DOMAIN_CATEGORIES = mapOf(
    "x.com" to "Discussion",
    "twitter.com" to "Discussion",
    "bsky.app" to "Discussion",
    "threads.net" to "Discussion",
    "mastodon.social" to "Discussion",
    "github.com" to "Repo",
    "gitlab.com" to "Repo",
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

data class ChatTurn(val fromUser: Boolean, val text: String)

interface OnDeviceSummarizer {
    suspend fun availability(): AiAvailability
    suspend fun organize(
        url: String,
        content: String,
        contentChars: Int = 4_000,
        knownTags: List<String> = emptyList(),
    ): OrganizedContent?
    suspend fun getModelVersion(): String
    suspend fun probeModels(): List<ModelOption>
    suspend fun selectModel(choice: ModelChoice)
    fun chatStream(itemContext: String, history: List<ChatTurn>, question: String): Flow<String>
    fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String? = null,
        history: List<ChatTurn> = emptyList(),
        question: String? = null,
    ): Flow<String>
}

data class OrganizedContent(
    val title: String,
    val headline: String,
    val summary: String,
    val category: String,
    val tags: List<String>,
)

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
            "Article", "Documentation", "Repo", "Video", "Discussion",
        ],
    )
    val category: String = "",
    @Guide(
        description = "3 to 6 descriptive tags for the specific tools, libraries, technologies, " +
            "frameworks, and key topics discussed in the content (1 to 3 words per tag). " +
            "Use clear, standard terminology.",
        minItems = 2,
        maxItems = 6,
    )
    val tags: List<String> = emptyList(),
)

class GeminiNanoSummarizer : OnDeviceSummarizer {
    private val modelMutex = Mutex()
    private var resolvedModel: GenerativeModel? = null
    private var activeModelLabel: String = "resolving…"
    @Volatile private var selectedChoice: ModelChoice = ModelChoice.Automatic
    private val warmupStarted = AtomicBoolean(false)
    @Volatile private var knownAvailable = false
    @Volatile private var fastDownloadComplete = false
    private val fastDownloadStarted = AtomicBoolean(false)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var structuredSupported: Boolean? = null
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun clientFor(choice: ModelChoice): GenerativeModel {
        val (stage, pref) = when (choice) {
            ModelChoice.PreviewFast -> ModelReleaseStage.PREVIEW to ModelPreference.FAST
            ModelChoice.PreviewFull -> ModelReleaseStage.PREVIEW to ModelPreference.FULL
            ModelChoice.StableFast -> ModelReleaseStage.STABLE to ModelPreference.FAST
            ModelChoice.StableFull -> ModelReleaseStage.STABLE to ModelPreference.FULL
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
            return@map ModelOption(choice, ModelStatus.Ready)
        }
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
            runCatching { resolvedModel?.close() }
            resolvedModel = null
            warmupStarted.set(false)
            knownAvailable = false
            activeModelLabel = "resolving…"
        }
        runCatching { model() }
            .onFailure { StashLog.w(TAG, "re-resolve after model switch failed", it) }
    }

    private suspend fun model(): GenerativeModel = modelMutex.withLock {
        resolvedModel?.let { return@withLock it }

        val choice = selectedChoice
        if (choice != ModelChoice.Automatic) {
            val client = clientFor(choice)
            val status = runCatching { client.checkStatus() }.getOrNull()
            if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                StashLog.d(TAG, "${choice.label} not yet downloaded (status=$status)")
            }
            activeModelLabel = choice.label.lowercase()
            resolvedModel = client
            if (status == FeatureStatus.AVAILABLE) knownAvailable = true
            warmup(client)
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
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                startFastDownload(fast)
                activeModelLabel = "stable/full · fast downloading"
                Generation.getClient()
            }
            else -> {
                runCatching { fast.close() }
                activeModelLabel = "stable/full"
                Generation.getClient()
            }
        }
        resolvedModel = chosen
        if (fastStatus == FeatureStatus.AVAILABLE) knownAvailable = true
        warmup(chosen)
        chosen
    }

    private fun warmup(model: GenerativeModel) {
        if (!warmupStarted.compareAndSet(false, true)) return
        downloadScope.launch {
            runCatching { model.warmup() }
                .onFailure { StashLog.w(TAG, "warmup failed", it) }
        }
    }

    private fun startFastDownload(fast: GenerativeModel) {
        if (!fastDownloadStarted.compareAndSet(false, true)) return
        downloadScope.launch {
            runCatching {
                fast.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted ->
                            StashLog.d(TAG, "fast download started: ${status.bytesToDownload / 1_048_576}MB")
                        is DownloadStatus.DownloadProgress ->
                            StashLog.d(TAG, "fast download progress: ${status.totalBytesDownloaded / 1_048_576}MB")
                        is DownloadStatus.DownloadCompleted -> {
                            StashLog.d(TAG, "fast download complete")
                            fastDownloadComplete = true
                        }
                        is DownloadStatus.DownloadFailed ->
                            StashLog.w(TAG, "fast download failed", status.e)
                    }
                }
            }.onFailure { StashLog.w(TAG, "fast download threw", it) }
            runCatching { fast.close() }
        }
    }

    suspend fun resetIfFastReady(): Boolean = modelMutex.withLock {
        if (!fastDownloadComplete || resolvedModel == null) return@withLock false
        runCatching { resolvedModel?.close() }
        resolvedModel = null
        fastDownloadComplete = false
        warmupStarted.set(false)
        knownAvailable = false
        true
    }

    fun close() {
        runCatching { resolvedModel?.close() }
        resolvedModel = null
        downloadScope.cancel()
    }

    override suspend fun getModelVersion(): String = runCatching {
        val base = model().getBaseModelName()
        "$base · $activeModelLabel"
    }.getOrDefault("Gemini Nano")

    override suspend fun availability(): AiAvailability = runCatching {
        val model = model()
        if (knownAvailable) return@runCatching AiAvailability.Available

        val status = model.checkStatus()
        if (status == FeatureStatus.AVAILABLE) {
            knownAvailable = true
            AiAvailability.Available
        } else if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            AiAvailability.Available
        } else {
            AiAvailability.Unavailable
        }
    }.getOrDefault(AiAvailability.Unavailable)

    private fun richPrompt(url: String, content: String, knownTags: List<String> = emptyList()): String {
        val knownCategory = categoryForDomain(url)
        val categoryGuidance = if (knownCategory != null) {
            """"category" MUST be exactly "$knownCategory"."""
        } else {
            """"category" MUST be one of: Article, Documentation, Repo, Video, Discussion."""
        }
        val existingTagsHint = if (knownTags.isNotEmpty()) {
            " When appropriate, align with active library tags: ${knownTags.take(10).joinToString(", ")}."
        } else ""
        val tagGuidance = "tags: 3 to 6 descriptive tags naming the specific technologies, libraries, tools, frameworks, and key topics discussed in the content (1 to 3 words per tag, e.g. 'Claude Code', 'Agent Harness', 'LangGraph', 'Terminal UI').$existingTagsHint"
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
        knownTags: List<String>,
    ): OrganizedContent? {
        val bounded = content.take(contentChars)
        structuredOrganize(url, bounded, knownTags)?.let { return it }
        return promptJsonOrganize(url, bounded, knownTags)
    }

    private suspend fun structuredOrganize(
        url: String,
        content: String,
        knownTags: List<String>,
    ): OrganizedContent? {
        if (structuredSupported == false) return null
        return runCatching {
            val m = model()
            if (structuredSupported == null) {
                structuredSupported = m.isStructuredOutputFeatureAvailable()
                if (structuredSupported != true) return null
            }
            val request = generateContentRequest(TextPart(schemaPrompt(url, content, knownTags))) {}
            val typed = m.generateContent(
                generateTypedContentRequest(
                    generateContentRequest = request,
                    outputClass = OrganizedResponse::class,
                    includeSchemaInPrompt = true,
                )
            )
            typed.candidates.firstOrNull()?.response?.toOrganizedContent(url)
        }.onFailure {
            StashLog.w(TAG, "structured output failed, falling back to prompt JSON", it)
        }.getOrNull()
    }

    private suspend fun promptJsonOrganize(
        url: String,
        content: String,
        knownTags: List<String>,
    ): OrganizedContent? {
        val prompt = richPrompt(url, content, knownTags)
        return runCatching {
            val raw = model().generateContent(prompt).candidates.firstOrNull()?.text?.trim().orEmpty()
            val jsonText = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            jsonParser.decodeFromString<OrganizedResponse>(jsonText).toOrganizedContent(url)
        }.getOrNull()
    }

    override fun chatStream(
        itemContext: String,
        history: List<ChatTurn>,
        question: String,
    ): Flow<String> = flow {
        val m = model()
        emitAll(
            m.generateContentStream(chatPrompt(itemContext, history, question))
                .mapNotNull { response -> response.candidates.firstOrNull()?.text }
        )
    }

    override fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = flow {
        val m = model()
        emitAll(
            m.generateContentStream(briefingPrompt(itemsContext, itemCount, topic, history, question))
                .mapNotNull { response -> response.candidates.firstOrNull()?.text }
        )
    }

    private fun briefingPrompt(
        itemsContext: String,
        itemCount: Int,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): String = buildString {
        appendLine("You are Stash's on-device intelligence. You generate clear, actionable executive briefings for saved links.")
        appendLine("Synthesize the provided saved items into a concise, practical brief.")
        appendLine()
        if (question == null) {
            val wantsComparison = itemCount > 1

            appendLine("Format guidelines:")
            if (!topic.isNullOrBlank()) {
                appendLine("- Focus the brief on the topic: $topic")
            }
            appendLine("- Do NOT include any introductory title such as 'Executive Briefing:'. Start directly with **The Big Picture**.")
            if (wantsComparison) {
                appendLine("- Use these exact bold section headers on their own line: **The Big Picture**, **Key Takeaways**, **Comparisons & Trade-offs**, and **The Bottom Line**.")
            } else {
                appendLine("- Use these exact bold section headers on their own line: **The Big Picture**, **Key Takeaways**, and **The Bottom Line**.")
            }
            appendLine("- Under **The Big Picture**: 1 to 2 sentences summarizing the core theme connecting these items.")
            appendLine("- Under **Key Takeaways**: Bullet points summarizing the primary insights, findings, and actionable takeaways from the saved items.")
            if (wantsComparison) {
                appendLine("- Under **Comparisons & Trade-offs**: Bullet points and comparisons highlighting how the tools/approaches differ, their trade-offs, and relative strengths.")
            } else {
                appendLine("- There is only one source. Do NOT write a comparisons or trade-offs section, and do not compare it to anything not present.")
            }
            appendLine("- Under **The Bottom Line**: 1 concise concluding takeaway or recommendation.")
            appendLine("- Use clean markdown with bold section headers and bullet points. Be direct, dense with substance, and avoid fluff or filler phrases like 'In conclusion'.")
        } else {
            appendLine("Format guidelines:")
            appendLine("- Answer the user's question directly, grounding your response in the saved items below. Keep answers concise and direct.")
        }
        appendLine()
        appendLine("Saved Items:")
        appendLine(itemsContext.trim())
        appendLine()
        if (history.isNotEmpty()) {
            appendLine("Conversation:")
            history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
                appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
            }
        }
        if (question != null) {
            appendLine("User: $question")
        }
        append("Assistant:")
    }

    private fun chatPrompt(
        itemContext: String,
        history: List<ChatTurn>,
        question: String,
    ): String = buildString {
        appendLine(
            "You are Stash's assistant, answering questions about a link the user saved. " +
                "You run entirely on this device."
        )
        appendLine(
            "Answer in plain text — no markdown, no bullet syntax. Be concrete and brief: " +
                "a few sentences unless the question genuinely needs more."
        )
        appendLine(
            "Ground answers in the saved item content and notes below. General knowledge is fine, but do not " +
                "invent details about the page beyond what is provided in the saved item."
        )
        appendLine()
        appendLine("Saved item:")
        appendLine(itemContext.trim())
        appendLine()
        appendLine("Conversation:")
        history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
            appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
        }
        appendLine("User: $question")
        append("Assistant:")
    }

    private fun String.takeWords(max: Int): String {
        if (length <= max) return this
        val cut = take(max)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > max / 2) cut.take(lastSpace).trimEnd(',', ';', ':', ' ') else cut
    }

    private fun OrganizedResponse.toOrganizedContent(url: String): OrganizedContent {
        val points = keyPoints.map(String::trim).filter(String::isNotBlank)
        return OrganizedContent(
            title = cleanTitle(title, url),
            headline = takeaway.trim()
                .ifBlank { points.firstOrNull().orEmpty() }
                .removeSuffix(".")
                .takeWords(90),
            summary = points.joinToString("\n") { it.removePrefix("- ").trim() },
            category = categoryForDomain(url)
                ?: category.trim().take(32).ifBlank { "Unsorted" },
            tags = tags.map(String::trim).filter(String::isNotBlank).distinct().take(8),
        )
    }

    private fun schemaPrompt(url: String, content: String, knownTags: List<String> = emptyList()): String = buildString {
        appendLine("Summarize this saved link so it can be rediscovered later.")
        appendLine("Tagging guidelines:")
        appendLine("- Extract 3 to 6 descriptive tags representing the specific tools, libraries, technologies, frameworks, and key topics discussed in the page.")
        appendLine("- Use standard, concise naming (1-3 words per tag, capitalized appropriately, e.g. 'Claude Code', 'Agent Harness', 'Terminal UI', 'LangGraph', 'Kotlin').")
        appendLine("- Avoid overly generic filler words like 'Post', 'Article', or 'Website'.")
        if (knownTags.isNotEmpty()) {
            val sampleTags = knownTags.take(10).joinToString(", ")
            appendLine("- When appropriate, align with active library tags: $sampleTags")
        }
        appendLine("- Use only the content below. Do not use outside knowledge.")
        appendLine()
        appendLine("URL: $url")
        appendLine("Content: $content")
    }
}

/**
 * Sanitizes page titles by stripping redundant site branding and repository taglines.
 */
internal fun cleanTitle(rawTitle: String, url: String): String {
    if (rawTitle.isBlank()) return ""
    var title = rawTitle.trim()

    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: ""

    val siteSuffixes = listOf(
        " · GitHub",
        " - GitHub",
        " | GitHub",
        " · GitLab",
        " - GitLab",
        " | Hacker News",
        " - YouTube",
        " | YouTube",
        " - Substack",
        " | Substack",
        " | Medium",
        " - Medium",
    )
    for (suffix in siteSuffixes) {
        if (title.endsWith(suffix, ignoreCase = true)) {
            title = title.substring(0, title.length - suffix.length).trim()
        }
    }

    if (host.contains("github.com")) {
        if (title.startsWith("GitHub - ", ignoreCase = true)) {
            title = title.substring(9).trim()
        } else if (title.startsWith("GitHub: ", ignoreCase = true)) {
            title = title.substring(8).trim()
        }

        if (title.contains(": ")) {
            val beforeColon = title.substringBefore(": ").trim()
            if (beforeColon.matches(Regex("""^[\w\-.]+/[\w\-.]+$"""))) {
                title = beforeColon
            }
        }
    }

    return title
}
