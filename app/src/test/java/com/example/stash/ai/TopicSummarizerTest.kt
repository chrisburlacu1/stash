package com.example.stash.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicSummarizerTest {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Test
    fun `organized response decodes topic successfully`() {
        val json = """
            {
                "title": "Mastering Jetpack Compose",
                "takeaway": "Declarative UI for modern Android apps",
                "keyPoints": [
                    "Simplifies state management",
                    "Supports Kotlin Multiplatform",
                    "Reduces boilerplate code"
                ],
                "category": "Article",
                "topic": "Android",
                "tags": ["Compose", "UI Toolkit", "Kotlin"]
            }
        """.trimIndent()

        val response = jsonParser.decodeFromString<OrganizedResponse>(json)

        assertEquals("Mastering Jetpack Compose", response.title)
        assertEquals("Android", response.topic)
        assertEquals("Article", response.category)
        assertEquals(3, response.tags.size)
        assertEquals("Compose", response.tags[0])
    }

    @Test
    fun `organized response handles missing topic gracefully with default`() {
        val json = """
            {
                "title": "A Great Post",
                "takeaway": "Insightful read",
                "keyPoints": ["One", "Two", "Three"],
                "category": "Article",
                "tags": ["Tech"]
            }
        """.trimIndent()

        val response = jsonParser.decodeFromString<OrganizedResponse>(json)

        assertEquals("", response.topic)
    }
}
