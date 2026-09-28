package dev.cburlacu.stash.data.prompt

import dev.cburlacu.stash.models.StashItem

object PromptContextBuilders {

    const val MAX_BRIEFING_ITEMS = 8
    const val BRIEFING_EXCERPT_BUDGET = 6_000
    const val CHAT_MAX_CONTENT_CHARS = 4_000

    fun itemsBriefingContext(
        items: List<StashItem>,
        maxItems: Int = MAX_BRIEFING_ITEMS,
        excerptBudgetTotal: Int = BRIEFING_EXCERPT_BUDGET,
    ): String = buildString {
        val included = items.take(maxItems)
        val excerptBudget = if (included.isEmpty()) 0 else excerptBudgetTotal / included.size

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

    fun itemChatContext(
        item: StashItem,
        maxContentChars: Int = CHAT_MAX_CONTENT_CHARS,
    ): String = buildString {
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
            appendLine(item.content.take(maxContentChars))
        }
    }
}
