package dev.cburlacu.stash.data.extract

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

object TwitterExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"

    fun isTwitterUrl(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        return host == "x.com" || host == "twitter.com"
    }

    fun extractTweet(url: String): Extraction? {
        if (!isTwitterUrl(url)) return null
        return fetchViaFxTwitter(url) ?: fetchViaVxTwitter(url) ?: fetchViaOEmbed(url)
    }

    fun fetchViaFxTwitter(url: String): Extraction? = runCatching {
        val path = URI(url).path.orEmpty()
        val json = readText("https://api.fxtwitter.com$path") ?: return@runCatching null
        parseFxTwitterJson(json)
    }.getOrNull()

    fun parseFxTwitterJson(json: String): Extraction? {
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

    fun fetchViaVxTwitter(url: String): Extraction? = runCatching {
        val path = URI(url).path.orEmpty()
        val json = readText("https://api.vxtwitter.com$path") ?: return@runCatching null
        parseVxTwitterJson(json)
    }.getOrNull()

    fun parseVxTwitterJson(json: String): Extraction? {
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

    fun fetchViaOEmbed(url: String): Extraction? = runCatching {
        val endpoint = "https://publish.twitter.com/oembed?omit_script=true&url=" +
            URLEncoder.encode(url, "UTF-8")
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

    fun upgradeTwitterAvatarUrl(url: String): String {
        return url.replace(Regex("_(?:normal|mini|bigger|200x200|x96|\\d+x\\d+)\\."), "_400x400.")
    }

    fun upgradeTwitterPhotoUrl(url: String): String {
        if (!url.contains("pbs.twimg.com/media/")) return url
        return if (url.contains("?name=") || url.contains("&name=")) {
            url.replace(Regex("([?&]name=)(?:small|medium|thumb)"), "$1large")
        } else if (url.contains("?")) {
            "$url&name=large"
        } else {
            "$url?name=large"
        }
    }

    fun readText(endpoint: String): String? = runCatching {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun buildTweetText(author: String?, text: String): String = buildString {
        append("Social media post")
        if (!author.isNullOrBlank()) append(" by ").append(author)
        append(".\n")
        append("Post text: ").append(text.replace(Regex("\\s+"), " ").take(1_500))
    }

    private fun String.unescapeJson(): String = replace("\\/", "/")
        .replace("\\n", " ")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
}
