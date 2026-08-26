package com.example.stash.data.local

import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import com.example.stash.ai.AiAvailability
import com.example.stash.ai.ChatTurn
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.categoryForDomain
import com.example.stash.data.ModelChoice
import com.example.stash.data.SortOrder
import com.example.stash.data.StashRepository
import com.example.stash.data.SummaryEffort
import com.example.stash.data.TagCount
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import com.example.stash.ui.theme.CardSeed
import com.example.stash.ui.theme.cropBiasFromPixels
import com.example.stash.ui.theme.seedFromPixels
import com.example.stash.util.StashLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
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
    ): Flow<List<StashItem>> {
        val ftsQuery = toFtsQuery(query)
        val source = if (ftsQuery.isNotBlank()) dao.search(ftsQuery, null) else dao.observeAll()
        return source
            .catch { emit(emptyList()) }
            .map { rows ->
                val items = rows.distinctBy(StashListRow::id)
                    .map { it.toModel(imageDir) }
                    .filter { item -> tags.isEmpty() || item.tags.containsAll(tags) }

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
        val effort = summaryEffort.first()

        val (organized, cachedImage) = coroutineScope {
            val pendingImage = extraction.imageUrl?.let { async { cacheHeaderImage(it, id) } }
            val summarized = if (available && hasContent) {
                summarizer.organize(normalized, extractedText, effort.contentChars, knownTags)
            } else null
            summarized to pendingImage?.await()
        }
        val category = organized?.category ?: categoryForDomain(normalized) ?: "Unsorted"

        dao.upsert(
            initialEntity.copy(
                imageFile = cachedImage?.fileName.orEmpty(),
                seedColor = cachedImage?.seedColor ?: 0,
                cropBias = cachedImage?.cropBias ?: 0f,
                content = if (hasContent) extractedText else "",
                title = organized?.title?.takeIf(::isUsefulTitle) ?: fallbackTitle,
                category = category,
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
            val pending = runCatching { dao.rowsMissingSeed() }.getOrNull().orEmpty()
            for (row in pending) {
                val file = File(dir, row.imageFile)
                if (!file.exists()) continue
                val analysis = runCatching { analyzeImage(file.readBytes()) }.getOrNull() ?: continue
                if (analysis.seedColor != CardSeed.NONE) {
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
        itemCount = items.size.coerceAtMost(MAX_BRIEFING_ITEMS),
        topic = topic,
        history = history,
        question = question,
    )

    @VisibleForTesting
    internal fun itemsBriefingContext(items: List<StashItem>): String = buildString {
        val included = items.take(MAX_BRIEFING_ITEMS)
        val excerptBudget = if (included.isEmpty()) 0 else BRIEFING_EXCERPT_BUDGET / included.size

        included.forEachIndexed { index, item ->
            appendLine("### Item ${index + 1}: ${item.title}")
            appendLine("Domain: ${item.domain} | Type: ${item.category}")
            if (item.headline.isNotBlank()) appendLine("Takeaway: ${item.headline}")
            val points = item.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
            if (points.isNotEmpty()) {
                appendLine("Key Points:")
                points.forEach { appendLine("- $it") }
            }
            if (item.tags.isNotEmpty()) appendLine("Tags: ${item.tags.joinToString(", ")}")
            if (item.content.isNotBlank() && excerptBudget > 0) {
                appendLine("Excerpt: ${item.content.take(excerptBudget)}")
            }
            appendLine()
        }
    }

    private fun itemChatContext(item: StashItem): String = buildString {
        appendLine("Title: ${item.title}")
        appendLine("Link: ${item.url} (${item.domain})")
        appendLine("Type: ${item.category}")
        if (item.headline.isNotBlank()) appendLine("Takeaway: ${item.headline}")
        val points = item.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
        if (points.isNotEmpty()) {
            appendLine("Key points saved from the page:")
            points.forEach { appendLine("- $it") }
        }
        if (item.tags.isNotEmpty()) appendLine("Tags: ${item.tags.joinToString(", ")}")
        if (item.content.isNotBlank()) {
            appendLine()
            appendLine("Full page content:")
            appendLine(item.content.take(4_000))
        }
    }

    override suspend fun getModelVersion(): String = summarizer.getModelVersion()

    override suspend fun probeModels() = summarizer.probeModels()

    override suspend fun selectModel(choice: ModelChoice) = summarizer.selectModel(choice)

    private suspend fun existingTags(): List<String> =
        dao.allTags()
            .flatMap { it.split(TAG_SEPARATOR) }
            .mapNotNull { TagNormalizer.normalize(it) }
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

    private data class Extraction(val text: String, val imageUrl: String? = null)

    private suspend fun extractReadableText(url: String): Extraction = withContext(Dispatchers.IO) {
        extractTweet(url)?.let { return@withContext Extraction(it) }

        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Stash/1.0")
            val html = connection.inputStream.bufferedReader().use { it.readText().take(MAX_HTML_CHARS) }

            val doc = Jsoup.parse(html, url)
            val ogTitle = doc.metaContent("og:title", "twitter:title")
                ?: doc.title().takeIf(String::isNotBlank)
            val ogDesc = doc.metaContent("og:description", "twitter:description", "description")
            val bodyText = doc.articleText()
            val ogImage = doc.selectFirst(
                "meta[property=og:image], meta[name=og:image], " +
                    "meta[property=twitter:image], meta[name=twitter:image]",
            )?.absUrl("content")?.takeIf(String::isNotBlank)

            StashLog.d(
                "StashExtract",
                "html=${html.length} bodyText=${bodyText.length} url=$url",
            )

            Extraction(
                text = buildString {
                    if (!ogTitle.isNullOrBlank()) append("Title: ").append(ogTitle).append("\n")
                    if (!ogDesc.isNullOrBlank()) append("Summary Note: ").append(ogDesc).append("\n")
                    append("Article Body: ").append(bodyText.take(EXTRACT_BODY_CHARS))
                },
                imageUrl = ogImage,
            )
        }.getOrDefault(Extraction("Saved URL: $url"))
    }

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
                File(dir, name).writeBytes(bytes)
                val analysis = analyzeImage(bytes)
                CachedImage(
                    fileName = name,
                    seedColor = analysis.seedColor,
                    cropBias = analysis.cropBias,
                )
            }.getOrNull()
        }
    }

    private fun Document.metaContent(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        selectFirst("meta[property=$key], meta[name=$key]")
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    @VisibleForTesting
    internal fun Document.articleText(): String {
        select(
            "script, style, noscript, nav, header, footer, aside, form, svg, iframe, " +
                "[class*=comment], [id*=comment], [class*=related], [class*=sidebar], [class*=newsletter]",
        ).remove()

        val candidates = listOf("article", "main", "[role=main]", "[class*=prose]", "[class*=content]")
            .flatMap { select(it) }
            .plus(body())

        val best = candidates
            .filterNotNull()
            .maxByOrNull { element -> element.select("p").sumOf { it.text().length } }
            ?: return ""

        val paragraphs = best.select("p, h1, h2, h3, li")
            .map { it.text().trim() }
            .filter { it.length > 40 }

        return if (paragraphs.isEmpty()) {
            best.text().replace(WHITESPACE, " ").trim()
        } else {
            paragraphs.joinToString("\n") { it.replace(WHITESPACE, " ") }
        }
    }

    private fun extractTweet(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        if (host != "x.com" && host != "twitter.com") return null
        return fetchViaFxTwitter(url) ?: fetchViaOEmbed(url)
    }

    private fun fetchViaFxTwitter(url: String): String? = runCatching {
        val path = URI(url).path.orEmpty()
        val json = readText("https://api.fxtwitter.com$path") ?: return@runCatching null

        val text = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()?.trim()
        if (text.isNullOrBlank()) return@runCatching null

        val author = Regex(""""author"\s*:\s*\{[^}]*?"name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()

        buildTweetText(author, text)
    }.getOrNull()

    private fun fetchViaOEmbed(url: String): String? = runCatching {
        val endpoint = "https://publish.twitter.com/oembed?omit_script=true&url=" +
            java.net.URLEncoder.encode(url, "UTF-8")
        val json = readText(endpoint) ?: return@runCatching null

        val author = Regex(""""author_name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()
        val body = Regex(""""html"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace("&#39;", "'")
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.replace(Regex("https://t\\.co/\\S+"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

        if (body.isNullOrBlank()) return@runCatching null
        buildTweetText(author, body)
    }.getOrNull()

    private fun buildTweetText(author: String?, text: String): String = buildString {
        append("Social media post")
        if (!author.isNullOrBlank()) append(" by ").append(author)
        append(".\n")
        append("Post text: ").append(text.replace(Regex("\\s+"), " ").take(1_500))
    }

    private fun readText(endpoint: String): String? = runCatching {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Stash/1.0")
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun String.unescapeJson(): String = replace("\\/", "/")
        .replace("\\n", " ")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }

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
)

private const val TAG_SEPARATOR = " | "
private const val MAX_TAGS_PER_ITEM = 6
private const val MIN_EXTRACT_CHARS = 120
private const val EXTRACT_BODY_CHARS = 8_000
private const val MAX_BRIEFING_ITEMS = 8
private const val BRIEFING_EXCERPT_BUDGET = 6_000
private const val MAX_HTML_CHARS = 600_000
private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
private const val IMAGE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"

private data class CachedImage(
    val fileName: String,
    val seedColor: Int,
    val cropBias: Float,
)

private const val ANALYSIS_SAMPLE_EDGE_PX = 160

private data class ImageAnalysis(val seedColor: Int, val cropBias: Float)

private fun analyzeImage(bytes: ByteArray): ImageAnalysis = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return@runCatching ImageAnalysis(CardSeed.NONE, 0f)

    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, longest / ANALYSIS_SAMPLE_EDGE_PX)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: return@runCatching ImageAnalysis(CardSeed.NONE, 0f)

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val width = bitmap.width
    val height = bitmap.height
    bitmap.recycle()

    ImageAnalysis(
        seedColor = seedFromPixels(pixels),
        cropBias = cropBiasFromPixels(pixels, width, height),
    )
}.getOrDefault(ImageAnalysis(CardSeed.NONE, 0f))

private val WHITESPACE = Regex("\\s+")

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
