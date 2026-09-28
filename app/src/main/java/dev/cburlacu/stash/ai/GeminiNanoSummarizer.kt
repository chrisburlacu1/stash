package dev.cburlacu.stash.ai

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
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.local.TagNormalizer
import dev.cburlacu.stash.util.StashLog
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
import kotlinx.serialization.json.Json

private const val TAG = "StashSummarizer"

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
        val client = runCatching { clientFor(choice) }.getOrNull()
        if (client == null) {
            return@map ModelOption(choice, ModelStatus.Unavailable)
        }
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
            val client = runCatching { clientFor(choice) }.getOrNull()
            if (client != null) {
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

    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
        knownTopics: List<String>,
    ): OrganizedContent? {
        val bounded = content.take(contentChars)
        structuredOrganize(url, bounded, knownTags, knownTopics)?.let { return it }
        return promptJsonOrganize(url, bounded, knownTags, knownTopics)
    }

    private suspend fun structuredOrganize(
        url: String,
        content: String,
        knownTags: List<String>,
        knownTopics: List<String> = emptyList(),
    ): OrganizedContent? {
        if (structuredSupported == false) return null
        return runCatching {
            val m = model()
            if (structuredSupported == null) {
                structuredSupported = m.isStructuredOutputFeatureAvailable()
                if (structuredSupported != true) return null
            }
            val request = generateContentRequest(TextPart(buildSchemaPrompt(url, content, knownTags, knownTopics))) {}
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
        knownTopics: List<String> = emptyList(),
    ): OrganizedContent? {
        val prompt = buildRichPrompt(url, content, knownTags, knownTopics)
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
            m.generateContentStream(buildChatPrompt(itemContext, history, question))
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
            m.generateContentStream(buildBriefingPrompt(itemsContext, itemCount, topic, history, question))
                .mapNotNull { response -> response.candidates.firstOrNull()?.text }
        )
    }

    override suspend fun inferTopic(
        title: String,
        summary: String,
        tags: String,
        knownTopics: List<String>,
    ): String? = runCatching {
        val prompt = buildInferTopicPrompt(title, summary, tags, knownTopics)
        val raw = model().generateContent(prompt)
            .candidates.firstOrNull()?.text?.trim().orEmpty()
        raw.lines().first().trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("[^\\w\\s&.+#/-]"), "")
            .trim()
            .take(32)
            .ifBlank { null }
    }.getOrNull()

    private fun String.takeWords(max: Int): String {
        if (length <= max) return this
        val cut = take(max)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > max / 2) cut.take(lastSpace).trimEnd(',', ';', ':', ' ') else cut
    }

    private fun OrganizedResponse.toOrganizedContent(url: String): OrganizedContent {
        val points = keyPoints.map(String::trim).filter(String::isNotBlank)
        val normalizedTopic = TagNormalizer.normalize(topic.trim().take(32))
            ?.replaceFirstChar(Char::uppercase)
            ?.ifBlank { "General" }
            ?: "General"
        return OrganizedContent(
            title = cleanTitle(title, url),
            headline = takeaway.trim()
                .ifBlank { points.firstOrNull().orEmpty() }
                .removeSuffix(".")
                .takeWords(90),
            summary = points.joinToString("\n") { it.removePrefix("- ").trim() },
            category = categoryForDomain(url)
                ?: category.trim().take(32).ifBlank { "Unsorted" },
            tags = tags.mapNotNull(TagNormalizer::normalize).filter(String::isNotBlank).distinct().take(6),
            topic = normalizedTopic,
        )
    }
}
