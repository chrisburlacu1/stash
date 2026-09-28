package dev.cburlacu.stash.data.local

import dev.cburlacu.stash.ai.AiAvailability
import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.ModelOption
import dev.cburlacu.stash.ai.OnDeviceSummarizer
import dev.cburlacu.stash.ai.OrganizedContent
import dev.cburlacu.stash.data.ModelChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitterExtractionTest {

    private val repository = RoomStashRepository(
        dao = FakeStashDaoForTwitter(),
        summarizer = FakeSummarizerForTwitter(),
    )

    @Test
    fun `upgradeTwitterAvatarUrl scales normal to 400x400`() {
        val original = "https://pbs.twimg.com/profile_images/1661201415899951105/azNjKOSH_normal.jpg"
        val upgraded = repository.upgradeTwitterAvatarUrl(original)
        assertEquals(
            "https://pbs.twimg.com/profile_images/1661201415899951105/azNjKOSH_400x400.jpg",
            upgraded,
        )
    }

    @Test
    fun `upgradeTwitterAvatarUrl scales mini and bigger`() {
        val mini = "https://pbs.twimg.com/profile_images/123/avatar_mini.png"
        val bigger = "https://pbs.twimg.com/profile_images/123/avatar_bigger.jpeg"

        assertEquals(
            "https://pbs.twimg.com/profile_images/123/avatar_400x400.png",
            repository.upgradeTwitterAvatarUrl(mini),
        )
        assertEquals(
            "https://pbs.twimg.com/profile_images/123/avatar_400x400.jpeg",
            repository.upgradeTwitterAvatarUrl(bigger),
        )
    }

    @Test
    fun `upgradeTwitterAvatarUrl scales 200x200 to 400x400`() {
        val avatar200 = "https://pbs.twimg.com/profile_images/2083983655387742208/J6MwCk4b_200x200.jpg"
        assertEquals(
            "https://pbs.twimg.com/profile_images/2083983655387742208/J6MwCk4b_400x400.jpg",
            repository.upgradeTwitterAvatarUrl(avatar200),
        )
    }

    @Test
    fun `upgradeTwitterPhotoUrl adds name=large when no query param exists`() {
        val plain = "https://pbs.twimg.com/media/diagram.jpg"
        assertEquals(
            "https://pbs.twimg.com/media/diagram.jpg?name=large",
            repository.upgradeTwitterPhotoUrl(plain),
        )
    }

    @Test
    fun `upgradeTwitterPhotoUrl preserves name=orig and upgrades name=small`() {
        val orig = "https://pbs.twimg.com/media/diagram.jpg?name=orig"
        val small = "https://pbs.twimg.com/media/diagram.jpg?name=small"

        assertEquals(
            "https://pbs.twimg.com/media/diagram.jpg?name=orig",
            repository.upgradeTwitterPhotoUrl(orig),
        )
        assertEquals(
            "https://pbs.twimg.com/media/diagram.jpg?name=large",
            repository.upgradeTwitterPhotoUrl(small),
        )
    }

    @Test
    fun `extractTweet returns null for non-twitter urls`() {
        assertNull(repository.extractTweet("https://github.com/android/nowinandroid"))
        assertNull(repository.extractTweet("https://news.ycombinator.com/item?id=123"))
        assertNull(repository.extractTweet("https://example.com"))
    }

    @Test
    fun `parseFxTwitterJson extracts photo when tweet has media`() {
        val json = """
            {
                "code": 200,
                "tweet": {
                    "text": "Check out our new architecture diagram!",
                    "author": {
                        "name": "Android Devs",
                        "avatar_url": "https://pbs.twimg.com/profile_images/1/avatar_normal.jpg"
                    },
                    "media": {
                        "photos": [
                            { "url": "https://pbs.twimg.com/media/diagram.jpg" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val extraction = repository.parseFxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals("https://pbs.twimg.com/media/diagram.jpg?name=large", extraction?.imageUrl)
        assertTrue(extraction?.text?.contains("Check out our new architecture diagram!") == true)
        assertTrue(extraction?.text?.contains("Android Devs") == true)
    }

    @Test
    fun `parseFxTwitterJson extracts video thumbnail when tweet has video`() {
        val json = """
            {
                "code": 200,
                "tweet": {
                    "text": "Check out this 3D demo!",
                    "author": {
                        "name": "Bee",
                        "avatar_url": "https://pbs.twimg.com/profile_images/1/avatar_200x200.jpg"
                    },
                    "media": {
                        "all": [
                            {
                                "type": "video",
                                "url": "https://video.twimg.com/amplify_video/123/vid/avc1/1920x1080/sample.mp4",
                                "thumbnail_url": "https://pbs.twimg.com/amplify_video_thumb/123/img/highres.jpg"
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val extraction = repository.parseFxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals("https://pbs.twimg.com/amplify_video_thumb/123/img/highres.jpg", extraction?.imageUrl)
        assertTrue(extraction?.text?.contains("Check out this 3D demo!") == true)
    }

    @Test
    fun `parseFxTwitterJson extracts and upgrades avatar for text-only tweet`() {
        val json = """
            {
                "code": 200,
                "tweet": {
                    "text": "just setting up my twttr",
                    "author": {
                        "name": "jack",
                        "avatar_url": "https://pbs.twimg.com/profile_images/1661201415899951105/azNjKOSH_normal.jpg"
                    }
                }
            }
        """.trimIndent()

        val extraction = repository.parseFxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals(
            "https://pbs.twimg.com/profile_images/1661201415899951105/azNjKOSH_400x400.jpg",
            extraction?.imageUrl,
        )
        assertTrue(extraction?.text?.contains("just setting up my twttr") == true)
        assertTrue(extraction?.text?.contains("jack") == true)
    }

    @Test
    fun `parseFxTwitterJson parses user profile response`() {
        val json = """
            {
                "code": 200,
                "user": {
                    "name": "Andrej Karpathy",
                    "screen_name": "karpathy",
                    "description": "Building Eureka Labs.",
                    "avatar_url": "https://pbs.twimg.com/profile_images/999/karpathy_normal.jpg"
                }
            }
        """.trimIndent()

        val extraction = repository.parseFxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals(
            "https://pbs.twimg.com/profile_images/999/karpathy_400x400.jpg",
            extraction?.imageUrl,
        )
        assertTrue(extraction?.text?.contains("Andrej Karpathy") == true)
        assertTrue(extraction?.text?.contains("@karpathy") == true)
        assertTrue(extraction?.text?.contains("Building Eureka Labs.") == true)
    }

    @Test
    fun `parseFxTwitterJson extracts profile banner when present`() {
        val json = """
            {
                "code": 200,
                "user": {
                    "name": "Bee",
                    "screen_name": "thtbee_",
                    "description": "A developer",
                    "banner_url": "https://pbs.twimg.com/profile_banners/123/banner.jpg",
                    "avatar_url": "https://pbs.twimg.com/profile_images/1/avatar_200x200.jpg"
                }
            }
        """.trimIndent()

        val extraction = repository.parseFxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals("https://pbs.twimg.com/profile_banners/123/banner.jpg", extraction?.imageUrl)
    }

    @Test
    fun `parseVxTwitterJson extracts photo and author`() {
        val json = """
            {
                "text": "Hello world from vxTwitter",
                "user_name": "OpenAI",
                "user_screen_name": "OpenAI",
                "user_profile_image_url": "https://pbs.twimg.com/profile_images/2/openai_normal.jpg",
                "mediaURLs": ["https://pbs.twimg.com/media/gpt.png"]
            }
        """.trimIndent()

        val extraction = repository.parseVxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals("https://pbs.twimg.com/media/gpt.png?name=large", extraction?.imageUrl)
        assertTrue(extraction?.text?.contains("Hello world from vxTwitter") == true)
        assertTrue(extraction?.text?.contains("OpenAI") == true)
    }

    @Test
    fun `parseVxTwitterJson extracts video thumbnail when mediaURL is video`() {
        val json = """
            {
                "text": "Cool video demo",
                "user_name": "Tech",
                "user_profile_image_url": "https://pbs.twimg.com/profile_images/2/avatar_normal.jpg",
                "mediaURLs": ["https://video.twimg.com/amplify_video/1/vid/avc1/1920x1080/demo.mp4"],
                "media_extended": [
                    {
                        "type": "video",
                        "thumbnail_url": "https://pbs.twimg.com/amplify_video_thumb/1/img/video_thumb.jpg",
                        "url": "https://video.twimg.com/amplify_video/1/vid/avc1/1920x1080/demo.mp4"
                    }
                ]
            }
        """.trimIndent()

        val extraction = repository.parseVxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals("https://pbs.twimg.com/amplify_video_thumb/1/img/video_thumb.jpg", extraction?.imageUrl)
    }

    @Test
    fun `parseVxTwitterJson upgrades avatar for text tweet without media`() {
        val json = """
            {
                "text": "Thoughts on LLM latency",
                "user_name": "Dev",
                "user_profile_image_url": "https://pbs.twimg.com/profile_images/3/dev_normal.jpg",
                "mediaURLs": []
            }
        """.trimIndent()

        val extraction = repository.parseVxTwitterJson(json)
        assertNotNull(extraction)
        assertEquals(
            "https://pbs.twimg.com/profile_images/3/dev_400x400.jpg",
            extraction?.imageUrl,
        )
    }
}

private class FakeStashDaoForTwitter : StashDao {
    override fun observeAll(): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeTag(tag: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeItem(id: String): Flow<StashEntity?> = flowOf(null)
    override fun observeItems(ids: List<String>): Flow<List<StashEntity>> = flowOf(emptyList())
    override suspend fun imageFileFor(id: String): String? = null
    override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTags(): List<String> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun rowsMissingSeed(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsWithImage(): List<SeedBackfillRow> = emptyList()
    override suspend fun rowsForTagBackfill(): List<TagBackfillRow> = emptyList()
    override suspend fun rowsMissingTwitterImage(): List<TwitterImageBackfillRow> = emptyList()
    override suspend fun allTwitterRows(): List<TwitterImageBackfillRow> = emptyList()
    override fun observeTopic(topic: String): Flow<List<StashListRow>> = flowOf(emptyList())
    override fun observeAllTopics(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun allTopics(): List<String> = emptyList()
    override suspend fun rowsForTopicBackfill(): List<TopicBackfillRow> = emptyList()
    override suspend fun updateItemTopic(id: String, topic: String) = Unit
    override suspend fun updateSearchTopic(id: String, topic: String) = Unit
    override suspend fun setImageData(id: String, imageFile: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun setSeedAndCrop(id: String, seedColor: Int, cropBias: Float) = Unit
    override suspend fun updateItemTags(id: String, tags: String) = Unit
    override suspend fun updateSearchTags(id: String, tags: String) = Unit
    override suspend fun setRead(id: String, isRead: Boolean) = Unit
    override suspend fun deleteItem(id: String) = Unit
    override suspend fun upsertItem(item: StashEntity) = Unit
    override suspend fun upsertSearch(item: StashSearchEntity) = Unit
    override suspend fun deleteSearch(id: String) = Unit
}

private class FakeSummarizerForTwitter : OnDeviceSummarizer {
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
