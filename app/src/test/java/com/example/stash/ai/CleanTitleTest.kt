package com.example.stash.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanTitleTest {

    @Test
    fun `github repository title with long tagline strips prefix and description`() {
        val raw = "GitHub - Graphify-Labs/graphify: Turn any codebase, with its docs, SQL schemas, configs, and PDFs, into a queryable knowledge graph."
        val cleaned = cleanTitle(raw, "https://github.com/Graphify-Labs/graphify")
        assertEquals("Graphify-Labs/graphify", cleaned)
    }

    @Test
    fun `github repository title without description strips github prefix`() {
        val raw = "GitHub - torvalds/linux"
        val cleaned = cleanTitle(raw, "https://github.com/torvalds/linux")
        assertEquals("torvalds/linux", cleaned)
    }

    @Test
    fun `github repository title with dot in repo name strips description`() {
        val raw = "GitHub - owner/repo.js: A lightweight JavaScript library"
        val cleaned = cleanTitle(raw, "https://github.com/owner/repo.js")
        assertEquals("owner/repo.js", cleaned)
    }

    @Test
    fun `github bare repo title with description strips description when url is github`() {
        val raw = "Graphify-Labs/graphify: Turn any codebase into a queryable knowledge graph."
        val cleaned = cleanTitle(raw, "https://github.com/Graphify-Labs/graphify")
        assertEquals("Graphify-Labs/graphify", cleaned)
    }

    @Test
    fun `github issue title preserves issue details and strips trailing github branding`() {
        val raw = "Fix memory leak in image decoder · Issue #123 · owner/repo · GitHub"
        val cleaned = cleanTitle(raw, "https://github.com/owner/repo/issues/123")
        assertEquals("Fix memory leak in image decoder · Issue #123 · owner/repo", cleaned)
    }

    @Test
    fun `github pull request title strips trailing github branding`() {
        val raw = "Add support for Gemini Nano by dev · Pull Request #456 · owner/repo · GitHub"
        val cleaned = cleanTitle(raw, "https://github.com/owner/repo/pull/456")
        assertEquals("Add support for Gemini Nano by dev · Pull Request #456 · owner/repo", cleaned)
    }

    @Test
    fun `gitlab repository title strips trailing gitlab branding`() {
        val raw = "GitLab.org / GitLab · GitLab"
        val cleaned = cleanTitle(raw, "https://gitlab.com/gitlab-org/gitlab")
        assertEquals("GitLab.org / GitLab", cleaned)
    }

    @Test
    fun `site suffix branding is stripped for hacker news, youtube, substack, medium`() {
        assertEquals(
            "Building AI-Powered Apps with Gemini Nano",
            cleanTitle("Building AI-Powered Apps with Gemini Nano | Hacker News", "https://news.ycombinator.com/item?id=123")
        )
        assertEquals(
            "Jetpack Compose in 2026",
            cleanTitle("Jetpack Compose in 2026 - YouTube", "https://youtube.com/watch?v=123")
        )
        assertEquals(
            "The Future of Modern Android",
            cleanTitle("The Future of Modern Android - Substack", "https://example.substack.com/p/future")
        )
        assertEquals(
            "Understanding Kotlin Coroutines",
            cleanTitle("Understanding Kotlin Coroutines | Medium", "https://medium.com/@dev/coroutines")
        )
    }

    @Test
    fun `standard article titles with colons are preserved`() {
        val raw = "Consensus at Scale: A Deep Dive"
        val cleaned = cleanTitle(raw, "https://example.com/consensus")
        assertEquals("Consensus at Scale: A Deep Dive", cleaned)
    }

    @Test
    fun `empty or blank title returns empty string`() {
        assertEquals("", cleanTitle("", "https://example.com"))
        assertEquals("", cleanTitle("   ", "https://example.com"))
    }
}
