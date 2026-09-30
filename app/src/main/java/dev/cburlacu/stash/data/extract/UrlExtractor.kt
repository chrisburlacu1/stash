package dev.cburlacu.stash.data.extract

import java.net.URI

private val URL_REGEX = Regex("""https?://[^\s<>"{}|\\^`]+""")

/**
 * Extracts the first HTTP or HTTPS URL from arbitrary text (such as text shared from
 * another app containing titles, headers, or commentary). If no HTTP/HTTPS URL is found,
 * returns the trimmed input text as fallback.
 */
fun extractFirstUrl(text: String): String {
    val match = URL_REGEX.find(text)?.value ?: return text.trim()
    val trimmedPunctuation = match.trimEnd('.', ',', ';', '!', '?')
    return if (trimmedPunctuation.endsWith(')') && !trimmedPunctuation.contains('(')) {
        trimmedPunctuation.dropLast(1)
    } else {
        trimmedPunctuation
    }
}

/**
 * Derives an immediate human-readable fallback title from a URL before network extraction
 * or on-device AI summarization completes.
 *
 * For GitHub and GitLab URLs pointing to repositories or nested repo paths, extracts
 * the "owner/repo" segment. For other sites, falls back to the capitalized domain name.
 */
fun deriveFallbackTitle(url: String): String {
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://$url"
    }
    val uri = runCatching { URI(normalized) }.getOrNull()
    val host = uri?.host?.removePrefix("www.")?.lowercase()
    val domain = host?.takeIf(String::isNotBlank) ?: "saved link"

    if (host == "github.com" || host == "gitlab.com" || host?.endsWith(".github.com") == true || host?.endsWith(".gitlab.com") == true) {
        val segments = uri.path?.split('/')?.filter { it.isNotBlank() }.orEmpty()
        if (segments.size >= 2) {
            return "${segments[0]}/${segments[1].removeSuffix(".git")}"
        }
    }

    return domain.replaceFirstChar(Char::uppercase)
}
