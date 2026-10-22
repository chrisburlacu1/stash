package dev.cburlacu.stash.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import dev.cburlacu.stash.util.StashLog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM implementation of [OnDeviceEmbedder] running `EmbeddingGemma 2 (270M)`.
 *
 * Follows all LiteRT-LM runtime mandates:
 * - A single-thread executor owns the [EmbeddingEngine] lifecycle (`initialize`, `computeEmbedding`, `close`).
 * - The `.litertlm` model file lives in `context.filesDir` (never in `assets/`).
 * - Stores the [EmbeddingEngine] reference only after `initialize()` succeeds and closes it at most once.
 * - Prefers [Backend.GPU] and transparently falls back to [Backend.CPU] if OpenCL is unavailable.
 */
class LiteRtGemmaEmbedder(
    private val context: Context,
) : OnDeviceEmbedder {

    private val engineExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRtGemmaEmbedder").apply { isDaemon = true }
    }
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val engineMutex = Mutex()

    private var engine: EmbeddingEngine? = null
    private var activeBackend: Backend = Backend.GPU()
    private val downloadCancelled = AtomicBoolean(false)

    private val _modelState = MutableStateFlow<EmbeddingModelState>(
        if (resolveModelFile() != null) EmbeddingModelState.Initializing else EmbeddingModelState.NotDownloaded
    )
    override val modelState: StateFlow<EmbeddingModelState> = _modelState.asStateFlow()

    override fun isReady(): Boolean = _modelState.value is EmbeddingModelState.Ready && engine != null

    /**
     * Resolves the `.litertlm` model file on disk.
     * Checks both the canonical `embeddinggemma-2-text-270m.litertlm` filename and `model.litertlm`
     * (so `adb shell run-as dev.cburlacu.stash cp /data/local/tmp/model.litertlm files/` works out-of-the-box).
     */
    private fun resolveModelFile(): File? {
        val primary = File(context.filesDir, MODEL_FILE_NAME)
        if (primary.exists() && primary.length() >= MIN_VALID_MODEL_BYTES) return primary
        val adbFallback = File(context.filesDir, ADB_MODEL_FILE_NAME)
        if (adbFallback.exists() && adbFallback.length() >= MIN_VALID_MODEL_BYTES) return adbFallback
        return null
    }

    override suspend fun initializeIfPresent(): Boolean {
        val modelFile = resolveModelFile()
        if (modelFile == null) {
            _modelState.value = EmbeddingModelState.NotDownloaded
            return false
        }
        return loadEngine(modelFile.absolutePath)
    }

    private suspend fun loadEngine(modelPath: String): Boolean =
        withContext(engineScope.coroutineContext) {
            engineMutex.withLock {
                if (engine?.config?.modelPath == modelPath && engine?.isInitialized() == true) {
                    return@withLock true
                }
                releaseLocked()
                _modelState.value = EmbeddingModelState.Initializing

                // Try GPU backend first, falling back to CPU if GPU initialization fails.
                val loadedGpu = tryInitializeLocked(modelPath, Backend.GPU(), "EmbeddingGemma 2 · GPU")
                if (loadedGpu) return@withLock true

                val loadedCpu = tryInitializeLocked(modelPath, Backend.CPU(), "EmbeddingGemma 2 · CPU")
                if (!loadedCpu) {
                    _modelState.value = EmbeddingModelState.Error("Failed to initialize EmbeddingGemma 2")
                }
                loadedCpu
            }
        }

    private fun tryInitializeLocked(
        modelPath: String,
        backend: Backend,
        label: String,
    ): Boolean {
        return try {
            val config = EmbeddingEngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = context.cacheDir.path,
                maxInputLength = MAX_INPUT_TOKENS,
            )
            // Store engine only AFTER initialize() succeeds (never call close() if initialize() threw).
            val created = EmbeddingEngine(config).also { it.initialize() }
            engine = created
            activeBackend = backend
            _modelState.value = EmbeddingModelState.Ready(backendLabel = label)
            StashLog.d(TAG, "Initialized $label from $modelPath")
            true
        } catch (e: Throwable) {
            StashLog.w(TAG, "Could not initialize EmbeddingEngine with $label: ${e.message}")
            false
        }
    }

    override suspend fun embedDocument(text: String): FloatArray? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val formatted = if (cleaned.startsWith("title:", ignoreCase = true)) {
            cleaned.take(MAX_EMBED_TEXT_CHARS)
        } else {
            "title: none | text: ${cleaned.take(MAX_EMBED_TEXT_CHARS)}"
        }
        return computeNormalizedEmbedding(formatted)
    }

    override suspend fun embedQuery(query: String): FloatArray? {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return null
        val formatted = "task: search result | query: ${cleaned.take(MAX_QUERY_TEXT_CHARS)}"
        return computeNormalizedEmbedding(formatted)
    }

    private suspend fun computeNormalizedEmbedding(formattedInput: String): FloatArray? =
        withContext(engineScope.coroutineContext) {
            engineMutex.withLock {
                val currentEngine = engine ?: run {
                    val file = resolveModelFile() ?: return@withLock null
                    val ok = tryInitializeLocked(file.absolutePath, Backend.GPU(), "EmbeddingGemma 2 · GPU") ||
                        tryInitializeLocked(file.absolutePath, Backend.CPU(), "EmbeddingGemma 2 · CPU")
                    if (!ok) return@withLock null
                    engine ?: return@withLock null
                }

                computeWithSignatureRetryLocked(currentEngine, formattedInput)
            }
        }

    private fun computeWithSignatureRetryLocked(
        currentEngine: EmbeddingEngine,
        formattedInput: String,
    ): FloatArray? {
        val options = EmbeddingOptions(
            normalize = true,
            outputSize = EMBEDDING_DIM,
        )
        var candidateText = formattedInput
        repeat(4) {
            try {
                val response = currentEngine.computeEmbedding(
                    listOf(InputData.Text(candidateText)),
                    options,
                )
                return VectorMath.truncateAndNormalize(response.embedding, EMBEDDING_DIM)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                // If dense punctuation/code caused token count to exceed the 8,192-token TFLite signature,
                // trim the input text by 25% and retry on the SAME backend (do not drop off GPU!).
                if (msg.contains("exceeds maximum supported signature length", ignoreCase = true) ||
                    msg.contains("INVALID_ARGUMENT", ignoreCase = true)
                ) {
                    val newLen = (candidateText.length * 3) / 4
                    if (newLen < 256) {
                        StashLog.w(TAG, "computeEmbedding still exceeded signature at ${candidateText.length} chars")
                        return null
                    }
                    candidateText = candidateText.take(newLen)
                    return@repeat
                }

                // Only fall back from GPU to CPU on genuine hardware/delegate runtime failures.
                if (activeBackend is Backend.GPU) {
                    StashLog.w(TAG, "GPU delegate failed (${e.message}), falling back to CPU")
                    val modelPath = currentEngine.config.modelPath ?: return null
                    releaseLocked()
                    if (tryInitializeLocked(modelPath, Backend.CPU(), "EmbeddingGemma 2 · CPU")) {
                        return runCatching {
                            val cpuResponse = checkNotNull(engine).computeEmbedding(
                                listOf(InputData.Text(candidateText)),
                                options,
                            )
                            VectorMath.truncateAndNormalize(cpuResponse.embedding, EMBEDDING_DIM)
                        }.getOrNull()
                    }
                }
                StashLog.w(TAG, "computeEmbedding failed: ${e.message}")
                return null
            }
        }
        return null
    }

    override suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (_modelState.value is EmbeddingModelState.Downloading) return@withContext false

        // If the model file already exists on disk (e.g. previous load failed or pushed via adb),
        // try loading it directly before downloading 157 MB over the network.
        resolveModelFile()?.let { existing ->
            if (loadEngine(existing.absolutePath)) return@withContext true
        }

        downloadCancelled.set(false)

        val targetFile = File(context.filesDir, MODEL_FILE_NAME)
        val tempFile = File(context.filesDir, "$MODEL_FILE_NAME.part")
        var connection: HttpURLConnection? = null

        try {
            _modelState.value = EmbeddingModelState.Downloading(
                downloadedBytes = 0L,
                totalBytes = EXPECTED_MODEL_APPROX_BYTES,
            )

            val conn = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Stash/1.0 (Android; LiteRT-LM)")
            }
            connection = conn

            val code = conn.responseCode
            if (code !in 200..299) {
                _modelState.value = EmbeddingModelState.Error("Download failed (HTTP $code)")
                return@withContext false
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: EXPECTED_MODEL_APPROX_BYTES
            var downloaded = 0L
            var lastEmitMillis = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        if (downloadCancelled.get()) {
                            tempFile.delete()
                            resetStateAfterCancel()
                            return@withContext false
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMillis >= 100L || downloaded == totalBytes) {
                            lastEmitMillis = now
                            _modelState.value = EmbeddingModelState.Downloading(
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            if (tempFile.length() < MIN_VALID_MODEL_BYTES ||
                (conn.contentLengthLong > 0L && tempFile.length() != conn.contentLengthLong)
            ) {
                tempFile.delete()
                _modelState.value = EmbeddingModelState.Error("Downloaded model file was incomplete")
                return@withContext false
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            loadEngine(targetFile.absolutePath)
        } catch (e: CancellationException) {
            tempFile.delete()
            resetStateAfterCancel()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            StashLog.w(TAG, "Model download failed", e)
            _modelState.value = EmbeddingModelState.Error(e.message ?: "Download failed")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun resetStateAfterCancel() {
        _modelState.value = if (engine != null) {
            EmbeddingModelState.Ready("EmbeddingGemma 2")
        } else {
            EmbeddingModelState.NotDownloaded
        }
    }

    override fun cancelDownload() {
        downloadCancelled.set(true)
    }

    private fun releaseLocked() {
        val current = engine
        engine = null
        if (current != null) {
            runCatching { current.close() }
        }
    }

    companion object {
        private const val TAG = "LiteRtGemmaEmbedder"
        const val MODEL_FILE_NAME = "embeddinggemma-2-text-270m.litertlm"
        const val ADB_MODEL_FILE_NAME = "model.litertlm"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm"

        private const val EXPECTED_MODEL_APPROX_BYTES = 165L * 1024 * 1024
        private const val MIN_VALID_MODEL_BYTES = 10L * 1024 * 1024
        // Enable the full 8,192-token signature bundled in embeddinggemma-2-text-270m.litertlm.
        private const val MAX_INPUT_TOKENS = 8_192
        private const val MAX_EMBED_TEXT_CHARS = 24_000
        private const val MAX_QUERY_TEXT_CHARS = 512
    }
}
