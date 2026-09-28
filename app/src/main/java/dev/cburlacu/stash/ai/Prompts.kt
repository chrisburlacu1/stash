package dev.cburlacu.stash.ai

private const val MAX_CHAT_HISTORY_TURNS = 12

internal fun buildRichPrompt(
    url: String,
    content: String,
    knownTags: List<String> = emptyList(),
    knownTopics: List<String> = emptyList(),
): String {
    val knownCategory = categoryForDomain(url)
    val categoryGuidance = if (knownCategory != null) {
        """"category" MUST be exactly "$knownCategory"."""
    } else {
        """"category" MUST be one of: Article, Documentation, Repo, Video, Discussion."""
    }
    val existingTopicsHint = if (knownTopics.isNotEmpty()) {
        " When appropriate, align with active library topics: ${knownTopics.take(8).joinToString(", ")}."
    } else ""
    val existingTagsHint = if (knownTags.isNotEmpty()) {
        " When appropriate, align with active library tags: ${knownTags.take(10).joinToString(", ")}."
    } else ""
    val topicGuidance = "topic: 1 high-level topic or domain representing the subject (e.g. 'Android', 'Design', 'React', 'AI', 'Tools', 'Productivity', 'Finance', 'General'). Max 2 words.$existingTopicsHint"
    val tagGuidance = "tags: 2 to 4 canonical singular tags for the core subject, tools, technologies, and concepts directly discussed in the content (1 to 3 words per tag). Ground tags strictly in the content (e.g. for terminal agent utilities use 'AI Agent', 'CLI', 'Developer Tool'; do not use generic parent tags like 'Machine Learning' unless the page is specifically about ML model training or algorithms). Always use singular nouns (e.g. 'Screenplay' not 'Screenplays', 'Recipe' not 'Recipes', 'Agent' not 'Agents'). Do not include format words ('Podcast', 'Audio', 'Episode', 'Article', 'Video', 'Post', 'Website', 'Newsletter').$existingTagsHint"
    return """
        Summarize this saved link so it can be rediscovered later. Reply with ONLY this JSON:
        {"title":"","takeaway":"","keyPoints":["",""],"category":"","topic":"","tags":[]}
        title: the real title of the page or post.
        takeaway: max 12 words, why this is worth remembering, no period, must not repeat the title.
        keyPoints: 3 to 5 short bullets of the substance — specifics, names, numbers, conclusions.
        $categoryGuidance
        $topicGuidance
        $tagGuidance
        Use only the content below. Do not use outside knowledge. If it is empty or unclear,
        say so rather than guessing.

        URL: $url
        Content: $content
    """.trimIndent()
}

internal fun buildSchemaPrompt(
    url: String,
    content: String,
    knownTags: List<String> = emptyList(),
    knownTopics: List<String> = emptyList(),
): String = buildString {
    appendLine("Summarize this saved link so it can be rediscovered later.")
    appendLine("Topic guidelines:")
    appendLine("- Assign 1 high-level topic or domain representing the subject (e.g. 'Android', 'Design', 'React', 'AI', 'Tools', 'Productivity', 'Finance', 'General'). Max 2 words.")
    if (knownTopics.isNotEmpty()) {
        val sampleTopics = knownTopics.take(8).joinToString(", ")
        appendLine("- When appropriate, align with active library topics: $sampleTopics")
    }
    appendLine("Tagging guidelines:")
    appendLine("- Extract 2 to 4 concise, canonical tags representing the core subject, specific tools, libraries, technologies, and concepts directly discussed in the page (1 to 3 words per tag).")
    appendLine("- Ground tags strictly in the actual content (e.g. for CLI utilities use 'AI Agent', 'CLI', 'Developer Tool'; do not invent generic parent tags like 'Machine Learning' unless the text is specifically about ML model training or algorithms).")
    appendLine("- Always use canonical singular nouns (e.g. 'Screenplay' not 'Screenplays', 'Recipe' not 'Recipes', 'AI Agent' not 'AI Agents', 'Agent' not 'Agents').")
    appendLine("- Exclude format noise words (e.g. 'Podcast', 'Audio', 'Episode', 'Article', 'Video', 'Post', 'Website', 'Newsletter').")
    if (knownTags.isNotEmpty()) {
        val sampleTags = knownTags.take(10).joinToString(", ")
        appendLine("- When appropriate, align with active library tags: $sampleTags")
    }
    appendLine("- Use only the content below. Do not use outside knowledge.")
    appendLine()
    appendLine("URL: $url")
    appendLine("Content: $content")
}

internal fun buildBriefingPrompt(
    itemsContext: String,
    itemCount: Int,
    topic: String?,
    history: List<ChatTurn>,
    question: String?,
): String = buildString {
    appendLine("You are Stash's on-device intelligence. You generate clear, actionable executive briefings for saved links.")
    appendLine("Synthesize the provided saved items into a concise, practical brief.")
    appendLine()
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
        appendLine("- Answer the user's question directly, grounding your response in the saved items below. Keep answers concise and direct.")
    }
    appendLine()
    appendLine("Saved Items:")
    appendLine(itemsContext.trim())
    appendLine()
    if (history.isNotEmpty()) {
        appendLine("Conversation:")
        history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
            appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
        }
    }
    if (question != null) {
        appendLine("User: $question")
    }
    append("Assistant:")
}

internal fun buildChatPrompt(
    itemContext: String,
    history: List<ChatTurn>,
    question: String,
): String = buildString {
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
    appendLine(itemContext.trim())
    appendLine()
    appendLine("Conversation:")
    history.takeLast(MAX_CHAT_HISTORY_TURNS).forEach { turn ->
        appendLine("${if (turn.fromUser) "User" else "Assistant"}: ${turn.text}")
    }
    appendLine("User: $question")
    append("Assistant:")
}

internal fun buildInferTopicPrompt(
    title: String,
    summary: String,
    tags: String,
    knownTopics: List<String>,
): String {
    val topicsHint = if (knownTopics.isNotEmpty()) {
        "\nExisting topics in this library: ${knownTopics.take(10).joinToString(", ")}. " +
            "Use one of these if the content fits. Otherwise create a new 1-2 word topic."
    } else ""
    return """Classify this saved link into ONE high-level topic domain (1-2 words max).
Examples: Android, Design, React, AI, Tools, Productivity, Finance, Web Development, Backend, DevOps.
$topicsHint
Reply with ONLY the topic name, nothing else.

Title: ${title.take(100)}
Summary: ${summary.take(200)}
Tags: ${tags.take(100)}"""
}
