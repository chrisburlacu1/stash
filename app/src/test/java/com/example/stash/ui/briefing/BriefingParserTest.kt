package com.example.stash.ui.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures for [parseBriefingToNodes], written against how Gemini Nano actually replies rather than
 * how the prompt asks it to. The parser used to be a private function inside the screen, so none of
 * this was reachable — and every failure mode below is silent at runtime: content lands in the
 * wrong section, or vanishes, with no error anywhere.
 */
class BriefingParserTest {

    @Test
    fun `parses the exact format the prompt asks for`() {
        val nodes = parseBriefingToNodes(
            """
            **The Big Picture**
            Both tools target the same CLI automation problem from opposite ends.

            **Key Takeaways**
            * Tool A optimises for throughput.
            * Tool B optimises for configurability.

            **Comparisons & Trade-offs**
            * A is faster; B is more flexible.

            **The Bottom Line**
            Pick A for scale, B for control.
            """.trimIndent(),
        )

        assertEquals(4, nodes.size)
        assertTrue(nodes[0] is BriefingNode.BigPicture)
        assertTrue(nodes[1] is BriefingNode.KeyTakeaways)
        assertTrue(nodes[2] is BriefingNode.Comparison)
        assertTrue(nodes[3] is BriefingNode.BottomLine)
        assertTrue((nodes[0] as BriefingNode.BigPicture).lines.single().contains("opposite ends"))
    }

    @Test
    fun `handles markdown heading syntax instead of bold`() {
        val nodes = parseBriefingToNodes(
            """
            ## The Big Picture
            One theme.

            ## Key Takeaways
            - A point.

            ## The Bottom Line
            A verdict.
            """.trimIndent(),
        )

        assertEquals(3, nodes.size)
        assertTrue(nodes.any { it is BriefingNode.BigPicture })
        assertTrue(nodes.any { it is BriefingNode.BottomLine })
    }

    @Test
    fun `handles numbered headings the prompt never asked for`() {
        val nodes = parseBriefingToNodes(
            """
            1. The Big Picture
            A shared theme.

            2. Key Takeaways
            - A point.

            3. Comparisons & Trade-offs
            - A differs from B.

            4. The Bottom Line
            A verdict.
            """.trimIndent(),
        )

        assertEquals(4, nodes.size)
        assertEquals(
            listOf("- A differs from B."),
            (nodes.single { it is BriefingNode.Comparison } as BriefingNode.Comparison).lines,
        )
    }

    /**
     * The failure the old parser had by construction: it started in the Big Picture bucket, so any
     * chatty opener became the overview.
     */
    @Test
    fun `discards a conversational preamble instead of filing it as the overview`() {
        val nodes = parseBriefingToNodes(
            """
            Sure! Here is your executive briefing based on the saved items.

            **The Big Picture**
            The real overview.
            """.trimIndent(),
        )

        val bigPicture = nodes.single { it is BriefingNode.BigPicture } as BriefingNode.BigPicture
        assertEquals(listOf("The real overview."), bigPicture.lines)
    }

    /**
     * The other half of the same bug: a bullet mentioning a heading keyword is body text, not a
     * section break. Treating it as one dropped the bullet and misrouted everything after it.
     */
    @Test
    fun `a bullet mentioning trade-offs stays a bullet`() {
        val nodes = parseBriefingToNodes(
            """
            **Key Takeaways**
            * The trade-off is latency against accuracy.
            * Insight: caching helps more than batching.
            * Both make different trade-offs here.

            **The Bottom Line**
            Measure first.
            """.trimIndent(),
        )

        val takeaways = nodes.single { it is BriefingNode.KeyTakeaways } as BriefingNode.KeyTakeaways
        assertEquals("all three bullets should survive", 3, takeaways.lines.size)
        assertTrue("no comparison section was written", nodes.none { it is BriefingNode.Comparison })
    }

    @Test
    fun `single-source briefing with no comparison section yields three nodes`() {
        val nodes = parseBriefingToNodes(
            """
            **The Big Picture**
            One article about caching.

            **Key Takeaways**
            * Cache invalidation is the hard part.

            **The Bottom Line**
            Worth reading.
            """.trimIndent(),
        )

        assertEquals(3, nodes.size)
        assertTrue(nodes.none { it is BriefingNode.Comparison })
    }

    @Test
    fun `sections are ordered consistently regardless of the order the model emitted them`() {
        val nodes = parseBriefingToNodes(
            """
            **The Bottom Line**
            A verdict.

            **The Big Picture**
            A theme.
            """.trimIndent(),
        )

        assertTrue(nodes.first() is BriefingNode.BigPicture)
        assertTrue(nodes.last() is BriefingNode.BottomLine)
    }

    @Test
    fun `blank and whitespace-only input yields no nodes`() {
        assertTrue(parseBriefingToNodes("").isEmpty())
        assertTrue(parseBriefingToNodes("   \n\n  \n").isEmpty())
    }

    /**
     * A reply with no recognisable structure at all produces nothing rather than one giant
     * mislabelled overview. The ViewModel's blank-briefing fallback is what the user sees.
     */
    @Test
    fun `unstructured prose yields no nodes rather than a mislabelled section`() {
        val nodes = parseBriefingToNodes(
            "I could not find enough detail in these items to write a briefing.",
        )

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun `partial stream mid-generation parses what has arrived so far`() {
        // The screen re-parses on every chunk, so half a briefing must not throw or misfile.
        val nodes = parseBriefingToNodes(
            """
            **The Big Picture**
            Both tools target the same problem.

            **Key Ta
            """.trimIndent(),
        )

        assertEquals(1, nodes.size)
        assertTrue(nodes.single() is BriefingNode.BigPicture)
    }
}
