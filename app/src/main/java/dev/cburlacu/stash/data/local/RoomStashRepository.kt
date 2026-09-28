package dev.cburlacu.stash.data.local

import androidx.annotation.VisibleForTesting
import dev.cburlacu.stash.ai.AiAvailability
import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.LibrarianAgent
import dev.cburlacu.stash.ai.OnDeviceSummarizer
import dev.cburlacu.stash.ai.categoryForDomain
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.SortOrder
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.SummaryEffort
import dev.cburlacu.stash.data.TagCount
import dev.cburlacu.stash.data.TopicCount
import dev.cburlacu.stash.data.extract.Extraction
import dev.cburlacu.stash.data.extract.TwitterExtractor
import dev.cburlacu.stash.data.extract.WebPageExtractor
import dev.cburlacu.stash.data.extract.WebPageExtractor.articleText
import dev.cburlacu.stash.data.image.ImageAnalyzer
import dev.cburlacu.stash.data.image.ImageFallbackRenderer
import dev.cburlacu.stash.data.prompt.PromptContextBuilders
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.util.ImageBitmapCache
import dev.cburlacu.stash.util.StashLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

class RoomStashRepository(
    private val dao: StashDao,
    private val summarizer: OnDeviceSummarizer,
    private val summaryEffort: Flow<SummaryEffort> = flowOf(SummaryEffort.Medium),
    private val imageDir: File? = null,
) : StashRepository {

    override fun observe(
        query: String,
        tags: Set<String>,
        sortOrder: SortOrder,
        topic: String?,
    ): Flow<List<StashItem>> {
        val ftsQuery = toFtsQuery(query)
        val source = when {
            ftsQuery.isNotBlank() -> dao.search(ftsQuery, null)
            topic != null -> dao.observeTopic(topic)
            else -> dao.observeAll()
        }
        return source
            .catch { emit(emptyList()) }
            .map { rows ->
                val items = rows.distinctBy(StashListRow::id)
                    .map { it.toModel(imageDir) }
                    .filter { item -> tags.isEmpty() || item.tags.containsAll(tags) }
                    .let { list ->
                        // Apply topic filter in-memory when combined with FTS search
                        if (topic != null && ftsQuery.isNotBlank()) {
                            list.filter { it.topic.equals(topic, ignoreCase = true) }
                        } else list
                    }

                if (ftsQuery.isNotBlank()) {
                    items
                } else {
                    when (sortOrder) {
                        SortOrder.Newest -> items.sortedByDescending { it.savedAtEpochMillis }
                        SortOrder.Oldest -> items.sortedBy { it.savedAtEpochMillis }
                        SortOrder.UnreadFirst -> items.sortedWith(
                            compareBy<StashItem> { it.isRead }.thenByDescending { it.savedAtEpochMillis }
                        )
                    }
                }
            }
    }

    override fun observeItem(id: String): Flow<StashItem?> =
        dao.observeItem(id).map { it?.toModel(imageDir) }

    override fun observeItems(ids: List<String>): Flow<List<StashItem>> {
        val order = ids.withIndex().associate { (index, id) -> id to index }
        return dao.observeItems(ids).map { rows ->
            rows.distinctBy(StashEntity::id)
                .sortedBy { order[it.id] ?: Int.MAX_VALUE }
                .map { it.toModel(imageDir) }
        }
    }

    override fun observeTags(): Flow<List<TagCount>> = dao.observeAllTags().map { tagsList ->
        tagsList.flatMap { it.split(TAG_SEPARATOR) }
            .mapNotNull { TagNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> TagCount(name, count) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.name.lowercase() })
    }

    override fun observeTopics(): Flow<List<TopicCount>> = dao.observeAllTopics().map { topicsList ->
        topicsList
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> TopicCount(name, count) }
            .sortedWith(compareByDescending<TopicCount> { it.count }.thenBy { it.name.lowercase() })
    }

    override suspend fun addUrl(url: String) {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        val domain = runCatching { URI(normalized).host.removePrefix("www.") }.getOrNull()
            ?.takeIf(String::isNotBlank) ?: "saved link"
        val fallbackTitle = domain.replaceFirstChar(Char::uppercase)

        val id = UUID.randomUUID().toString()
        val initialEntity = StashEntity(
            id = id,
            url = normalized,
            title = fallbackTitle,
            domain = domain,
            category = "Unsorted",
            summary = "Summarizing link...",
            tags = "",
            readTime = "1 min",
            savedAtEpochMillis = System.currentTimeMillis(),
            aiState = AiState.Summarizing.name,
        )
        dao.upsert(initialEntity)

        val available = summarizer.availability() == AiAvailability.Available
        val extraction = extractReadableText(normalized)
        val extractedText = extraction.text
        val hasContent = hasUsableContent(extractedText)
        val fallbackSummary = if (hasContent) {
            extractedText.take(200) + "..."
        } else {
            "Couldn't read this page — it may be private, deleted, or need a login."
        }

        val knownTags = existingTags()
        val knownTopics = existingTopics()
        val effort = summaryEffort.first()

        val (organized, cachedImage) = coroutineScope {
            val pendingImage = extraction.imageUrl?.let { async { cacheHeaderImage(it, id) } }
            val summarized = if (available && hasContent) {
                summarizer.organize(normalized, extractedText, effort.contentChars, knownTags, knownTopics)
            } else null
            summarized to pendingImage?.await()
        }
        val finalImage = cachedImage ?: if (TwitterExtractor.isTwitterUrl(normalized)) saveXFallbackImage(id) else null
        val category = organized?.category ?: categoryForDomain(normalized) ?: "Unsorted"
        val topic = organized?.topic?.takeIf(String::isNotBlank) ?: ""

        dao.upsert(
            initialEntity.copy(
                imageFile = finalImage?.fileName.orEmpty(),
                seedColor = finalImage?.seedColor ?: 0,
                cropBias = finalImage?.cropBias ?: 0f,
                content = if (hasContent) extractedText else "",
                title = organized?.title?.takeIf(::isUsefulTitle) ?: fallbackTitle,
                category = category,
                topic = topic,
                headline = organized?.headline?.takeIf(String::isNotBlank)
                    ?: if (hasContent) "" else "Content unavailable",
                summary = organized?.summary?.takeIf(String::isNotBlank) ?: fallbackSummary,
                tags = reconcileTags(organized?.tags.orEmpty(), knownTags).joinToString(TAG_SEPARATOR),
                aiState = when {
                    organized != null -> AiState.Ready.name
                    !hasContent -> AiState.Failed.name
                    available -> AiState.Failed.name
                    else -> AiState.Unavailable.name
                },
            )
        )
    }

    private fun hasUsableContent(extracted: String): Boolean {
        if (extracted.isBlank() || extracted.startsWith("Saved URL:")) return false
        val body = extracted
            .substringAfter("Article Body:", extracted)
            .substringAfter("Post text:", extracted)
            .trim()
        return extracted.length >= MIN_EXTRACT_CHARS || body.length >= MIN_EXTRACT_CHARS
    }

    override suspend fun setRead(id: String, isRead: Boolean) = dao.setRead(id, isRead)

    suspend fun backfillSeedColors() {
        val dir = imageDir ?: return
        withContext(Dispatchers.IO) {
            val pending = runCatching { dao.rowsWithImage() }.getOrNull().orEmpty()
            for (row in pending) {
                val file = File(dir, row.imageFile)
                if (!file.exists()) continue
                val analysis = runCatching { ImageAnalyzer.analyzeImage(file.readBytes()) }.getOrNull() ?: continue
                if (analysis.seedColor != row.seedColor || analysis.cropBias != row.cropBias) {
                    runCatching { dao.setSeedAndCrop(row.id, analysis.seedColor, analysis.cropBias) }
                }
            }
        }
    }

    suspend fun backfillNormalizedTags() {
        withContext(Dispatchers.IO) {
            val pending = runCatching { dao.rowsForTagBackfill() }.getOrNull().orEmpty()
            for (row in pending) {
                val rawTags = row.tags.split(TAG_SEPARATOR).map(String::trim).filter(String::isNotBlank)
                val normalized = reconcileTags(rawTags, knownTags = emptyList())
                val newTagsStr = normalized.joinToString(TAG_SEPARATOR)
                if (newTagsStr != row.tags) {
                    runCatching { dao.setTags(row.id, newTagsStr) }
                }
            }
        }
    }

    /**
     * Backfills topics for items missing one using the on-device LibrarianAgent.
     * Safe to call at startup — no-ops if AI is unavailable or no items need backfill.
     */
    override suspend fun backfillTopics(): Int {
        val agent = LibrarianAgent(dao, summarizer)
        return agent.backfillTopics()
    }

    suspend fun backfillTwitterImages() {
        val dir = imageDir ?: return
        withContext(Dispatchers.IO) {
            val rows = runCatching { dao.allTwitterRows() }.getOrNull().orEmpty()
            for (row in rows) {
                val file = if (row.imageFile.isNotBlank()) File(dir, row.imageFile) else null
                val needsUpgrade = file == null || !file.exists() || file.length() == 0L || isLowResolutionImage(file)
                if (!needsUpgrade) continue

                val extraction = extractTweet(row.url)
                val cached = extraction?.imageUrl?.let { cacheHeaderImage(it, row.id) }
                    ?: if (file == null || !file.exists()) saveXFallbackImage(row.id) else null
                if (cached != null) {
                    runCatching {
                        dao.setImageData(row.id, cached.fileName, cached.seedColor, cached.cropBias)
                    }
                    StashLog.d("RoomStashRepository", "Backfilled high-res Twitter image for ${row.id}: ${cached.fileName}")
                }
            }
        }
    }

    @VisibleForTesting
    internal fun isLowResolutionImage(file: File): Boolean = ImageAnalyzer.isLowResolutionImage(file)

    override suspend fun delete(id: String) {
        val imageFile = runCatching { dao.imageFileFor(id) }.getOrNull()
        dao.delete(id)
        if (!imageFile.isNullOrBlank() && imageDir != null) {
            withContext(Dispatchers.IO) {
                runCatching { File(imageDir, imageFile).delete() }
            }
        }
    }

    override fun chat(item: StashItem, history: List<ChatTurn>, question: String): Flow<String> =
        summarizer.chatStream(itemChatContext(item), history, question)

    override fun briefing(
        items: List<StashItem>,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = summarizer.briefingStream(
        itemsContext = itemsBriefingContext(items),
        itemCount = items.size.coerceAtMost(PromptContextBuilders.MAX_BRIEFING_ITEMS),
        topic = topic,
        history = history,
        question = question,
    )

    @VisibleForTesting
    internal fun itemsBriefingContext(items: List<StashItem>): String =
        PromptContextBuilders.itemsBriefingContext(items)

    private fun itemChatContext(item: StashItem): String =
        PromptContextBuilders.itemChatContext(item)

    override suspend fun getModelVersion(): String = summarizer.getModelVersion()

    override suspend fun probeModels() = summarizer.probeModels()

    override suspend fun selectModel(choice: ModelChoice) = summarizer.selectModel(choice)

    private suspend fun existingTags(): List<String> =
        dao.allTags()
            .flatMap { it.split(TAG_SEPARATOR) }
            .mapNotNull { TagNormalizer.normalize(it) }
            .filter(String::isNotBlank)
            .distinct()

    private suspend fun existingTopics(): List<String> =
        dao.allTopics()
            .map { it.trim() }
            .filter(String::isNotBlank)
            .distinct()

    @VisibleForTesting
    internal fun reconcileTags(tags: List<String>, knownTags: List<String>): List<String> {
        val knownByStem = knownTags
            .mapNotNull { tag -> TagNormalizer.normalize(tag)?.let { TagNormalizer.stem(it) to tag } }
            .toMap()

        return tags.asSequence()
            .mapNotNull { TagNormalizer.normalize(it) }
            .map { normalized -> knownByStem[TagNormalizer.stem(normalized)] ?: normalized }
            .distinctBy { TagNormalizer.stem(it) }
            .take(MAX_TAGS_PER_ITEM)
            .toList()
    }

    private fun isUsefulTitle(title: String): Boolean {
        val cleaned = title.trim()
        if (cleaned.length < 4) return false
        return cleaned.lowercase() !in PLACEHOLDER_TITLES
    }

    private suspend fun extractReadableText(url: String): Extraction =
        WebPageExtractor.extractReadableText(url)

    private suspend fun cacheHeaderImage(imageUrl: String, id: String): CachedImage? {
        val dir = imageDir ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!dir.exists() && !dir.mkdirs()) return@runCatching null
                val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", IMAGE_USER_AGENT)
                }
                if (connection.responseCode !in 200..299) return@runCatching null
                val declared = connection.contentLengthLong
                if (declared > MAX_IMAGE_BYTES) return@runCatching null

                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        buffer.write(chunk, 0, read)
                        if (buffer.size() > MAX_IMAGE_BYTES) return@runCatching null
                    }
                    buffer.toByteArray()
                }
                if (bytes.isEmpty()) return@runCatching null

                val name = "$id.img"
                val file = File(dir, name)
                file.writeBytes(bytes)
                ImageBitmapCache.evict(file.absolutePath)
                val analysis = ImageAnalyzer.analyzeImage(bytes)
                CachedImage(
                    fileName = name,
                    seedColor = analysis.seedColor,
                    cropBias = analysis.cropBias,
                )
            }.getOrNull()
        }
    }

    @VisibleForTesting
    internal fun Document.articleText(): String = with(WebPageExtractor) { articleText() }

    @VisibleForTesting
    internal fun extractTweet(url: String): Extraction? = TwitterExtractor.extractTweet(url)

    @VisibleForTesting
    internal fun parseFxTwitterJson(json: String): Extraction? = TwitterExtractor.parseFxTwitterJson(json)

    @VisibleForTesting
    internal fun parseVxTwitterJson(json: String): Extraction? = TwitterExtractor.parseVxTwitterJson(json)

    @VisibleForTesting
    internal fun upgradeTwitterAvatarUrl(url: String): String = TwitterExtractor.upgradeTwitterAvatarUrl(url)

    @VisibleForTesting
    internal fun upgradeTwitterPhotoUrl(url: String): String = TwitterExtractor.upgradeTwitterPhotoUrl(url)

    @VisibleForTesting
    internal fun generateXFallbackImage(width: Int = 800, height: Int = 450): ByteArray =
        ImageFallbackRenderer.generateXFallbackImage(width, height)

    private fun saveXFallbackImage(id: String): CachedImage? {
        val dir = imageDir ?: return null
        return runCatching {
            if (!dir.exists() && !dir.mkdirs()) return null
            val bytes = generateXFallbackImage()
            if (bytes.isEmpty()) return null
            val name = "$id.img"
            val file = File(dir, name)
            file.writeBytes(bytes)
            ImageBitmapCache.evict(file.absolutePath)
            val analysis = ImageAnalyzer.analyzeImage(bytes)
            CachedImage(
                fileName = name,
                seedColor = analysis.seedColor,
                cropBias = analysis.cropBias,
            )
        }.getOrNull()
    }

    internal fun readText(endpoint: String): String? = TwitterExtractor.readText(endpoint)

    private fun toFtsQuery(query: String): String {
        val sanitized = query.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
        if (sanitized.isBlank()) return ""
        return sanitized
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" AND ") { "\"$it\"*" }
    }
}

private fun StashEntity.toModel(imageDir: File? = null) = StashItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    category = category,
    imagePath = imageFile.takeIf { it.isNotBlank() && imageDir != null }
        ?.let { File(imageDir, it).takeIf(File::exists)?.absolutePath },
    content = content,
    headline = headline.ifBlank { summary },
    summary = summary,
    tags = tags.split(TAG_SEPARATOR).mapNotNull(TagNormalizer::normalize).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
    seedColor = seedColor,
    cropBias = cropBias,
    topic = topic,
)

private fun StashListRow.toModel(imageDir: File? = null) = StashItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    category = category,
    imagePath = imageFile.takeIf { it.isNotBlank() && imageDir != null }
        ?.let { File(imageDir, it).takeIf(File::exists)?.absolutePath },
    headline = headline.ifBlank { summary },
    summary = summary,
    tags = tags.split(TAG_SEPARATOR).mapNotNull(TagNormalizer::normalize).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
    seedColor = seedColor,
    cropBias = cropBias,
    topic = topic,
)

private const val TAG_SEPARATOR = " | "
private const val MAX_TAGS_PER_ITEM = 6
private const val MIN_EXTRACT_CHARS = 120
private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
private const val IMAGE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"

private data class CachedImage(
    val fileName: String,
    val seedColor: Int,
    val cropBias: Float,
)

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
