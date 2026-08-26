package com.example.stash.ui.briefing

internal sealed interface BriefingNode {
    val id: String
    val level: Int

    data class BigPicture(
        override val id: String = "node_big_picture",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class KeyTakeaways(
        override val id: String = "node_takeaways",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class Comparison(
        override val id: String = "node_comparison",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode

    data class BottomLine(
        override val id: String = "node_bottom_line",
        override val level: Int = 0,
        val lines: List<String>,
    ) : BriefingNode
}

private enum class Section { Preamble, BigPicture, Takeaways, Comparison, BottomLine }

/**
 * Splits a streamed markdown briefing into its structured sections.
 */
internal fun parseBriefingToNodes(rawText: String): List<BriefingNode> {
    if (rawText.isBlank()) return emptyList()

    val buckets = mutableMapOf<Section, MutableList<String>>()
    var current = Section.Preamble

    for (rawLine in rawText.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue

        val header = headerSectionOf(trimmed)
        if (header != null) {
            current = header
            continue
        }
        if (current == Section.Preamble) continue

        buckets.getOrPut(current) { mutableListOf() }.add(rawLine)
    }

    return buildList {
        buckets[Section.BigPicture]?.let { add(BriefingNode.BigPicture(lines = it)) }
        buckets[Section.Takeaways]?.let { add(BriefingNode.KeyTakeaways(lines = it)) }
        buckets[Section.Comparison]?.let { add(BriefingNode.Comparison(lines = it)) }
        buckets[Section.BottomLine]?.let { add(BriefingNode.BottomLine(lines = it)) }
    }
}

private fun headerSectionOf(trimmedLine: String): Section? {
    val bare = trimmedLine.stripHeadingDecoration().stripLeadingNumber()
    if (bare.isEmpty()) return null

    exactSectionOf(bare)?.let { return it }
    if (!looksLikeHeading(trimmedLine, bare)) return null
    return keywordSectionOf(bare.lowercase())
}

private fun exactSectionOf(bare: String): Section? = when (bare.lowercase()) {
    "the big picture" -> Section.BigPicture
    "key takeaways" -> Section.Takeaways
    "comparisons & trade-offs", "comparisons and trade-offs" -> Section.Comparison
    "the bottom line" -> Section.BottomLine
    else -> null
}

private fun keywordSectionOf(lower: String): Section? = when {
    lower.contains("big picture") || lower.contains("overview") -> Section.BigPicture
    lower.contains("trade-off") || lower.contains("tradeoff") ||
        lower.contains("comparison") || lower.contains("differences") -> Section.Comparison
    lower.contains("takeaway") || lower.contains("key point") ||
        lower.contains("insight") || lower.contains("finding") -> Section.Takeaways
    lower.contains("bottom line") || lower.contains("conclusion") ||
        lower.contains("verdict") || lower.contains("recommendation") -> Section.BottomLine
    else -> null
}

private fun looksLikeHeading(trimmedLine: String, bare: String): Boolean {
    if (bare.length > HEADING_MAX_CHARS) return false
    if (bare.contains(". ") || bare.endsWith(".")) return false

    val withoutBullet = trimmedLine.stripBulletMarker()
    val isMarkdownHeading = withoutBullet.startsWith("#")
    val isBolded = withoutBullet.startsWith("**")
    val isColonLabelled = bare.endsWith(":") || withoutBullet.trimEnd().endsWith(":")
    val isNumbered = NUMBERED_HEADING.matches(withoutBullet)

    return isMarkdownHeading || isBolded || isColonLabelled || isNumbered
}

private fun String.stripHeadingDecoration(): String = stripBulletMarker()
    .trimStart('#', ' ')
    .trim()
    .removeSurrounding("**")
    .removePrefix("**")
    .removeSuffix("**")
    .trim()
    .removeSuffix(":")
    .trim()
    .removeSuffix("**")
    .trim()

private fun String.stripBulletMarker(): String = trim()
    .removePrefix("* ")
    .removePrefix("- ")
    .removePrefix("• ")
    .trim()

private fun String.stripLeadingNumber(): String = LEADING_NUMBER.replace(this, "").trim()

private val LEADING_NUMBER = Regex("""^\d+[.)]\s+""")
private val NUMBERED_HEADING = Regex("""^\d+[.)]\s+\*{0,2}[\w &'\-]+\*{0,2}:?$""")
private const val HEADING_MAX_CHARS = 48
