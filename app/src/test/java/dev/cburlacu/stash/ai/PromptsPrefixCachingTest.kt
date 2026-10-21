package dev.cburlacu.stash.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsPrefixCachingTest {

    @Test
    fun `schema prompt prefix remains invariant across different URLs, tags, and topics`() {
        val first = buildSchemaPrompt(
            url = "https://github.com/google/ksp",
            content = "Kotlin Symbol Processing API",
            knownTags = listOf("Kotlin", "Compiler"),
            knownTopics = listOf("Android"),
        )
        val second = buildSchemaPrompt(
            url = "https://developer.android.com/jetpack/compose",
            content = "Declarative UI toolkit",
            knownTags = listOf("Jetpack Compose", "UI", "Performance"),
            knownTopics = listOf("Android", "Design"),
        )

        assertEquals(first.prefix, second.prefix)
        assertTrue(first.suffix.contains("Kotlin, Compiler"))
        assertTrue(second.suffix.contains("Jetpack Compose, UI, Performance"))
    }

    @Test
    fun `rich prompt prefix remains invariant across different domains and library hints`() {
        val repoPrompt = buildRichPrompt(
            url = "https://github.com/owner/repo",
            content = "Repo readme",
            knownTags = listOf("CLI"),
            knownTopics = listOf("Tools"),
        )
        val articlePrompt = buildRichPrompt(
            url = "https://example.com/post",
            content = "Blog post body",
            knownTags = listOf("AI Agent", "Architecture"),
            knownTopics = listOf("AI", "Backend"),
        )

        assertEquals(repoPrompt.prefix, articlePrompt.prefix)
        assertTrue(repoPrompt.suffix.contains("\"Repo\""))
        assertTrue(articlePrompt.suffix.contains("Article, Documentation, Repo, Video, Discussion"))
    }

    @Test
    fun `chat prompt prefix remains identical across multi-turn conversation for the same item`() {
        val itemContext = "Title: Compose Performance\nExcerpt: Recomposition skipping rules..."
        val turn1 = buildChatPrompt(
            itemContext = itemContext,
            history = emptyList(),
            question = "What causes unnecessary recompositions?",
        )
        val turn2 = buildChatPrompt(
            itemContext = itemContext,
            history = listOf(
                ChatTurn(fromUser = true, text = "What causes unnecessary recompositions?"),
                ChatTurn(fromUser = false, text = "Unstable parameters such as standard List instances."),
            ),
            question = "How do I fix unstable lists?",
        )

        assertEquals(turn1.prefix, turn2.prefix)
        assertTrue(turn1.prefix.contains(itemContext))
        assertTrue(turn2.suffix.contains("Unstable parameters"))
        assertTrue(turn2.suffix.contains("How do I fix unstable lists?"))
    }

    @Test
    fun `briefing prompt shares cached prefix between initial briefing and follow-up chat questions`() {
        val itemsContext = "### Item 1: Tool A\n### Item 2: Tool B"
        val initialBriefing = buildBriefingPrompt(
            itemsContext = itemsContext,
            itemCount = 2,
            topic = "Tools",
            history = emptyList(),
            question = null,
        )
        val followUpTurn = buildBriefingPrompt(
            itemsContext = itemsContext,
            itemCount = 2,
            topic = "Tools",
            history = listOf(
                ChatTurn(fromUser = true, text = "Which one is faster?"),
                ChatTurn(fromUser = false, text = "Tool A is faster."),
            ),
            question = "Does Tool B support plugins?",
        )

        assertEquals(initialBriefing.prefix, followUpTurn.prefix)
        assertTrue(initialBriefing.prefix.contains(itemsContext))
        assertTrue(initialBriefing.suffix.contains("**Comparisons & Trade-offs**"))
        assertTrue(followUpTurn.suffix.contains("Does Tool B support plugins?"))
    }
}
