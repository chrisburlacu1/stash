package dev.cburlacu.stash.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Matryoshka Representation Learning (MRL) target dimension for EmbeddingGemma 2.
 * 256 floats * 4 bytes = 1,024 bytes (1 KB) per saved item in SQLite.
 */
const val EMBEDDING_DIM = 256

/**
 * Minimum cosine similarity for a saved item to be considered a semantic match.
 */
const val MIN_SEMANTIC_SIMILARITY = 0.55f

/**
 * Lifecycle and download state of the on-device EmbeddingGemma 2 model.
 */
sealed interface EmbeddingModelState {
    data object NotDownloaded : EmbeddingModelState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : EmbeddingModelState {
        val progress: Float
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
    }
    data object Initializing : EmbeddingModelState
    data class Ready(val backendLabel: String) : EmbeddingModelState
    data class Error(val message: String) : EmbeddingModelState
}

/**
 * Contract for generating on-device semantic text embeddings via EmbeddingGemma 2.
 */
interface OnDeviceEmbedder {
    val modelState: StateFlow<EmbeddingModelState>

    fun isReady(): Boolean

    suspend fun initializeIfPresent(): Boolean

    suspend fun downloadModel(): Boolean

    fun cancelDownload()

    /**
     * Generates an L2-normalized [EMBEDDING_DIM]-float vector for a saved document,
     * applying the asymmetric indexing prefix (`"title: {title | none} | text: ..."`).
     */
    suspend fun embedDocument(text: String): FloatArray?

    /**
     * Generates an L2-normalized [EMBEDDING_DIM]-float vector for a user search query,
     * applying the asymmetric query prefix (`"task: search result | query: ..."`).
     */
    suspend fun embedQuery(query: String): FloatArray?
}

/**
 * No-op implementation used in unit tests or when no embedder is configured.
 */
object NoOpOnDeviceEmbedder : OnDeviceEmbedder {
    override val modelState: StateFlow<EmbeddingModelState> =
        MutableStateFlow(EmbeddingModelState.NotDownloaded)

    override fun isReady(): Boolean = false
    override suspend fun initializeIfPresent(): Boolean = false
    override suspend fun downloadModel(): Boolean = false
    override fun cancelDownload() = Unit
    override suspend fun embedDocument(text: String): FloatArray? = null
    override suspend fun embedQuery(query: String): FloatArray? = null
}

/**
 * Pure Kotlin vector utilities for Matryoshka dimension truncation, L2 normalization,
 * dot-product cosine similarity, and compact binary serialization for Room BLOB storage.
 */
object VectorMath {

    /**
     * Truncates a raw embedding vector (e.g. 768d) to the first [dim] floats (Matryoshka MRL)
     * and L2-normalizes it to unit length so cosine similarity reduces to a pure dot product.
     */
    fun truncateAndNormalize(raw: FloatArray, dim: Int = EMBEDDING_DIM): FloatArray {
        if (raw.isEmpty()) return FloatArray(0)
        val size = minOf(raw.size, dim)
        val out = FloatArray(size)
        var sumSquares = 0.0
        for (i in 0 until size) {
            val v = raw[i]
            out[i] = v
            sumSquares += v.toDouble() * v.toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm > 1e-12f) {
            for (i in 0 until size) {
                out[i] = out[i] / norm
            }
        }
        return out
    }

    /**
     * Computes cosine similarity between two L2-normalized vectors via dot product.
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        if (len == 0) return 0f
        var sum = 0f
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /**
     * Serializes a [FloatArray] into a Little-Endian [ByteArray] (`4 * size` bytes).
     */
    fun toByteArray(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    /**
     * Deserializes a Little-Endian [ByteArray] back into a [FloatArray].
     */
    fun fromByteArray(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % Float.SIZE_BYTES != 0) return FloatArray(0)
        val count = bytes.size / Float.SIZE_BYTES
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = buffer.getFloat()
        }
        return out
    }

    /**
     * Formats item metadata and full extracted article content into EmbeddingGemma 2's
     * official retrieval document format (`"title: {title | none} | text: {content}"`),
     * leveraging the 8,192-token context window.
     */
    fun buildDocumentEmbeddingText(
        title: String,
        headline: String,
        topic: String,
        tags: String,
        summary: String,
        content: String = "",
    ): String {
        val cleanTitle = title.trim().ifBlank { "none" }
        val body = buildString {
            if (headline.isNotBlank() && !headline.trim().equals(title.trim(), ignoreCase = true)) {
                appendLine(headline.trim())
            }
            if (topic.isNotBlank()) appendLine("Topic: ${topic.trim()}")
            if (tags.isNotBlank()) {
                val cleanTags = tags.split(" | ").map(String::trim).filter(String::isNotBlank)
                if (cleanTags.isNotEmpty()) {
                    appendLine("Tags: ${cleanTags.joinToString(", ")}")
                }
            }
            if (summary.isNotBlank()) {
                val cleanSummary = summary.lines()
                    .map { it.removePrefix("- ").trim() }
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                if (cleanSummary.isNotBlank()) {
                    appendLine(cleanSummary)
                }
            }
            if (content.isNotBlank()) {
                val cleanContent = content
                    .substringAfter("Article Body:")
                    .substringAfter("Post text:")
                    .trim()
                if (cleanContent.isNotBlank() && !cleanContent.startsWith("Saved URL:")) {
                    append(cleanContent)
                }
            }
        }.trim().ifBlank { cleanTitle }

        return "title: $cleanTitle | text: $body"
    }
}
