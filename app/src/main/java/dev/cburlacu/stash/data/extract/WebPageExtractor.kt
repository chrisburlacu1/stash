package dev.cburlacu.stash.data.extract

import dev.cburlacu.stash.util.StashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URL

object WebPageExtractor {

    private const val MAX_HTML_CHARS = 600_000
    private const val EXTRACT_BODY_CHARS = 8_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"
    private val WHITESPACE = Regex("\\s+")

    suspend fun extractReadableText(url: String): Extraction = withContext(Dispatchers.IO) {
        TwitterExtractor.extractTweet(url)?.let { return@withContext it }

        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
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

    fun Document.metaContent(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        selectFirst("meta[property=$key], meta[name=$key]")
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    fun Document.articleText(): String {
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
}
