package dev.cburlacu.stash.data.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun `extracts plain url directly`() {
        val input = "https://example.com/article"
        assertEquals("https://example.com/article", extractFirstUrl(input))
    }

    @Test
    fun `extracts url from share sheet text with prefix title`() {
        val input = "Building AI Apps with Gemini Nano https://youtu.be/xyz123"
        assertEquals("https://youtu.be/xyz123", extractFirstUrl(input))
    }

    @Test
    fun `extracts url from share sheet text with suffix commentary`() {
        val input = "Check this out https://github.com/torvalds/linux - thought you might find it interesting"
        assertEquals("https://github.com/torvalds/linux", extractFirstUrl(input))
    }

    @Test
    fun `extracts url from multi-line shared text`() {
        val input = """
            Interesting discussion on Hacker News:
            https://news.ycombinator.com/item?id=42000000
            
            Worth reading!
        """.trimIndent()
        assertEquals("https://news.ycombinator.com/item?id=42000000", extractFirstUrl(input))
    }

    @Test
    fun `strips trailing punctuation commonly attached in text`() {
        assertEquals("https://example.com/page", extractFirstUrl("Check out https://example.com/page."))
        assertEquals("https://example.com/page", extractFirstUrl("Read this: https://example.com/page,"))
        assertEquals("https://example.com/page", extractFirstUrl("Have you seen https://example.com/page?"))
        assertEquals("https://example.com/page", extractFirstUrl("Awesome project https://example.com/page!"))
    }

    @Test
    fun `strips enclosing parentheses around url`() {
        val input = "Here is the link (https://example.com/doc) for reference."
        assertEquals("https://example.com/doc", extractFirstUrl(input))
    }

    @Test
    fun `preserves url with internal balanced parentheses`() {
        val input = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(programming_language)", extractFirstUrl(input))
    }

    @Test
    fun `returns trimmed input when no http or https scheme exists`() {
        val input = "   github.com/torvalds/linux   "
        assertEquals("github.com/torvalds/linux", extractFirstUrl(input))
    }

    @Test
    fun `extracts http url`() {
        val input = "Old site: http://info.cern.ch/hypertext/WWW/TheProject.html"
        assertEquals("http://info.cern.ch/hypertext/WWW/TheProject.html", extractFirstUrl(input))
    }

    @Test
    fun `returns empty string for blank input`() {
        assertEquals("", extractFirstUrl(""))
        assertEquals("", extractFirstUrl("   "))
    }

    @Test
    fun `derives owner and repo fallback title for github urls`() {
        assertEquals("torvalds/linux", deriveFallbackTitle("https://github.com/torvalds/linux"))
        assertEquals("torvalds/linux", deriveFallbackTitle("https://github.com/torvalds/linux.git"))
        assertEquals("google/guava", deriveFallbackTitle("https://github.com/google/guava/issues/123"))
        assertEquals("flutter/flutter", deriveFallbackTitle("https://github.com/flutter/flutter/pull/456"))
    }

    @Test
    fun `derives owner and repo fallback title for gitlab urls`() {
        assertEquals("gitlab-org/gitlab", deriveFallbackTitle("https://gitlab.com/gitlab-org/gitlab"))
        assertEquals("inkscape/inkscape", deriveFallbackTitle("https://gitlab.com/inkscape/inkscape/-/issues/1"))
    }

    @Test
    fun `falls back to capitalized domain for github root or single segment paths`() {
        assertEquals("Github.com", deriveFallbackTitle("https://github.com/"))
        assertEquals("Github.com", deriveFallbackTitle("https://github.com/torvalds"))
    }

    @Test
    fun `derives capitalized domain for general websites`() {
        assertEquals("Nytimes.com", deriveFallbackTitle("https://www.nytimes.com/article/tech-update"))
        assertEquals("Theverge.com", deriveFallbackTitle("https://theverge.com/2026/future-of-ai"))
    }

    @Test
    fun `derives fallback title for urls without scheme`() {
        assertEquals("android/architecture-samples", deriveFallbackTitle("github.com/android/architecture-samples"))
        assertEquals("Kotlinlang.org", deriveFallbackTitle("kotlinlang.org/docs/home.html"))
    }
}
