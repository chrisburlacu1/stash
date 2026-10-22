package dev.cburlacu.stash.ai

import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.local.EmbeddingBackfillRow
import dev.cburlacu.stash.data.local.RoomStashRepository
import dev.cburlacu.stash.data.local.SeedBackfillRow
import dev.cburlacu.stash.data.local.StashDao
import dev.cburlacu.stash.data.local.StashEmbeddingEntity
import dev.cburlacu.stash.data.local.StashEntity
import dev.cburlacu.stash.data.local.StashListRow
import dev.cburlacu.stash.data.local.StashSearchEntity
import dev.cburlacu.stash.data.local.TagBackfillRow
import dev.cburlacu.stash.data.local.TopicBackfillRow
import dev.cburlacu.stash.data.local.TwitterImageBackfillRow
import dev.cburlacu.stash.data.local.resolveCompleteHeadline
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchTest {

    @Test
    fun `truncateAndNormalize slices 768d vector to 256d and L2-normalizes to unit length`() {
        val raw = FloatArray(768) { index -> (index + 1).toFloat() }
        val normalized = VectorMath.truncateAndNormalize(raw, EMBEDDING_DIM)

        assertEquals(EMBEDDING_DIM, normalized.size)
        val norm = sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1.0f, norm, 1e-5f)
    }

    @Test
    fun `dotProduct computes exact cosine similarity for normalized vectors`() {
        val a = VectorMath.truncateAndNormalize(floatArrayOf(3f, 4f, 0f), dim = 3)
        val b = VectorMath.truncateAndNormalize(floatArrayOf(6f, 8f, 0f), dim = 3)
        val orthogonal = VectorMath.truncateAndNormalize(floatArrayOf(0f, 0f, 5f), dim = 3)
        val opposite = VectorMath.truncateAndNormalize(floatArrayOf(-3f, -4f, 0f), dim = 3)

        assertEquals(1.0f, VectorMath.dotProduct(a, b), 1e-5f)
        assertEquals(0.0f, VectorMath.dotProduct(a, orthogonal), 1e-5f)
        assertEquals(-1.0f, VectorMath.dotProduct(a, opposite), 1e-5f)
    }

    @Test
    fun `toByteArray and fromByteArray round-trip 256d vector into 1024-byte BLOB`() {
        val original = VectorMath.truncateAndNormalize(
            FloatArray(EMBEDDING_DIM) { idx -> (idx - 128) * 0.125f }
        )
        val bytes = VectorMath.toByteArray(original)

        assertEquals(EMBEDDING_DIM * Float.SIZE_BYTES, bytes.size)
        val restored = VectorMath.fromByteArray(bytes)
        assertArrayEquals(original, restored, 1e-6f)
    }

    @Test
    fun `buildDocumentEmbeddingText uses official EmbeddingGemma title and text format with full content`() {
        val formatted = VectorMath.buildDocumentEmbeddingText(
            title = "Gemini AI Visual Design",
            headline = "Designers use gradients, motion, and shapes",
            topic = "UI Design",
            tags = "UI Design | Gradient | Motion",
            summary = "- Motion builds trust\n- Smooth gradients",
            content = "Title: Gemini AI Visual Design\nArticle Body: Deep dive into choreography and spring physics.",
        )

        assertTrue(formatted.startsWith("title: Gemini AI Visual Design | text: "))
        assertTrue(formatted.contains("Designers use gradients, motion, and shapes"))
        assertTrue(formatted.contains("Tags: UI Design, Gradient, Motion"))
        assertTrue(formatted.contains("Deep dive into choreography and spring physics."))
        assertFalse(
            "Header before 'Article Body:' must be stripped and not restored by 'Post text:' fallback",
            formatted.contains("Article Body:"),
        )
    }

    @Test
    fun `buildDocumentEmbeddingText strips Post text prefix for social posts`() {
        val formatted = VectorMath.buildDocumentEmbeddingText(
            title = "Tweet by @googledevs",
            headline = "EmbeddingGemma 2 launched for Android",
            topic = "AI",
            tags = "LiteRT | Android",
            summary = "- Runs on GPU",
            content = "Author: @googledevs\nPost text: EmbeddingGemma 2 brings 8K context to mobile.",
        )

        assertTrue(formatted.contains("EmbeddingGemma 2 brings 8K context to mobile."))
        assertFalse(
            "Header before 'Post text:' must be stripped",
            formatted.contains("Post text:"),
        )
        assertFalse(formatted.contains("Author: @googledevs"))
    }

    @Test
    fun `resolveCompleteHeadline repairs 90-char truncated headlines using full summary bullet`() {
        val truncated = "Designers use gradients, motion, and shapes to build trust in an evolving"
        val summary = "- Designers use gradients, motion, and shapes to build trust in an evolving AI interface.\n- Second bullet"

        assertEquals(
            "Designers use gradients, motion, and shapes to build trust in an evolving AI interface.",
            resolveCompleteHeadline(truncated, summary),
        )

        val complete = "EmbeddingGemma 2 runs on-device with 256d Matryoshka vectors"
        assertEquals(
            complete,
            resolveCompleteHeadline(complete, "- Unrelated bullet"),
        )
    }

    @Test
    fun `toFtsQuery strips question stop words and possessives so natural questions match FTS5`() {
        val repository = RoomStashRepository(
            dao = FakeSemanticDao(),
            summarizer = FakeSemanticSummarizer(),
        )

        assertEquals(
            "\"Gemini\"* AND \"motion\"* AND \"design\"*",
            repository.toFtsQuery("What is Gemini's motion design?"),
        )
        assertEquals(
            "\"gemini\"* AND \"motion\"* AND \"design\"*",
            repository.toFtsQuery("whats's gemini's motion design?"),
        )
    }

    @Test
    fun `isPlausibleSemanticQuery rejects keyboard mash gibberish`() {
        val repository = RoomStashRepository(
            dao = FakeSemanticDao(),
            summarizer = FakeSemanticSummarizer(),
        )

        assertFalse(repository.isPlausibleSemanticQuery(repository.extractSearchTokens("asdfghjkl")))
        assertFalse(repository.isPlausibleSemanticQuery(repository.extractSearchTokens("qwertzuiop")))
        assertFalse(repository.isPlausibleSemanticQuery(repository.extractSearchTokens("zzzzzz")))
        assertTrue(repository.isPlausibleSemanticQuery(repository.extractSearchTokens("gemini motion design")))
        assertTrue(repository.isPlausibleSemanticQuery(repository.extractSearchTokens("webgpu llm")))
    }

    @Test
    fun `rankHybridSearchResults keeps exact FTS matches on top and rejects partial-entity noise`() {
        val repository = RoomStashRepository(
            dao = FakeSemanticDao(),
            summarizer = FakeSemanticSummarizer(),
        )

        val motionDesignItem = testItem(
            id = "gemini-motion",
            title = "Gemini AI Visual Design",
            summary = "Designers use gradients, motion, and shapes to build trust",
        )
        val unrelatedGeminiModelItem = testItem(
            id = "gemini-model",
            title = "Bringing our latest frontier model to Android Developers",
            summary = "Gemini 4 Argon brings advanced agentic capabilities",
        )
        val conceptualSibling = testItem(
            id = "semantic-sibling",
            title = "Fluid AI Transitions and Shader Choreography",
            summary = "Gemini motion design principles for expressive surfaces",
        )
        val composeItem = testItem(
            id = "compose-unrelated",
            title = "Jetpack Compose LazyColumn Guide",
            summary = "List state and keys",
        )

        val results = repository.rankHybridSearchResults(
            ftsItems = listOf(motionDesignItem),
            allItems = listOf(motionDesignItem, unrelatedGeminiModelItem, conceptualSibling, composeItem),
            similarityById = mapOf(
                "gemini-motion" to 0.78f,
                "semantic-sibling" to 0.77f,
                "gemini-model" to 0.61f,
                "compose-unrelated" to 0.48f,
            ),
            queryTokens = listOf("gemini", "motion", "design"),
        )

        assertEquals(listOf("gemini-motion", "semantic-sibling"), results.items.map { it.id })
        assertFalse("Exact FTS match should not be flagged as semantic-only", "gemini-motion" in results.semanticMatchIds)
        assertTrue("Strong sibling match should be flagged in semanticMatchIds", "semantic-sibling" in results.semanticMatchIds)
        assertFalse("Unrelated article that only shares 'Gemini' should be excluded", "gemini-model" in results.items.map { it.id })
        assertFalse("Unrelated Compose article should be excluded", "compose-unrelated" in results.items.map { it.id })
    }

    private fun testItem(id: String, title: String, summary: String = "Key point") = StashItem(
        id = id,
        url = "https://example.com/$id",
        title = title,
        domain = "example.com",
        category = "Article",
        headline = summary,
        summary = summary,
        tags = listOf("Android"),
        readTime = "3 min",
        savedAtEpochMillis = 1000L,
        aiState = AiState.Ready,
    )
}

private class FakeSemanticDao : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTopic(topic: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(null)
    override fun observeItems(ids: List<String>): Flow<List<StashEntity>> = flowOf(emptyList())
    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override fun observeAllTopics(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTopics(): List<String> = emptyList()
    override fun observeTotalCount(): Flow<Int> = flowOf(0)
    override fun observeEmbeddingCount(): Flow<Int> = flowOf(0)
    override suspend fun allEmbeddings(): List<StashEmbeddingEntity> = emptyList()
    override suspend fun rowsMissingEmbeddings(): List<EmbeddingBackfillRow> = emptyList()
    override suspend fun upsertEmbedding(item: StashEmbeddingEntity) = Unit
    override suspend fun deleteEmbedding(id: String) = Unit
    override suspend fun count(): Int = 0
    override suspend fun rowsWithImage(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsForTagBackfill(): List<TagBackfillRow> = emptyList()
    override suspend fun rowsForTopicBackfill(): List<TopicBackfillRow> = emptyList()
    override suspend fun allTwitterRows(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun setImageData(id: String, imageFile: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun setSeedAndCrop(id: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun updateItemTags(id: String, tags: String) = Unit
    override suspend fun updateSearchTags(id: String, tags: String) = Unit
    override suspend fun updateItemTopic(id: String, topic: String) = Unit
    override suspend fun updateSearchTopic(id: String, topic: String) = Unit
    override suspend fun setRead(id: String, isRead: Boolean) = Unit
    override suspend fun deleteItem(id: String) = Unit
    override suspend fun upsertItem(item: StashEntity) = Unit
    override suspend fun upsertSearch(item: StashSearchEntity) = Unit
    override suspend fun deleteSearch(id: String) = Unit
}

private class FakeSemanticSummarizer : OnDeviceSummarizer {
    override suspend fun availability(): AiAvailability = AiAvailability.Unavailable
    override suspend fun organize(
        url: String,
        content: String,
        contentChars: Int,
        knownTags: List<String>,
        knownTopics: List<String>,
    ): OrganizedContent? = null
    override suspend fun getModelVersion(): String = "fake"
    override suspend fun inferTopic(title: String, summary: String, tags: String, knownTopics: List<String>): String? = null
    override suspend fun probeModels(): List<ModelOption> = emptyList()
    override suspend fun selectModel(choice: ModelChoice) = Unit
    override fun chatStream(itemContext: String, history: List<ChatTurn>, question: String): Flow<String> = flowOf()
    override fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = flowOf()
}
