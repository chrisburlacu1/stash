package dev.cburlacu.stash.ai

import java.net.URI

private val DOMAIN_CATEGORIES = mapOf(
    "x.com" to "Discussion",
    "twitter.com" to "Discussion",
    "bsky.app" to "Discussion",
    "threads.net" to "Discussion",
    "mastodon.social" to "Discussion",
    "github.com" to "Repo",
    "gitlab.com" to "Repo",
    "youtube.com" to "Video",
    "youtu.be" to "Video",
    "vimeo.com" to "Video",
    "reddit.com" to "Discussion",
    "news.ycombinator.com" to "Discussion",
    "stackoverflow.com" to "Discussion",
)

/**
 * Returns the default category string if the domain has a known semantic mapping.
 */
internal fun categoryForDomain(url: String): String? {
    val host = runCatching { URI(url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.lowercase()
        ?: return null
    return DOMAIN_CATEGORIES[host]
        ?: DOMAIN_CATEGORIES.entries.firstOrNull { host.endsWith(".${it.key}") }?.value
}

/**
 * Sanitizes page titles by stripping redundant site branding and repository taglines.
 */
internal fun cleanTitle(rawTitle: String, url: String): String {
    if (rawTitle.isBlank()) return ""
    var title = rawTitle.trim()

    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: ""

    val siteSuffixes = listOf(
        " · GitHub",
        " - GitHub",
        " | GitHub",
        " · GitLab",
        " - GitLab",
        " | Hacker News",
        " - YouTube",
        " | YouTube",
        " - Substack",
        " | Substack",
        " | Medium",
        " - Medium",
        " - Google Design",
        " | Google Design",
        " · Google Design",
        " - Google",
        " | Google",
        " · Google",
    )
    for (suffix in siteSuffixes) {
        if (title.endsWith(suffix, ignoreCase = true)) {
            title = title.substring(0, title.length - suffix.length).trim()
        }
    }

    if (host.contains("github.com")) {
        if (title.startsWith("GitHub - ", ignoreCase = true)) {
            title = title.substring(9).trim()
        } else if (title.startsWith("GitHub: ", ignoreCase = true)) {
            title = title.substring(8).trim()
        }

        if (title.contains(": ")) {
            val beforeColon = title.substringBefore(": ").trim()
            if (beforeColon.matches(Regex("""^[\w\-.]+/[\w\-.]+$"""))) {
                title = beforeColon
            }
        }
    }

    return title
}
