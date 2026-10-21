package dev.cburlacu.stash.ai

private const val MAX_CHAT_HISTORY_TURNS = 12

/**
 * Splits a prompt into a static, reusable [prefix] (cached by ML Kit GenAI's prefix caching)
 * and a per-request dynamic [suffix].
 *
 * When prefix caching is unavailable on the current device/model, [fullText] concatenates both
 * sections into a single standard prompt.
 */
internal data class CachedPrompt(
    val prefix: String,
    val suffix: String,
) {
    fun fullText(): String = if (prefix.isBlank()) suffix else "$prefix\n\n$suffix"
}

internal const val RICH_PROMPT_PREFIX = """Summarize this saved link so it can be rediscovered later. Reply with ONLY this JSON:
{"title":"","takeaway":"","keyPoints":["",""],"category":"","topic":"","tags":[]}
title: the real title of the page or post.
takeaway: max 12 words, why this is worth remembering, no period, must not repeat the title.
keyPoints: 3 to 5 short bullets of the substance — specifics, names, numbers, conclusions.
topic: 1 broad, top-level umbrella discipline representing the whole subject (strongly prefer a single noun like 'Design', 'Android', 'AI', 'Film', 'Engineering', 'Finance', 'Productivity', 'General'; max 2 words). Put narrower sub-fields or systems (e.g. 'UI Design', 'Material You', 'Short Film') into tags, NOT topic.
tags: 2 to 4 canonical singular tags for the core subject, tools, technologies, and concepts directly discussed in the content (1 to 3 words per tag). Ground tags strictly in the content (e.g. for terminal agent utilities use 'AI Agent', 'CLI', 'Developer Tool'; do not use generic parent tags like 'Machine Learning' unless the page is specifically about ML model training or algorithms). Always use singular nouns (e.g. 'Screenplay' not 'Screenplays', 'Recipe' not 'Recipes', 'Agent' not 'Agents'). Do not include format words ('Podcast', 'Audio', 'Episode', 'Article', 'Video', 'Post', 'Website', 'Newsletter').
Use only the content below. Do not use outside knowledge. If it is empty or unclear, say so rather than guessing."""

internal const val SCHEMA_PROMPT_PREFIX = """Summarize this saved link so it can be rediscovered later.
Topic guidelines:
- Assign 1 broad, top-level umbrella discipline representing the whole subject (strongly prefer a single noun like 'Design', 'Android', 'AI', 'Film', 'Engineering', 'Finance', 'Productivity', 'General'; max 2 words).
- Put narrower sub-fields, mediums, or design systems (e.g. 'UI Design', 'Material You', 'Short Film', 'Jetpack Compose') in tags, NOT topic.
Tagging guidelines:
- Extract 2 to 4 concise, canonical tags representing the core subject, specific tools, libraries, technologies, and concepts directly discussed in the page (1 to 3 words per tag).
- Ground tags strictly in the actual content (e.g. for CLI utilities use 'AI Agent', 'CLI', 'Developer Tool'; do not invent generic parent tags like 'Machine Learning' unless the text is specifically about ML model training or algorithms).
- Always use canonical singular nouns (e.g. 'Screenplay' not 'Screenplays', 'Recipe' not 'Recipes', 'AI Agent' not 'AI Agents', 'Agent' not 'Agents').
- Exclude format noise words (e.g. 'Podcast', 'Audio', 'Episode', 'Article', 'Video', 'Post', 'Website', 'Newsletter').
- Use only the content below. Do not use outside knowledge."""

internal const val INFER_TOPIC_PROMPT_PREFIX = """Classify this saved link into ONE broad, top-level umbrella topic discipline (prefer 1 word, 2 words max).
Examples: Design, Android, AI, Film, Engineering, Tools, Productivity, Finance, Web Development, Backend, DevOps.
Keep the topic broad (e.g. 'Design' rather than 'UI Design').
Reply with ONLY the topic name, nothing else."""

internal fun buildRichPrompt(
    url: String,
    content: String,
    knownTags: List<String> = emptyList(),
    knownTopics: List<String> = emptyList(),
): CachedPrompt {
    val knownCategory = categoryForDomain(url)
    val categoryGuidance = if (knownCategory != null) {
        """"category" MUST be exactly "$knownCategory"."""
    } else {
        """"category" MUST be one of: Article, Documentation, Repo, Video, Discussion."""
    }
    val suffix = buildString {
        appendLine(categoryGuidance)
        if (knownTopics.isNotEmpty()) {
            appendLine(
                "Reuse an active library topic ONLY if it accurately covers the whole subject (${knownTopics.take(8).joinToString(", ")}); " +
                    "do not force-fit into a narrower sub-topic if the article is broader or conceptual."
            )
        }
        if (knownTags.isNotEmpty()) {
            appendLine("When appropriate, align tags with active library tags: ${knownTags.take(10).joinToString(", ")}.")
        }
        appendLine()
        appendLine("URL: $url")
        append("Content: $content")
    }
    return CachedPrompt(
        prefix = RICH_PROMPT_PREFIX,
        suffix = suffix,
    )
}

internal fun buildSchemaPrompt(
    url: String,
    content: String,
    knownTags: List<String> = emptyList(),
    knownTopics: List<String> = emptyList(),
): CachedPrompt {
    val suffix = buildString {
        if (knownTopics.isNotEmpty()) {
            val sampleTopics = knownTopics.take(8).joinToString(", ")
            appendLine(
                "Reuse an active library topic ONLY if it accurately covers the whole subject ($sampleTopics); " +
                    "do not force-fit into a narrower sub-topic if the article is broader or conceptual."
            )
        }
        if (knownTags.isNotEmpty()) {
            val sampleTags = knownTags.take(10).joinToString(", ")
            appendLine("When appropriate, align with active library tags: $sampleTags")
        }
        appendLine("URL: $url")
        append("Content: $content")
    }
    return CachedPrompt(
        prefix = SCHEMA_PROMPT_PREFIX,
        suffix = suffix,
    )
}

internal fun buildBriefingPrompt(
    itemsContext: String,
    itemCount: Int,
    topic: String?,
    history: List<ChatTurn>,
    question: String?,
): CachedPrompt {
    // Prefix holds the invariant persona + the large multi-item context (up to 6,000 chars)
    // so both the initial briefing and every follow-up question in BriefingChatSheet share the cache.
    val prefix = buildString {
        appendLine("You are Stash's on-device intelligence. You generate clear, actionable executive briefings for saved links.")
        appendLine("Synthesize the provided saved items into a concise, practical brief.")
        appendLine()
        appendLine("Saved Items:")
        append(itemsContext.trim())
    }

    val suffix = buildString {
        if (question == null) {
            val wantsComparison = itemCount > 1

            appendLine("Format guidelines:")
            if (!topic.isNullOrBlank()) {
                appendLine("- Focus the brief on the topic: $topic")
            }
            appendLine("- Do NOT include any introductory title such as 'Executive Briefing:'. Start directly with **The Big Picture**.")
            if (wantsComparison) {
                appendLine("- Use these exact bold section headers on their own line: **The Big Picture**, **Key Takeaways**, **Comparisons & Trade-offs**, and **The Bottom Line**.")
            } else {
                appendLine("- Use these exact bold section headers on their own line: **The Big Picture**, **Key Takeaways**, and **The Bottom Line**.")
            }
            appendLine("- Under **The Big Picture**: 1 to 2 sentences summarizing the core theme connecting these items.")
            appendLine("- Under **Key Takeaways**: Bullet points summarizing the primary insights, findings, and actionable takeaways from the saved items.")
            if (wantsComparison) {
                appendLine("- Under **Comparisons & Trade-offs**: Bullet points and comparisons highlighting how the tools/approaches differ, their trade-offs, and relative strengths.")
            } else {
                appendLine("- There is only one source. Do NOT write a comparisons or trade-offs section, and do not compare it to anything not present.")
            }
            appendLine("- Under **The Bottom Line**: 1 concise concluding takeaway or recommendation.")
            appendLine("- Use clean markdown with bold section headers and bullet points. Be direct, dense with substance, and avoid fluff or filler phrases like 'In conclusion'.")
        } else {
            appendLine("Format guidelines:")
            appendLine("- Answer the user's question directly, grounding your response in the saved items above. Keep answers concise and direct.")
        }
        if (history.isNotEmpty()) {
            appendLine()
            appendLine("Conversation:")
            history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
                appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
            }
        }
        if (question != null) {
            appendLine()
            appendLine("User: $question")
        }
        append("Assistant:")
    }

    return CachedPrompt(
        prefix = prefix,
        suffix = suffix,
    )
}

internal fun buildChatPrompt(
    itemContext: String,
    history: List<ChatTurn>,
    question: String,
): CachedPrompt {
    // Prefix holds the instructions + full scraped item context (~4,000 chars) so every
    // follow-up question in the chat session hits the cached prefix state.
    val prefix = buildString {
        appendLine(
            "You are Stash's assistant, answering questions about a link the user saved. " +
                "You run entirely on this device."
        )
        appendLine(
            "Answer in plain text — no markdown, no bullet syntax. Be concrete and brief: " +
                "a few sentences unless the question genuinely needs more."
        )
        appendLine(
            "Ground answers in the saved item content and notes below. General knowledge is fine, but do not " +
                "invent details about the page beyond what is provided in the saved item."
        )
        appendLine()
        appendLine("Saved item:")
        append(itemContext.trim())
    }

    val suffix = buildString {
        appendLine("Conversation:")
        history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
            appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
        }
        appendLine("User: $question")
        append("Assistant:")
    }

    return CachedPrompt(
        prefix = prefix,
        suffix = suffix,
    )
}

internal fun buildInferTopicPrompt(
    title: String,
    summary: String,
    tags: String,
    knownTopics: List<String>,
): CachedPrompt {
    val suffix = buildString {
        if (knownTopics.isNotEmpty()) {
            appendLine(
                "Existing topics in this library: ${knownTopics.take(10).joinToString(", ")}. " +
                    "Reuse one ONLY if it accurately covers the whole subject; do not force-fit into a narrower sub-topic when a broader umbrella topic is more accurate."
            )
            appendLine()
        }
        appendLine("Title: ${title.take(100)}")
        appendLine("Summary: ${summary.take(200)}")
        append("Tags: ${tags.take(100)}")
    }
    return CachedPrompt(
        prefix = INFER_TOPIC_PROMPT_PREFIX,
        suffix = suffix,
    )
}

