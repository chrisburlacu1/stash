package com.example.stash.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AiAvailability {
    data object Available : AiAvailability
    data object Unavailable : AiAvailability
}

/** Domains whose content type is unambiguous, so the model never gets to guess it wrong. */
private val DOMAIN_CATEGORIES = mapOf(
    "x.com" to "Tweet",
    "twitter.com" to "Tweet",
    "bsky.app" to "Tweet",
    "threads.net" to "Tweet",
    "mastodon.social" to "Tweet",
    "github.com" to "GitHub Repo",
    "gitlab.com" to "GitHub Repo",
    "youtube.com" to "Video",
    "youtu.be" to "Video",
    "vimeo.com" to "Video",
    "reddit.com" to "Discussion",
    "news.ycombinator.com" to "Discussion",
    "stackoverflow.com" to "Discussion",
)

internal fun categoryForDomain(url: String): String? {
    val host = runCatching { java.net.URI(url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.lowercase()
        ?: return null
    return DOMAIN_CATEGORIES[host]
        ?: DOMAIN_CATEGORIES.entries.firstOrNull { host.endsWith(".${it.key}") }?.value
}

interface OnDeviceSummarizer {
    suspend fun availability(): AiAvailability
    suspend fun organize(url: String, content: String, knownTags: List<String>): OrganizedContent?
    suspend fun getModelVersion(): String
}

data class OrganizedContent(
    val title: String,
    /** One short line for the feed row, so the list never shows a truncated paragraph. */
    val headline: String,
    val summary: String,
    val category: String,
    val tags: List<String>,
)

@Serializable
private data class OrganizedResponse(
    val title: String,
    val headline: String = "",
    val summary: String,
    val category: String,
    val tags: List<String>,
)

class GeminiNanoSummarizer : OnDeviceSummarizer {
    private val model by lazy { Generation.getClient() }
    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun getModelVersion(): String = runCatching {
        model.getBaseModelName()
    }.getOrDefault("Gemini Nano")

    override suspend fun availability(): AiAvailability = runCatching {
        val status = model.checkStatus()
        if (status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            AiAvailability.Available
        } else {
            AiAvailability.Unavailable
        }
    }.getOrDefault(AiAvailability.Unavailable)

    override suspend fun organize(
        url: String,
        content: String,
        knownTags: List<String>,
    ): OrganizedContent? {
        if (availability() != AiAvailability.Available) return null

        // Inference time scales with prompt length, so the payload is kept tight: 1,200 chars
        // is enough for a title/summary and roughly halves latency versus 3,000.
        val boundedContent = content.take(1_200)

        // categoryForDomain() settles known hosts deterministically, so the prompt only needs
        // the category list — the per-domain rules would be tokens spent re-deriving it.
        val knownCategory = categoryForDomain(url)
        val categoryGuidance = if (knownCategory != null) {
            """"category" MUST be exactly "$knownCategory"."""
        } else {
            """"category" MUST be one of: Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website."""
        }

        // Only the closest existing tags are offered; sending the whole vocabulary grows the
        // prompt without improving reuse.
        val tagGuidance = if (knownTags.isEmpty()) {
            "Give 1-3 broad topic tags."
        } else {
            "Give 1-3 broad topic tags, reusing these exactly where they fit: " +
                knownTags.take(12).joinToString(", ")
        }

        val prompt = """
            Summarize this saved link. Reply with ONLY this JSON:
            {"title":"","headline":"","summary":"","category":"","tags":[]}
            title: the real title of the page or post.
            headline: max 10 words, no period, must not repeat the title.
            summary: two sentences.
            $categoryGuidance
            $tagGuidance
            Use only the content below. If it is empty or unclear, say so rather than guessing.

            URL: $url
            Content: $boundedContent
        """.trimIndent()
        return runCatching {
            val raw = model.generateContent(prompt).candidates.firstOrNull()?.text?.trim().orEmpty()
            val jsonText = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            jsonParser.decodeFromString<OrganizedResponse>(jsonText).let { response ->
                OrganizedContent(
                    title = response.title.trim(),
                    // Falls back to the first sentence of the summary when the model omits or
                    // over-runs the headline, so the feed still gets a short line.
                    headline = response.headline.trim()
                        .ifBlank { response.summary.trim().substringBefore('.') }
                        .removeSuffix(".")
                        .take(90),
                    summary = response.summary.trim(),
                    // The domain is ground truth where we have it; the model otherwise labels
                    // short social posts as "Article" because they read like prose.
                    category = categoryForDomain(url)
                        ?: response.category.trim().take(32).ifBlank { "Unsorted" },
                    tags = response.tags.map(String::trim).filter(String::isNotBlank).distinct().take(8),
                )
            }
        }.getOrNull()
    }
}
