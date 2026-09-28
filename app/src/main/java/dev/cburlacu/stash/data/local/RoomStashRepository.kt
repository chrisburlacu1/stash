package dev.cburlacu.stash.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.PathParser
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
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.theme.CardSeed
import dev.cburlacu.stash.ui.theme.cropBiasFromPixels
import dev.cburlacu.stash.ui.theme.seedFromPixels
import dev.cburlacu.stash.ui.util.ImageBitmapCache
import dev.cburlacu.stash.util.StashLog
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
        val finalImage = cachedImage ?: if (isTwitterUrl(normalized)) saveXFallbackImage(id) else null
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
                val analysis = runCatching { analyzeImage(file.readBytes()) }.getOrNull() ?: continue
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
    internal fun isLowResolutionImage(file: File): Boolean = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth in 1..200 || options.outHeight in 1..200
    }.getOrDefault(false)

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

    internal data class Extraction(val text: String, val imageUrl: String? = null)

    private suspend fun extractReadableText(url: String): Extraction = withContext(Dispatchers.IO) {
        extractTweet(url)?.let { return@withContext it }

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
                val file = File(dir, name)
                file.writeBytes(bytes)
                ImageBitmapCache.evict(file.absolutePath)
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

    @VisibleForTesting
    internal fun extractTweet(url: String): Extraction? {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        if (host != "x.com" && host != "twitter.com") return null
        return fetchViaFxTwitter(url) ?: fetchViaVxTwitter(url) ?: fetchViaOEmbed(url)
    }

    private fun isTwitterUrl(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        return host == "x.com" || host == "twitter.com"
    }

    private fun fetchViaFxTwitter(url: String): Extraction? = runCatching {
        val path = URI(url).path.orEmpty()
        val json = readText("https://api.fxtwitter.com$path") ?: return@runCatching null
        parseFxTwitterJson(json)
    }.getOrNull()

    @VisibleForTesting
    internal fun parseFxTwitterJson(json: String): Extraction? {
        val tweetText = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()?.trim()

        return if (!tweetText.isNullOrBlank()) {
            val author = Regex(""""author"\s*:\s*\{[^}]*?"name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val photoUrl = Regex(""""photos"\s*:\s*\[\s*\{[^}]*?"url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val videoThumbnailUrl = Regex(""""thumbnail_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val avatarUrl = Regex(""""avatar_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val imageUrl = (photoUrl?.let(::upgradeTwitterPhotoUrl) ?: videoThumbnailUrl)
                ?: avatarUrl?.let(::upgradeTwitterAvatarUrl)

            Extraction(
                text = buildTweetText(author, tweetText),
                imageUrl = imageUrl,
            )
        } else {
            val userName = Regex(""""user"\s*:\s*\{[^}]*?"name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val screenName = Regex(""""screen_name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val bio = Regex(""""description"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()?.trim()
            val bannerUrl = Regex(""""banner_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()
            val avatarUrl = Regex(""""avatar_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(json)?.groupValues?.get(1)?.unescapeJson()

            if (userName.isNullOrBlank() && screenName.isNullOrBlank()) return null

            val title = userName ?: screenName.orEmpty()
            val body = buildString {
                append("X / Twitter profile for ").append(title)
                if (!screenName.isNullOrBlank()) append(" (@").append(screenName).append(")")
                if (!bio.isNullOrBlank()) append(".\nBio: ").append(bio.replace(Regex("\\s+"), " ").take(1_000))
            }

            Extraction(
                text = body,
                imageUrl = bannerUrl ?: avatarUrl?.let(::upgradeTwitterAvatarUrl),
            )
        }
    }

    private fun fetchViaVxTwitter(url: String): Extraction? = runCatching {
        val path = URI(url).path.orEmpty()
        val json = readText("https://api.vxtwitter.com$path") ?: return@runCatching null
        parseVxTwitterJson(json)
    }.getOrNull()

    @VisibleForTesting
    internal fun parseVxTwitterJson(json: String): Extraction? {
        val text = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()?.trim()
        if (text.isNullOrBlank()) return null

        val author = Regex(""""user_name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()
        val photoUrl = Regex(""""mediaURLs"\s*:\s*\[\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()
            ?.takeUnless { it.endsWith(".mp4", ignoreCase = true) }
        val videoThumbnailUrl = Regex(""""thumbnail_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()
        val avatarUrl = Regex(""""user_profile_image_url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)?.unescapeJson()

        val imageUrl = (photoUrl?.let(::upgradeTwitterPhotoUrl) ?: videoThumbnailUrl)
            ?: avatarUrl?.let(::upgradeTwitterAvatarUrl)

        return Extraction(
            text = buildTweetText(author, text),
            imageUrl = imageUrl,
        )
    }

    private fun fetchViaOEmbed(url: String): Extraction? = runCatching {
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
        Extraction(text = buildTweetText(author, body), imageUrl = null)
    }.getOrNull()

    @VisibleForTesting
    internal fun upgradeTwitterAvatarUrl(url: String): String {
        return url.replace(Regex("_(?:normal|mini|bigger|200x200|x96|\\d+x\\d+)\\."), "_400x400.")
    }

    @VisibleForTesting
    internal fun upgradeTwitterPhotoUrl(url: String): String {
        if (!url.contains("pbs.twimg.com/media/")) return url
        return if (url.contains("?name=") || url.contains("&name=")) {
            url.replace(Regex("([?&]name=)(?:small|medium|thumb)"), "$1large")
        } else if (url.contains("?")) {
            "$url&name=large"
        } else {
            "$url?name=large"
        }
    }

    @VisibleForTesting
    internal fun generateXFallbackImage(width: Int = 800, height: Int = 450): ByteArray = runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                0xFF0F1419.toInt(),
                0xFF16181C.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val pathData = "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"
        val path = PathParser.createPathFromPathData(pathData)

        val targetHeight = height * 0.40f
        val scale = targetHeight / 24f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            val scaledW = 24f * scale
            val scaledH = 24f * scale
            postTranslate((width - scaledW) / 2f, (height - scaledH) / 2f)
        }
        path.transform(matrix)

        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE7E9EA.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, glyphPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        stream.toByteArray()
    }.getOrDefault(ByteArray(0))

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
            val analysis = analyzeImage(bytes)
            CachedImage(
                fileName = name,
                seedColor = analysis.seedColor,
                cropBias = analysis.cropBias,
            )
        }.getOrNull()
    }

    private fun buildTweetText(author: String?, text: String): String = buildString {
        append("Social media post")
        if (!author.isNullOrBlank()) append(" by ").append(author)
        append(".\n")
        append("Post text: ").append(text.replace(Regex("\\s+"), " ").take(1_500))
    }

    internal fun readText(endpoint: String): String? = runCatching {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
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
        seedColor = seedFromPixels(pixels, width, height),
        cropBias = cropBiasFromPixels(pixels, width, height),
    )
}.getOrDefault(ImageAnalysis(CardSeed.NONE, 0f))

private val WHITESPACE = Regex("\\s+")

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
