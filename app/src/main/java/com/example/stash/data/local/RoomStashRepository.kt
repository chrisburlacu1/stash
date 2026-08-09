package com.example.stash.data.local

import com.example.stash.ai.AiAvailability
import com.example.stash.ai.OnDeviceSummarizer
import com.example.stash.ai.categoryForDomain
import com.example.stash.data.StashRepository
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomStashRepository(
    private val dao: StashDao,
    private val summarizer: OnDeviceSummarizer,
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
                rows.distinctBy(StashEntity::id)
                    .map(StashEntity::toModel)
                    .filter { item -> tags.isEmpty() || item.tags.containsAll(tags) }
            }
    }

    override fun observeItem(id: String): Flow<StashItem?> =
        dao.observeItem(id).map { it?.toModel() }

    override fun observeTags(): Flow<List<String>> = dao.observeAllTags().map { tagsList ->
        tagsList.flatMap { it.split(TAG_SEPARATOR) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
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
        val extractedText = extractReadableText(normalized)
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
        val organized = if (available && hasContent) {
            summarizer.organize(normalized, extractedText, knownTags)
        } else null
        val category = organized?.category ?: categoryForDomain(normalized) ?: "Unsorted"

        dao.upsert(
            initialEntity.copy(
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

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun getModelVersion(): String = summarizer.getModelVersion()

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
     */
    private fun reconcileTags(tags: List<String>, knownTags: List<String>): List<String> {
        val byNormalized = knownTags.associateBy { it.normalizedTag() }
        return tags.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
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

    private suspend fun extractReadableText(url: String): String = withContext(Dispatchers.IO) {
        // Social posts are JS-rendered shells that return no content (x.com answers 404 with an
        // empty SPA), so the model would otherwise invent a plausible post from nothing.
        extractTweet(url)?.let { return@withContext it }

        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Stash/1.0")
            val html = connection.inputStream.bufferedReader().use { it.readText().take(150_000) }

            // 1. Extract OpenGraph & Meta Description
            val ogTitle = Regex("""<meta[^>]+(?:property|name)=["'](?:og:title|twitter:title|title)["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
            val ogDesc = Regex("""<meta[^>]+(?:property|name)=["'](?:og:description|twitter:description|description)["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)

            // 2. Strip noise HTML (scripts, styles, headers, footers, navigation)
            val cleanHtml = html
                .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<header[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<footer[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<nav[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), " ")

            val bodyText = cleanHtml
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&nbsp;|&#160;"), " ")
                .replace("&amp;", "&")
                .replace(Regex("\\s+"), " ")
                .trim()

            buildString {
                if (!ogTitle.isNullOrBlank()) append("Title: ").append(ogTitle).append("\n")
                if (!ogDesc.isNullOrBlank()) append("Summary Note: ").append(ogDesc).append("\n")
                // The OpenGraph pair above usually carries the gist; the body is a shorter
                // supplement so the prompt stays small enough for fast on-device inference.
                append("Article Body: ").append(bodyText.take(1_200))
            }
        }.getOrDefault("Saved URL: $url")
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

private fun StashEntity.toModel() = StashItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    category = category,
    // Rows saved before the headline column, or when AI was unavailable, fall back to the summary.
    headline = headline.ifBlank { summary },
    summary = summary,
    tags = tags.split(TAG_SEPARATOR).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
)

private const val TAG_SEPARATOR = " | "

/** Keeps the filter row scannable; the AI happily returns 7+ tags per item if allowed. */
private const val MAX_TAGS_PER_ITEM = 3

/** Below this, extraction returned boilerplate rather than real content. */
private const val MIN_EXTRACT_CHARS = 120

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
