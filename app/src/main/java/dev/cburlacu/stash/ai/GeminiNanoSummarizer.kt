package dev.cburlacu.stash.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.PromptPrefix
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
import kotlinx.coroutines.flow.catch
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
    @Volatile private var cachingSupported: Boolean? = null
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
            structuredSupported = null
            cachingSupported = null
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
        structuredSupported = null
        cachingSupported = null
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

    private suspend fun isCachingSupported(model: GenerativeModel): Boolean {
        cachingSupported?.let { return it }
        return runCatching { model.isCachingFeatureAvailable() }
            .onFailure { StashLog.w(TAG, "isCachingFeatureAvailable check failed", it) }
            .getOrDefault(false)
            .also { supported ->
                cachingSupported = supported
                StashLog.d(TAG, "prefix caching supported=$supported")
            }
    }

    private suspend fun buildContentRequest(
        model: GenerativeModel,
        prompt: CachedPrompt,
    ): GenerateContentRequest {
        return if (prompt.prefix.isNotBlank() && isCachingSupported(model)) {
            generateContentRequest(TextPart(prompt.suffix)) {
                promptPrefix = PromptPrefix(prompt.prefix)
            }
        } else {
            generateContentRequest(TextPart(prompt.fullText())) {}
        }
    }

    private fun buildUncachedRequest(prompt: CachedPrompt): GenerateContentRequest =
        generateContentRequest(TextPart(prompt.fullText())) {}

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
            val prompt = buildSchemaPrompt(url, content, knownTags, knownTopics)
            val request = buildContentRequest(m, prompt)
            val typed = runCatching {
                m.generateContent(
                    generateTypedContentRequest(
                        generateContentRequest = request,
                        outputClass = OrganizedResponse::class,
                        includeSchemaInPrompt = true,
                    )
                )
            }.recoverCatching { err ->
                if (cachingSupported == true) {
                    StashLog.w(TAG, "structured output with prefix cache failed, retrying uncached", err)
                    m.generateContent(
                        generateTypedContentRequest(
                            generateContentRequest = buildUncachedRequest(prompt),
                            outputClass = OrganizedResponse::class,
                            includeSchemaInPrompt = true,
                        )
                    )
                } else {
                    throw err
                }
            }.getOrThrow()
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
            val m = model()
            val response = runCatching {
                m.generateContent(buildContentRequest(m, prompt))
            }.recoverCatching { err ->
                if (cachingSupported == true) {
                    StashLog.w(TAG, "prompt JSON with prefix cache failed, retrying uncached", err)
                    m.generateContent(buildUncachedRequest(prompt))
                } else {
                    throw err
                }
            }.getOrThrow()
            val raw = response.candidates.firstOrNull()?.text?.trim().orEmpty()
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
        val prompt = buildChatPrompt(itemContext, history, question)
        val request = buildContentRequest(m, prompt)
        emitAll(
            m.generateContentStream(request)
                .catch { err ->
                    if (cachingSupported == true) {
                        StashLog.w(TAG, "chat stream with prefix cache failed, retrying uncached", err)
                        emitAll(m.generateContentStream(buildUncachedRequest(prompt)))
                    } else {
                        throw err
                    }
                }
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
        val prompt = buildBriefingPrompt(itemsContext, itemCount, topic, history, question)
        val request = buildContentRequest(m, prompt)
        emitAll(
            m.generateContentStream(request)
                .catch { err ->
                    if (cachingSupported == true) {
                        StashLog.w(TAG, "briefing stream with prefix cache failed, retrying uncached", err)
                        emitAll(m.generateContentStream(buildUncachedRequest(prompt)))
                    } else {
                        throw err
                    }
                }
                .mapNotNull { response -> response.candidates.firstOrNull()?.text }
        )
    }

    override suspend fun inferTopic(
        title: String,
        summary: String,
        tags: String,
        knownTopics: List<String>,
    ): String? = runCatching {
        val m = model()
        val prompt = buildInferTopicPrompt(title, summary, tags, knownTopics)
        val response = runCatching {
            m.generateContent(buildContentRequest(m, prompt))
        }.recoverCatching { err ->
            if (cachingSupported == true) {
                m.generateContent(buildUncachedRequest(prompt))
            } else {
                throw err
            }
        }.getOrThrow()
        val raw = response.candidates.firstOrNull()?.text?.trim().orEmpty()
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
