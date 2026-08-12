package com.example.stash.data.local

import androidx.annotation.VisibleForTesting
import com.example.stash.ai.AiAvailability
import com.example.stash.ai.ChatTurn
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.categoryForDomain
import com.example.stash.data.ModelChoice
import com.example.stash.data.StashRepository
import com.example.stash.data.SummaryEffort
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
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
    /**
     * Read here rather than passed into [addUrl]: saves are triggered from the share intent as
     * well as the UI, and threading an effort level through every entry point would leak a
     * summarizer detail into callers that have no opinion about it.
     */
    private val summaryEffort: Flow<SummaryEffort> = flowOf(SummaryEffort.Medium),
    /**
     * Where cached header images live. A directory rather than a [android.content.Context] so the
     * repository stays constructible from a plain JVM test; null disables image caching entirely,
     * which is the default for tests and fakes.
     */
    private val imageDir: File? = null,
) : StashRepository {
    override fun observe(query: String, tags: Set<String>): Flow<List<StashItem>> {
        val ftsQuery = toFtsQuery(query)
        // Tag filtering is applied in Kotlin rather than SQL: the DAO's LIKE-based predicate
        // only handles one tag, and multi-tag AND would need dynamic SQL for a set that is
        // realistically a handful of entries over a personal-scale library.
        val source = if (ftsQuery.isNotBlank()) dao.search(ftsQuery, null) else dao.observeAll()
        return source
            .catch { emit(emptyList()) }
            // distinctBy guards the feed's LazyColumn keys: the FTS join can only ever emit an
            // item once per matching stash_search row, so a stray duplicate there would
            // otherwise crash the list rather than just rank the item oddly.
            .map { rows ->
                rows.distinctBy(StashListRow::id)
                    .map { it.toModel(imageDir) }
                    .filter { item -> tags.isEmpty() || item.tags.containsAll(tags) }
            }
    }

    override fun observeItem(id: String): Flow<StashItem?> =
        dao.observeItem(id).map { it?.toModel(imageDir) }

    override fun observeTags(): Flow<List<String>> = dao.observeAllTags().map { tagsList ->
        tagsList.flatMap { it.split(TAG_SEPARATOR) }
            .map { it.trim().asDisplayTag() }
            .filter { it.isNotBlank() }
            // Cased on read as well as on write, so rows saved before the casing rule existed
            // display consistently without needing a migration. distinct() then collapses what
            // were previously separate "Node.js"/"node.js" chips into one.
            .distinct()
            .sortedBy { it.lowercase() }
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
        // Deleted, private, and JS-only pages yield nothing to summarize. Running the model on
        // an empty payload is what produced summaries describing unrelated saved items, so
        // inference is skipped entirely and the row says plainly that content was unavailable.
        val hasContent = hasUsableContent(extractedText)
        val fallbackSummary = if (hasContent) {
            extractedText.take(200) + "..."
        } else {
            "Couldn't read this page — it may be private, deleted, or need a login."
        }

        // The model sees the tags already in use so it can reuse them instead of minting a
        // near-duplicate for every save ("AI design" vs "AI Design" vs "Design AI").
        val knownTags = existingTags()
        val effort = summaryEffort.first()

        // The image download is pure I/O and the summarizer is on-device compute, so overlapping
        // them keeps the save at roughly its previous cost instead of paying for both in series.
        val (organized, imageFile) = coroutineScope {
            val pendingImage = extraction.imageUrl?.let { async { cacheHeaderImage(it, id) } }
            val summarized = if (available && hasContent) {
                summarizer.organize(normalized, extractedText, effort.contentChars)
            } else null
            summarized to pendingImage?.await()
        }
        val category = organized?.category ?: categoryForDomain(normalized) ?: "Unsorted"

        dao.upsert(
            initialEntity.copy(
                imageFile = imageFile.orEmpty(),
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

    /**
     * Extraction "succeeds" on pages that are really empty shells, so a length floor decides
     * whether there is enough substance to summarize rather than trusting the call returned.
     */
    private fun hasUsableContent(extracted: String): Boolean {
        if (extracted.isBlank() || extracted.startsWith("Saved URL:")) return false
        val body = extracted
            .substringAfter("Article Body:", extracted)
            .substringAfter("Post text:", extracted)
            .trim()
        return extracted.length >= MIN_EXTRACT_CHARS || body.length >= MIN_EXTRACT_CHARS
    }

    override suspend fun setRead(id: String, isRead: Boolean) = dao.setRead(id, isRead)

    /** Deletes the row and its cached header image; orphaned files would otherwise accumulate. */
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

    /**
     * What the chat model gets to know about the item: everything the app stored at save time.
     * Deliberately not a re-fetch of the page — chat must work offline and answer instantly,
     * and re-fetching would leak reading activity on every question.
     */
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
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    /**
     * Snaps AI tags onto existing ones when they differ only by case or spacing, then caps the
     * result. Without this every save adds a handful of near-duplicates and the filter row
     * becomes unusable.
     *
     * Note this only *deduplicates* — it cannot judge relevance, so a wrong tag survives. Tag
     * accuracy is the prompt's job, which is why the existing vocabulary is no longer shown to
     * the model (see [OnDeviceSummarizer.organize]).
     */
    private fun reconcileTags(tags: List<String>, knownTags: List<String>): List<String> {
        val byNormalized = knownTags.associateBy { it.normalizedTag() }
        return tags.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            // Cased first, then snapped: an existing tag's spelling should win over a newly
            // generated one, so the lookup must come after normalising the candidate's own casing.
            .map(String::asDisplayTag)
            .map { byNormalized[it.normalizedTag()] ?: it }
            .distinctBy { it.normalizedTag() }
            .take(MAX_TAGS_PER_ITEM)
            .toList()
    }

    private fun String.normalizedTag(): String = lowercase().filter(Char::isLetterOrDigit)

    /**
     * Rejects placeholder titles the model falls back to when extraction returned nothing
     * ("Twitter Post", "Untitled"), so the row shows the domain instead of a fake title.
     */
    private fun isUsefulTitle(title: String): Boolean {
        val cleaned = title.trim()
        if (cleaned.length < 4) return false
        return cleaned.lowercase() !in PLACEHOLDER_TITLES
    }

    /** Extracted page text plus the header image URL found in the same parse, if any. */
    private data class Extraction(val text: String, val imageUrl: String? = null)

    private suspend fun extractReadableText(url: String): Extraction = withContext(Dispatchers.IO) {
        // Social posts are JS-rendered shells that return no content (x.com answers 404 with an
        // empty SPA), so the model would otherwise invent a plausible post from nothing.
        extractTweet(url)?.let { return@withContext Extraction(it) }

        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Stash/1.0")
            // The old 150,000 cap silently truncated real articles: one measured page was 218,691
            // bytes with its prose starting at byte 189,410, so the pipeline only ever saw the
            // nav and table of contents. Truncation also sliced mid-tag, which defeated the
            // regex tag-stripper and leaked raw markup into the model's input.
            val html = connection.inputStream.bufferedReader().use { it.readText().take(MAX_HTML_CHARS) }

            val doc = Jsoup.parse(html, url)
            val ogTitle = doc.metaContent("og:title", "twitter:title")
                ?: doc.title().takeIf(String::isNotBlank)
            val ogDesc = doc.metaContent("og:description", "twitter:description", "description")
            val bodyText = doc.articleText()
            // Jsoup was given the base URL, so absUrl resolves protocol-relative and root-relative
            // image paths that would otherwise be undownloadable.
            val ogImage = doc.selectFirst(
                "meta[property=og:image], meta[name=og:image], " +
                    "meta[property=twitter:image], meta[name=twitter:image]",
            )?.absUrl("content")?.takeIf(String::isNotBlank)

            // Extraction degrades silently — a page whose prose we miss still "succeeds" and just
            // produces a thin summary — so the yield is worth logging. This is how the 286-chars-
            // of-navigation-menu bug was found.
            android.util.Log.d(
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

    /**
     * Downloads the header image into [imageDir] and returns its filename, or null on any failure.
     *
     * Runs once per save, never at render time: the feed reads these files from disk, so scrolling
     * makes no network requests and the app works offline. Failures are silent by design — a
     * missing image degrades the card to its text-only form, which is not worth failing a save or
     * bothering the user over.
     */
    private suspend fun cacheHeaderImage(imageUrl: String, id: String): String? {
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
                // Guard against a mislabelled or hostile URL handing back something huge: the
                // header is advisory, so the read below is capped independently.
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

                // Extension-less: the file is only ever decoded by content, and trusting a
                // remote-supplied suffix would let the URL dictate names on our filesystem.
                val name = "$id.img"
                File(dir, name).writeBytes(bytes)
                name
            }.getOrNull()
        }
    }

    /** First matching `<meta>` content value, trying each name/property in order. */
    private fun Document.metaContent(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        selectFirst("meta[property=$key], meta[name=$key]")
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    /**
     * Pulls the article prose out of a parsed page.
     *
     * Chrome is removed by element rather than regex, then a selector cascade looks for the usual
     * prose containers. The paragraph-density fallback matters more than it looks: on a measured
     * Hashnode/Next.js page the `<article>` element held only the header, hero image and a table
     * of contents, while the real paragraphs sat in a sibling `div.prose` — so trusting any single
     * container tag is not enough. Whichever candidate yields the most paragraph text wins.
     *
     * `internal` + [VisibleForTesting] rather than `private`: this is the narrowest seam that lets
     * a plain JVM test exercise the selector cascade directly against fixture HTML, without
     * standing up the rest of the repository (network, Room, the summarizer).
     */
    @VisibleForTesting
    internal fun Document.articleText(): String {
        // Comment widgets and related-post rails are prose-shaped, so they score well on paragraph
        // density and can outrank the article itself — one measured page led with "No comments yet.
        // Be the first to comment."
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

        // Paragraphs only, so residual link lists and TOC entries do not crowd out the prose.
        val paragraphs = best.select("p, h1, h2, h3, li")
            .map { it.text().trim() }
            .filter { it.length > 40 }

        // Collapse runs of whitespace inside each paragraph, but keep the paragraph breaks: they
        // are the only structure the model gets, and they stop separate points running together.
        return if (paragraphs.isEmpty()) {
            best.text().replace(WHITESPACE, " ").trim()
        } else {
            paragraphs.joinToString("\n") { it.replace(WHITESPACE, " ") }
        }
    }

    /**
     * Pulls a post's real text for x.com/twitter.com, which serve an empty JS shell to scrapers.
     * Returns null for any other host so normal scraping runs, and null for posts that are
     * genuinely unreachable (deleted or private) so the caller can skip summarizing.
     */
    private fun extractTweet(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        if (host != "x.com" && host != "twitter.com") return null
        return fetchViaFxTwitter(url) ?: fetchViaOEmbed(url)
    }

    /**
     * fxtwitter mirrors the post as JSON and expands t.co links to their real destination,
     * which matters because the shortener is meaningless to the summarizer.
     */
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

    /** Returns the body for a 2xx response, or null for anything else. */
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
    // Resolved to an absolute path here rather than stored as one: the app's data dir can move
    // between installs, so only the bare filename is durable.
    imagePath = imageFile.takeIf { it.isNotBlank() && imageDir != null }
        ?.let { File(imageDir, it).takeIf(File::exists)?.absolutePath },
    content = content,
    // Rows saved before the headline column, or when AI was unavailable, fall back to the summary.
    headline = headline.ifBlank { summary },
    summary = summary,
    // Cased on read so pre-existing rows match newly saved ones — see asDisplayTag().
    tags = tags.split(TAG_SEPARATOR).map(String::asDisplayTag).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
)

/**
 * The feed's mapper. Identical to [StashEntity.toModel] except that `content` stays empty — a list
 * row never carries the page body (see [StashListRow]). Chat reaches its item through
 * `observeItem`, which does load it.
 */
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
    tags = tags.split(TAG_SEPARATOR).map(String::asDisplayTag).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
)

/**
 * Title-cases a tag for display, so the filter row does not mix "V8" with "pointer compression".
 *
 * Only all-lowercase words are touched. Anything the model capitalised deliberately is left alone,
 * which is what keeps "Node.js", "V8", "iOS" and "gRPC" intact — naive title casing would turn
 * those into "Node.Js", "V8", "Ios" and "Grpc". Short connecting words stay lowercase unless they
 * lead the tag.
 */
private fun String.asDisplayTag(): String = trim()
    .split(' ')
    .filter(String::isNotEmpty)
    .mapIndexed { index, word ->
        when {
            // Mixed or upper case is a deliberate choice by the model — preserve it verbatim.
            word.any(Char::isUpperCase) -> word
            index > 0 && word in TAG_MINOR_WORDS -> word
            else -> word.replaceFirstChar(Char::uppercase)
        }
    }
    .joinToString(" ")

private val TAG_MINOR_WORDS = setOf("and", "or", "of", "for", "in", "on", "to", "the", "a", "an", "vs")

private const val TAG_SEPARATOR = " | "

/** Keeps the filter row scannable; the AI happily returns 7+ tags per item if allowed. */
private const val MAX_TAGS_PER_ITEM = 3

/** Below this, extraction returned boilerplate rather than real content. */
private const val MIN_EXTRACT_CHARS = 120

/**
 * How much page body to keep for the summarizer. The old 1,200 (~200 words) starved the model on
 * long articles. Measured ceiling is 8,192 tokens for the active Gemini Nano variant, and the
 * previous prompt used only ~300 of them, so there is room for this.
 */
private const val EXTRACT_BODY_CHARS = 8_000

/**
 * Read cap for the fetched document. Generous because prose can sit deep in the page — a measured
 * article had its first real paragraph at byte 189,410 of 218,691. Still bounded so a pathological
 * page cannot exhaust memory.
 */
private const val MAX_HTML_CHARS = 600_000

/**
 * Size ceiling for a cached header image. OpenGraph images are typically 50–300KB; 5MB leaves room
 * for an oversized hero without letting one page fill the user's storage.
 */
private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024

/** Matches the page-fetch agent: some CDNs serve differently (or 403) to unknown clients. */
private const val IMAGE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"

private val WHITESPACE = Regex("\\s+")

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
