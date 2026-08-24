package com.example.stash.ui.briefing

/**
 * The four sections a briefing is rendered as. Each becomes one node on the timeline rail.
 *
 * Not every briefing carries all four — a single-source brief is asked for no comparison section
 * (see `briefingPrompt`), and a model that skips one simply produces no node for it.
 */
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

/** Which bucket a line is being collected into. [Preamble] is "before any header was recognised". */
private enum class Section { Preamble, BigPicture, Takeaways, Comparison, BottomLine }

/**
 * Splits a streamed markdown briefing into its four sections.
 *
 * The prompt asks for exact bold headers (`**The Big Picture**` and friends), so those are matched
 * first and exactly. Nano does drift, though — it drops the asterisks, adds a colon, numbers the
 * headings — so a looser keyword match runs as a fallback. Keeping them as two tiers is the point:
 * the loose rules are permissive enough that, applied to body text, they would swallow real content
 * as a heading.
 *
 * Unrecognised leading text goes to [Section.Preamble] and is **discarded**, not merged into The Big
 * Picture. Starting the state machine inside a real section is what made drift silent before: a
 * chatty "Here is your briefing:" opener became the overview, and a briefing whose first header the
 * matcher missed piled every later section into it too.
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

/**
 * The section this line opens, or null if it is body text.
 *
 * Exact match on the requested headers first; keyword match second, and only for lines that look
 * structurally like a heading rather than prose.
 */
private fun headerSectionOf(trimmedLine: String): Section? {
    // Leading numbering is stripped before anything else, so "3. Comparisons & Trade-offs" reaches
    // the exact matcher as the header it plainly is.
    val bare = trimmedLine.stripHeadingDecoration().stripLeadingNumber()
    if (bare.isEmpty()) return null

    exactSectionOf(bare)?.let { return it }
    if (!looksLikeHeading(trimmedLine, bare)) return null
    return keywordSectionOf(bare.lowercase())
}

/** The headers the prompt actually asks for, compared without decoration or case. */
private fun exactSectionOf(bare: String): Section? = when (bare.lowercase()) {
    "the big picture" -> Section.BigPicture
    "key takeaways" -> Section.Takeaways
    "comparisons & trade-offs", "comparisons and trade-offs" -> Section.Comparison
    "the bottom line" -> Section.BottomLine
    else -> null
}

/**
 * The fallback tier. Deliberately not applied to arbitrary lines — a takeaway bullet can easily
 * contain the word "trade-off" mid-sentence, and treating that as a heading would drop the bullet
 * and misroute everything after it.
 */
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

/**
 * Whether a line is shaped like a heading: short, no sentence-internal punctuation, and either
 * markdown-marked (`#`, `**bold**`) or trailing a colon.
 *
 * The length cap alone was the old rule, which let a short bullet ("- Faster, but fewer features")
 * masquerade as a section break.
 */
private fun looksLikeHeading(trimmedLine: String, bare: String): Boolean {
    if (bare.length > HEADING_MAX_CHARS) return false
    // Sentence punctuation mid-line means prose, not a label.
    if (bare.contains(". ") || bare.endsWith(".")) return false

    val withoutBullet = trimmedLine.stripBulletMarker()
    val isMarkdownHeading = withoutBullet.startsWith("#")
    val isBolded = withoutBullet.startsWith("**")
    val isColonLabelled = bare.endsWith(":") || withoutBullet.trimEnd().endsWith(":")
    val isNumbered = NUMBERED_HEADING.matches(withoutBullet)

    return isMarkdownHeading || isBolded || isColonLabelled || isNumbered
}

/** Strips bullet markers, heading hashes, bold asterisks and a trailing colon down to bare words. */
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

/**
 * Drops `1. ` / `2) ` style numbering, which Nano adds unprompted. Applied only to the leading
 * position, so a decimal inside a sentence is untouched.
 */
private fun String.stripLeadingNumber(): String = LEADING_NUMBER.replace(this, "").trim()

private val LEADING_NUMBER = Regex("""^\d+[.)]\s+""")

/** `1. Key Takeaways` / `2) The Bottom Line` — numbering the prompt never asked for but Nano adds. */
private val NUMBERED_HEADING = Regex("""^\d+[.)]\s+\*{0,2}[\w &'\-]+\*{0,2}:?$""")

/**
 * Longest a line can be and still be a heading. The requested headers top out at 24 characters
 * ("Comparisons & Trade-offs"); the slack absorbs numbering and light rewording without reaching
 * the length of a real bullet.
 */
private const val HEADING_MAX_CHARS = 48
