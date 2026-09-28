package dev.cburlacu.stash.ai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.cburlacu.stash.data.ModelChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

sealed interface AiAvailability {
    data object Available : AiAvailability
    data object Unavailable : AiAvailability
}

enum class ModelStatus {
    Ready,
    Downloadable,
    Downloading,
    Unavailable,
}

data class ModelOption(val choice: ModelChoice, val status: ModelStatus)

data class ChatTurn(val fromUser: Boolean, val text: String)

interface OnDeviceSummarizer {
    suspend fun availability(): AiAvailability
    suspend fun organize(
        url: String,
        content: String,
        contentChars: Int = 4_000,
        knownTags: List<String> = emptyList(),
        knownTopics: List<String> = emptyList(),
    ): OrganizedContent?

    /**
     * Lightweight topic classification for backfill. Given an item's title, summary,
     * and tags, returns a 1-2 word high-level topic domain (e.g. "Android", "Design").
     * Returns null if AI is unavailable or inference fails.
     */
    suspend fun inferTopic(
        title: String,
        summary: String,
        tags: String,
        knownTopics: List<String> = emptyList(),
    ): String?

    suspend fun getModelVersion(): String
    suspend fun probeModels(): List<ModelOption>
    suspend fun selectModel(choice: ModelChoice)
    fun chatStream(itemContext: String, history: List<ChatTurn>, question: String): Flow<String>
    fun briefingStream(
        itemsContext: String,
        itemCount: Int,
        topic: String? = null,
        history: List<ChatTurn> = emptyList(),
        question: String? = null,
    ): Flow<String>
}

data class OrganizedContent(
    val title: String,
    val headline: String,
    val summary: String,
    val category: String,
    val tags: List<String>,
    val topic: String = "",
)

@Serializable
@Generable(description = "Metadata extracted from a saved link so it can be rediscovered later")
data class OrganizedResponse(
    @Guide(description = "The real title of the page or post")
    val title: String,
    @Guide(
        description = "Max 12 words on why this is worth remembering. " +
            "No trailing period. Must not repeat the title.",
    )
    val takeaway: String = "",
    @Guide(
        description = "Short bullets of the actual substance: specifics, names, numbers, " +
            "conclusions. No filler such as 'the article explains the details'.",
        minItems = 3,
        maxItems = 5,
    )
    val keyPoints: List<String> = emptyList(),
    @Guide(
        description = "The content type of the link",
        enumValues = [
            "Article", "Documentation", "Repo", "Video", "Discussion",
        ],
    )
    val category: String = "",
    @Guide(
        description = "1 high-level topic domain representing the subject (e.g. 'Android', 'Design', 'React', 'AI', 'Tools', 'Productivity', 'Finance', 'General'). Max 2 words."
    )
    val topic: String = "",
    @Guide(
        description = "2 to 4 concise, canonical tags describing the core subject, technologies, tools, and specific concepts discussed in the content (1 to 3 words per tag). " +
            "Ground tags directly in the page content (e.g. use 'AI Agent', 'CLI', 'Developer Tool' for terminal agent tools; do not invent generic umbrella tags like 'Machine Learning' unless the content specifically discusses ML models or training). " +
            "Always use singular nouns (e.g. 'Screenplay' not 'Screenplays', 'Recipe' not 'Recipes', 'Agent' not 'Agents'). " +
            "Do not include format words (e.g. 'Podcast', 'Audio', 'Episode', 'Article', 'Video', 'Post', 'Website', 'Newsletter').",
        minItems = 2,
        maxItems = 4,
    )
    val tags: List<String> = emptyList(),
)
