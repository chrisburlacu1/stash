package dev.cburlacu.stash.data.local

import androidx.annotation.VisibleForTesting
import dev.cburlacu.stash.ai.AiAvailability
import dev.cburlacu.stash.ai.ChatTurn
import dev.cburlacu.stash.ai.EmbeddingModelState
import dev.cburlacu.stash.ai.LibrarianAgent
import dev.cburlacu.stash.ai.MIN_SEMANTIC_SIMILARITY
import dev.cburlacu.stash.ai.NoOpOnDeviceEmbedder
import dev.cburlacu.stash.ai.OnDeviceEmbedder
import dev.cburlacu.stash.ai.OnDeviceSummarizer
import dev.cburlacu.stash.ai.VectorMath
import dev.cburlacu.stash.ai.categoryForDomain
import dev.cburlacu.stash.data.ModelChoice
import dev.cburlacu.stash.data.SearchResults
import dev.cburlacu.stash.data.SortOrder
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.TagCount
import dev.cburlacu.stash.data.TopicCount
import dev.cburlacu.stash.data.extract.Extraction
import dev.cburlacu.stash.data.extract.TwitterExtractor
import dev.cburlacu.stash.data.extract.WebPageExtractor
import dev.cburlacu.stash.data.extract.WebPageExtractor.articleText
import dev.cburlacu.stash.data.extract.deriveFallbackTitle
import dev.cburlacu.stash.data.extract.extractFirstUrl
import dev.cburlacu.stash.data.image.ImageAnalyzer
import dev.cburlacu.stash.data.image.ImageFallbackRenderer
import dev.cburlacu.stash.data.prompt.PromptContextBuilders
import dev.cburlacu.stash.models.AiState
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.components.ImageBitmapCache
import dev.cburlacu.stash.util.StashLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

class RoomStashRepository(
    private val dao: StashDao,
    private val summarizer: OnDeviceSummarizer,
    private val imageDir: File? = null,
    private val embedder: OnDeviceEmbedder = NoOpOnDeviceEmbedder,
) : StashRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backfillMutex = Mutex()

    override val embeddingModelState: StateFlow<EmbeddingModelState>
        get() = embedder.modelState

    override fun observeIndexedCount(): Flow<Pair<Int, Int>> = combine(
        dao.observeEmbeddingCount().catch { emit(0) },
        dao.observeTotalCount().catch { emit(0) },
    ) { indexed, total ->
        indexed.coerceAtMost(total) to total
    }

    override suspend fun downloadEmbeddingModel(): Boolean =
        withContext(repoScope.coroutineContext) {
            val ok = embedder.downloadModel()
            if (ok) {
                backfillEmbeddings()
            }
            ok
        }

    override fun cancelEmbeddingDownload() {
        embedder.cancelDownload()
    }

    override fun observe(
        query: String,
        tags: Set<String>,
        sortOrder: SortOrder,
        topic: String?,
    ): Flow<List<StashItem>> {
        val ftsQuery = toFtsQuery(query)
        val source = when {
            ftsQuery.isNotBlank() -> dao.search(ftsQuery, null)
            topic != null -> dao.observeTopic(topic)
            else -> dao.observeAll()
        }
        return source
            .catch { emit(emptyList()) }
            .map { rows ->
                val items = rows.distinctBy(StashListRow::id)
                    .map { it.toModel(imageDir) }
                    .filter { item -> tags.isEmpty() || item.tags.containsAll(tags) }
                    .let { list ->
                        // Apply topic filter in-memory when combined with FTS search
                        if (topic != null && ftsQuery.isNotBlank()) {
                            list.filter { it.topic.equals(topic, ignoreCase = true) }
                        } else list
                    }

                if (ftsQuery.isNotBlank()) {
                    items
                } else {
                    when (sortOrder) {
                        SortOrder.Newest -> items.sortedByDescending { it.savedAtEpochMillis }
                        SortOrder.Oldest -> items.sortedBy { it.savedAtEpochMillis }
                        SortOrder.UnreadFirst -> items.sortedWith(
                            compareBy<StashItem> { it.isRead }.thenByDescending { it.savedAtEpochMillis }
                        )
                    }
                }
            }
    }

    override fun search(query: String): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(SearchResults())

        val queryTokens = extractSearchTokens(trimmed)
        val plausibleSemantic = isPlausibleSemanticQuery(queryTokens)
        val ftsQuery = toFtsQuery(trimmed)
        val ftsFlow = if (ftsQuery.isNotBlank()) {
            dao.search(ftsQuery, null).catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }

        val semanticQueryText = if (queryTokens.isNotEmpty()) {
            queryTokens.joinToString(" ")
        } else {
            trimmed
        }

        return flow {
            // Compute the query embedding once per search query (rather than on every DB emission).
            val cachedQueryVec = if (embedder.isReady() && trimmed.length >= 2 && plausibleSemantic) {
                embedder.embedQuery(semanticQueryText)
            } else {
                null
            }

            emitAll(
                combine(
                    ftsFlow,
                    dao.observeAll().catch { emit(emptyList()) },
                ) { ftsRows, allRows ->
                    val ftsItems = ftsRows.distinctBy(StashListRow::id).map { it.toModel(imageDir) }
                    if (!embedder.isReady() || trimmed.length < 2) {
                        return@combine SearchResults(items = ftsItems, semanticMatchIds = emptySet())
                    }

                    if (ftsItems.isEmpty() && !plausibleSemantic) {
                        return@combine SearchResults(items = emptyList(), semanticMatchIds = emptySet())
                    }

                    val queryVec = cachedQueryVec ?: embedder.embedQuery(semanticQueryText)
                    if (queryVec == null || queryVec.isEmpty()) {
                        return@combine SearchResults(items = ftsItems, semanticMatchIds = emptySet())
                    }

                    val storedEmbeddings = runCatching { dao.allEmbeddings() }.getOrNull().orEmpty()
                    if (storedEmbeddings.isEmpty()) {
                        return@combine SearchResults(items = ftsItems, semanticMatchIds = emptySet())
                    }

                    val similarityById = HashMap<String, Float>(storedEmbeddings.size)
                    for (entity in storedEmbeddings) {
                        val itemVec = VectorMath.fromByteArray(entity.vector)
                        if (itemVec.isNotEmpty()) {
                            similarityById[entity.id] = VectorMath.dotProduct(queryVec, itemVec)
                        }
                    }

                    // Convert rows without hitting File.exists() on the entire library;
                    // resolve imagePath only for the final ranked results.
                    val allItems = allRows.distinctBy(StashListRow::id).map { it.toModel(imageDir = null) }
                    val ranked = rankHybridSearchResults(
                        ftsItems = ftsItems,
                        allItems = allItems,
                        similarityById = similarityById,
                        queryTokens = queryTokens,
                    )
                    if (imageDir == null) {
                        ranked
                    } else {
                        val rowById = allRows.associateBy(StashListRow::id)
                        ranked.copy(
                            items = ranked.items.map { item ->
                                if (item.imagePath != null) item
                                else rowById[item.id]?.toModel(imageDir) ?: item
                            }
                        )
                    }
                }
            )
        }.flowOn(Dispatchers.Default)
    }

    @VisibleForTesting
    internal fun rankHybridSearchResults(
        ftsItems: List<StashItem>,
        allItems: List<StashItem>,
        similarityById: Map<String, Float>,
        queryTokens: List<String> = emptyList(),
        minSemanticSimilarity: Float = MIN_SEMANTIC_SIMILARITY,
        maxResults: Int = MAX_SEARCH_RESULTS,
    ): SearchResults {
        val ftsIds = ftsItems.mapTo(HashSet()) { it.id }
        val scored = ArrayList<Pair<StashItem, Float>>(ftsItems.size + 8)
        val semanticMatchIds = HashSet<String>()

        ftsItems.forEachIndexed { index, item ->
            val rankScore = (1.0f - index * 0.03f).coerceAtLeast(0.4f)
            val simBoost = (similarityById[item.id]?.coerceAtLeast(0f) ?: 0f) * 0.35f
            scored.add(item to (1.0f + rankScore + simBoost))
        }

        // Compute the lower-half baseline similarity across the library so flat/unrelated queries
        // (where all items score similarly) do not produce false-positive semantic matches.
        val sortedSims = similarityById.values.sorted()
        val bestOverallSim = sortedSims.lastOrNull() ?: 0f
        val baselineFloor = if (sortedSims.size >= 4) {
            val halfCount = (sortedSims.size / 2).coerceAtLeast(1)
            val bottomHalfMean = sortedSims.take(halfCount).sum() / halfCount
            bottomHalfMean + 0.07f
        } else {
            minSemanticSimilarity
        }

        val effectiveThreshold = maxOf(minSemanticSimilarity, baselineFloor)

        // Count how many query content tokens each item matches lexically.
        val normalizedTokens = queryTokens.map { it.lowercase() }.filter { it.length >= 2 }
        val tokenHitsById = if (normalizedTokens.size >= 2) {
            allItems.associate { item ->
                item.id to countTokenMatches(item, normalizedTokens)
            }
        } else {
            emptyMap()
        }
        val maxTokenHits = tokenHitsById.values.maxOrNull() ?: 0

        val maxDropFromBest = if (ftsItems.isNotEmpty()) 0.06f else 0.07f
        val requiredFloor = effectiveThreshold

        val semanticCandidates = ArrayList<Pair<StashItem, Float>>()
        for (item in allItems) {
            if (item.id in ftsIds) continue
            val sim = similarityById[item.id] ?: continue
            if (sim < requiredFloor) continue
            if (sim < bestOverallSim - maxDropFromBest) continue

            // If another item in the library matched >= 2 query keywords (or FTS matched a multi-word query),
            // reject items that only share a single broad entity token unless similarity is very close to best.
            if (normalizedTokens.size >= 2 && (ftsItems.isNotEmpty() || maxTokenHits >= 2)) {
                val hits = tokenHitsById[item.id] ?: 0
                if (hits < maxOf(2, maxTokenHits) && sim < bestOverallSim - 0.03f) {
                    continue
                }
            }

            semanticCandidates.add(item to sim)
        }

        semanticCandidates
            .sortedByDescending { it.second }
            .take(MAX_SEMANTIC_RESULTS)
            .forEach { (item, sim) ->
                scored.add(item to sim)
                semanticMatchIds.add(item.id)
            }

        val ranked = scored
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }

        val visibleSemanticIds = ranked.mapNotNullTo(HashSet()) { item ->
            item.id.takeIf { it in semanticMatchIds }
        }

        return SearchResults(
            items = ranked,
            semanticMatchIds = visibleSemanticIds,
        )
    }

    private fun countTokenMatches(item: StashItem, lowerTokens: List<String>): Int {
        val haystack = buildString {
            append(item.title).append(' ')
            append(item.headline).append(' ')
            append(item.summary).append(' ')
            append(item.topic).append(' ')
            append(item.domain).append(' ')
            item.tags.forEach { append(it).append(' ') }
        }.lowercase()
        return lowerTokens.count { token -> haystack.contains(token) }
    }

    override fun observeItem(id: String): Flow<StashItem?> =
        dao.observeItem(id).map { it?.toModel(imageDir) }

    override fun observeItems(ids: List<String>): Flow<List<StashItem>> {
        val order = ids.withIndex().associate { (index, id) -> id to index }
        return dao.observeItems(ids).map { rows ->
            rows.distinctBy(StashEntity::id)
                .sortedBy { order[it.id] ?: Int.MAX_VALUE }
                .map { it.toModel(imageDir) }
        }
    }

    override fun observeTags(): Flow<List<TagCount>> = dao.observeAllTags().map { tagsList ->
        tagsList.flatMap { it.split(TAG_SEPARATOR) }
            .mapNotNull { TagNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> TagCount(name, count) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.name.lowercase() })
    }

    override fun observeTopics(): Flow<List<TopicCount>> = dao.observeAllTopics().map { topicsList ->
        topicsList
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> TopicCount(name, count) }
            .sortedWith(compareByDescending<TopicCount> { it.count }.thenBy { it.name.lowercase() })
    }

    override suspend fun addUrl(url: String) {
        val extracted = extractFirstUrl(url)
        val normalized = if (extracted.startsWith("http://") || extracted.startsWith("https://")) {
            extracted
        } else {
            "https://$extracted"
        }
        val domain = runCatching { URI(normalized).host.removePrefix("www.") }.getOrNull()
            ?.takeIf(String::isNotBlank) ?: "saved link"
        val fallbackTitle = deriveFallbackTitle(normalized)

        val id = UUID.randomUUID().toString()
        val initialEntity = StashEntity(
            id = id,
            url = normalized,
            title = fallbackTitle,
            domain = domain,
            category = "Unsorted",
            summary = "Summarizing link...",
            tags = "",
            readTime = "1 min",
            savedAtEpochMillis = System.currentTimeMillis(),
            aiState = AiState.Summarizing.name,
        )
        dao.upsert(initialEntity)

        val available = summarizer.availability() == AiAvailability.Available
        val extraction = extractReadableText(normalized)
        val extractedText = extraction.text
        val hasContent = hasUsableContent(extractedText)
        val fallbackSummary = if (hasContent) {
            extractedText.take(200) + "..."
        } else {
            "Couldn't read this page — it may be private, deleted, or need a login."
        }

        val knownTags = existingTags()
        val knownTopics = existingTopics()

        val (organized, cachedImage) = coroutineScope {
            val pendingImage = extraction.imageUrl?.let { async { cacheHeaderImage(it, id) } }
            val summarized = if (available && hasContent) {
                summarizer.organize(
                    url = normalized,
                    content = extractedText,
                    knownTags = knownTags,
                    knownTopics = knownTopics,
                )
            } else null
            summarized to pendingImage?.await()
        }
        val finalImage = cachedImage ?: if (TwitterExtractor.isTwitterUrl(normalized)) saveXFallbackImage(id) else null
        val category = organized?.category ?: categoryForDomain(normalized) ?: "Unsorted"
        val topic = reconcileTopic(organized?.topic.orEmpty(), knownTopics)

        val finalEntity = initialEntity.copy(
            imageFile = finalImage?.fileName.orEmpty(),
            seedColor = finalImage?.seedColor ?: 0,
            cropBias = finalImage?.cropBias ?: 0f,
            content = if (hasContent) extractedText else "",
            title = organized?.title?.takeIf(::isUsefulTitle) ?: fallbackTitle,
            category = category,
            topic = topic,
            headline = organized?.headline?.takeIf(String::isNotBlank)
                ?: if (hasContent) "" else "Content unavailable",
            summary = organized?.summary?.takeIf(String::isNotBlank) ?: fallbackSummary,
            tags = reconcileTags(organized?.tags.orEmpty(), knownTags).joinToString(TAG_SEPARATOR),
            aiState = when {
                organized != null -> AiState.Ready.name
                !hasContent -> AiState.Failed.name
                available -> AiState.Failed.name
                else -> AiState.Unavailable.name
            },
        )
        dao.upsert(finalEntity)
        indexItemEmbedding(finalEntity)
    }

    private suspend fun indexItemEmbedding(entity: StashEntity) {
        if (!embedder.isReady()) return
        val docText = VectorMath.buildDocumentEmbeddingText(
            title = entity.title,
            headline = entity.headline,
            topic = entity.topic,
            tags = entity.tags,
            summary = entity.summary,
            content = entity.content,
        )
        if (docText.isBlank()) return
        val vector = embedder.embedDocument(docText) ?: return
        runCatching {
            dao.upsertEmbedding(
                StashEmbeddingEntity(
                    id = entity.id,
                    vector = VectorMath.toByteArray(vector),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun backfillEmbeddings(): Int = withContext(Dispatchers.IO) {
        backfillMutex.withLock {
            if (!embedder.isReady() && !embedder.initializeIfPresent()) {
                return@withLock 0
            }
            val pending = runCatching { dao.rowsMissingEmbeddings() }.getOrNull().orEmpty()
            if (pending.isEmpty()) return@withLock 0

            StashLog.d("RoomStashRepository", "Backfilling embeddings for ${pending.size} items")
            var indexed = 0
            for (row in pending) {
                val docText = VectorMath.buildDocumentEmbeddingText(
                    title = row.title,
                    headline = row.headline,
                    topic = row.topic,
                    tags = row.tags,
                    summary = row.summary,
                    content = row.content,
                )
                if (docText.isBlank()) continue
                val vector = embedder.embedDocument(docText) ?: continue
                runCatching {
                    dao.upsertEmbedding(
                        StashEmbeddingEntity(
                            id = row.id,
                            vector = VectorMath.toByteArray(vector),
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                    )
                    indexed++
                }
            }
            StashLog.d("RoomStashRepository", "Embedding backfill complete: $indexed/${pending.size} items")
            indexed
        }
    }

    private fun hasUsableContent(extracted: String): Boolean {
        if (extracted.isBlank() || extracted.startsWith("Saved URL:")) return false
        val body = extracted
            .substringAfter("Article Body:")
            .substringAfter("Post text:")
            .trim()
        return extracted.length >= MIN_EXTRACT_CHARS || body.length >= MIN_EXTRACT_CHARS
    }

    override suspend fun setRead(id: String, isRead: Boolean) = dao.setRead(id, isRead)

    suspend fun backfillSeedColors() {
        val dir = imageDir ?: return
        withContext(Dispatchers.IO) {
            val pending = runCatching { dao.rowsWithImage() }.getOrNull().orEmpty()
            for (row in pending) {
                val file = File(dir, row.imageFile)
                if (!file.exists()) continue
                val analysis = runCatching { ImageAnalyzer.analyzeImage(file.readBytes()) }.getOrNull() ?: continue
                if (analysis.seedColor != row.seedColor || analysis.cropBias != row.cropBias) {
                    runCatching { dao.setSeedAndCrop(row.id, analysis.seedColor, analysis.cropBias) }
                }
            }
        }
    }

    suspend fun backfillNormalizedTags() {
        withContext(Dispatchers.IO) {
            val pending = runCatching { dao.rowsForTagBackfill() }.getOrNull().orEmpty()
            for (row in pending) {
                val rawTags = row.tags.split(TAG_SEPARATOR).map(String::trim).filter(String::isNotBlank)
                val normalized = reconcileTags(rawTags, knownTags = emptyList())
                val newTagsStr = normalized.joinToString(TAG_SEPARATOR)
                if (newTagsStr != row.tags) {
                    runCatching { dao.setTags(row.id, newTagsStr) }
                }
            }
        }
    }

    /**
     * Backfills topics for items missing one using the on-device LibrarianAgent.
     * Safe to call at startup — no-ops if AI is unavailable or no items need backfill.
     */
    override suspend fun backfillTopics(): Int {
        val agent = LibrarianAgent(dao, summarizer)
        return agent.backfillTopics()
    }

    suspend fun backfillTwitterImages() {
        val dir = imageDir ?: return
        withContext(Dispatchers.IO) {
            val rows = runCatching { dao.allTwitterRows() }.getOrNull().orEmpty()
            for (row in rows) {
                val file = if (row.imageFile.isNotBlank()) File(dir, row.imageFile) else null
                val needsUpgrade = file == null || !file.exists() || file.length() == 0L || isLowResolutionImage(file)
                if (!needsUpgrade) continue

                val extraction = extractTweet(row.url)
                val cached = extraction?.imageUrl?.let { cacheHeaderImage(it, row.id) }
                    ?: if (file == null || !file.exists()) saveXFallbackImage(row.id) else null
                if (cached != null) {
                    runCatching {
                        dao.setImageData(row.id, cached.fileName, cached.seedColor, cached.cropBias)
                    }
                    StashLog.d("RoomStashRepository", "Backfilled high-res Twitter image for ${row.id}: ${cached.fileName}")
                }
            }
        }
    }

    @VisibleForTesting
    internal fun isLowResolutionImage(file: File): Boolean = ImageAnalyzer.isLowResolutionImage(file)

    override suspend fun delete(id: String) {
        val imageFile = runCatching { dao.imageFileFor(id) }.getOrNull()
        dao.delete(id)
        if (!imageFile.isNullOrBlank() && imageDir != null) {
            withContext(Dispatchers.IO) {
                runCatching { File(imageDir, imageFile).delete() }
            }
        }
    }

    override fun chat(item: StashItem, history: List<ChatTurn>, question: String): Flow<String> =
        summarizer.chatStream(itemChatContext(item), history, question)

    override fun briefing(
        items: List<StashItem>,
        topic: String?,
        history: List<ChatTurn>,
        question: String?,
    ): Flow<String> = summarizer.briefingStream(
        itemsContext = itemsBriefingContext(items),
        itemCount = items.size.coerceAtMost(PromptContextBuilders.MAX_BRIEFING_ITEMS),
        topic = topic,
        history = history,
        question = question,
    )

    @VisibleForTesting
    internal fun itemsBriefingContext(items: List<StashItem>): String =
        PromptContextBuilders.itemsBriefingContext(items)

    private fun itemChatContext(item: StashItem): String =
        PromptContextBuilders.itemChatContext(item)

    override suspend fun getModelVersion(): String = summarizer.getModelVersion()

    override suspend fun probeModels() = summarizer.probeModels()

    override suspend fun selectModel(choice: ModelChoice) = summarizer.selectModel(choice)

    private suspend fun existingTags(): List<String> =
        dao.allTags()
            .flatMap { it.split(TAG_SEPARATOR) }
            .mapNotNull { TagNormalizer.normalize(it) }
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }

    private suspend fun existingTopics(): List<String> =
        dao.allTopics()
            .map { it.trim() }
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }

    @VisibleForTesting
    internal fun reconcileTopic(topic: String, knownTopics: List<String>): String {
        val cleaned = topic.trim()
        if (cleaned.isBlank()) return ""
        knownTopics.find { it.equals(cleaned, ignoreCase = true) }?.let { return it }
        val stem = TagNormalizer.stem(cleaned)
        if (stem.isNotBlank()) {
            knownTopics.find { TagNormalizer.stem(it) == stem }?.let { return it }
        }
        return cleaned
    }

    @VisibleForTesting
    internal fun reconcileTags(tags: List<String>, knownTags: List<String>): List<String> {
        val knownByStem = knownTags
            .mapNotNull { tag -> TagNormalizer.normalize(tag)?.let { TagNormalizer.stem(it) to tag } }
            .toMap()

        return tags.asSequence()
            .mapNotNull { TagNormalizer.normalize(it) }
            .map { normalized -> knownByStem[TagNormalizer.stem(normalized)] ?: normalized }
            .distinctBy { TagNormalizer.stem(it) }
            .take(MAX_TAGS_PER_ITEM)
            .toList()
    }

    private fun isUsefulTitle(title: String): Boolean {
        val cleaned = title.trim()
        if (cleaned.length < 4) return false
        return cleaned.lowercase() !in PLACEHOLDER_TITLES
    }

    private suspend fun extractReadableText(url: String): Extraction =
        WebPageExtractor.extractReadableText(url)

    private suspend fun cacheHeaderImage(imageUrl: String, id: String): CachedImage? {
        val dir = imageDir ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!dir.exists() && !dir.mkdirs()) return@runCatching null
                val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", IMAGE_USER_AGENT)
                }
                if (connection.responseCode !in 200..299) return@runCatching null
                val declared = connection.contentLengthLong
                if (declared > MAX_IMAGE_BYTES) return@runCatching null

                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        buffer.write(chunk, 0, read)
                        if (buffer.size() > MAX_IMAGE_BYTES) return@runCatching null
                    }
                    buffer.toByteArray()
                }
                if (bytes.isEmpty()) return@runCatching null

                val name = "$id.img"
                val file = File(dir, name)
                file.writeBytes(bytes)
                ImageBitmapCache.evict(file.absolutePath)
                val analysis = ImageAnalyzer.analyzeImage(bytes)
                CachedImage(
                    fileName = name,
                    seedColor = analysis.seedColor,
                    cropBias = analysis.cropBias,
                )
            }.getOrNull()
        }
    }

    @VisibleForTesting
    internal fun Document.articleText(): String = with(WebPageExtractor) { articleText() }

    @VisibleForTesting
    internal fun extractTweet(url: String): Extraction? = TwitterExtractor.extractTweet(url)

    @VisibleForTesting
    internal fun parseFxTwitterJson(json: String): Extraction? = TwitterExtractor.parseFxTwitterJson(json)

    @VisibleForTesting
    internal fun parseVxTwitterJson(json: String): Extraction? = TwitterExtractor.parseVxTwitterJson(json)

    @VisibleForTesting
    internal fun upgradeTwitterAvatarUrl(url: String): String = TwitterExtractor.upgradeTwitterAvatarUrl(url)

    @VisibleForTesting
    internal fun upgradeTwitterPhotoUrl(url: String): String = TwitterExtractor.upgradeTwitterPhotoUrl(url)

    @VisibleForTesting
    internal fun generateXFallbackImage(width: Int = 800, height: Int = 450): ByteArray =
        ImageFallbackRenderer.generateXFallbackImage(width, height)

    private fun saveXFallbackImage(id: String): CachedImage? {
        val dir = imageDir ?: return null
        return runCatching {
            if (!dir.exists() && !dir.mkdirs()) return null
            val bytes = generateXFallbackImage()
            if (bytes.isEmpty()) return null
            val name = "$id.img"
            val file = File(dir, name)
            file.writeBytes(bytes)
            ImageBitmapCache.evict(file.absolutePath)
            val analysis = ImageAnalyzer.analyzeImage(bytes)
            CachedImage(
                fileName = name,
                seedColor = analysis.seedColor,
                cropBias = analysis.cropBias,
            )
        }.getOrNull()
    }

    internal fun readText(endpoint: String): String? = TwitterExtractor.readText(endpoint)

    @VisibleForTesting
    internal fun extractSearchTokens(query: String): List<String> {
        val dePossessed = query
            .replace(POSSESSIVE_SUFFIX_REGEX, "")
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()
        if (dePossessed.isBlank()) return emptyList()

        val rawTokens = dePossessed
            .split(WHITESPACE_REGEX)
            .map(String::trim)
            .filter { it.length >= 2 }

        val contentTokens = rawTokens.filter { it.lowercase() !in SEARCH_STOP_WORDS }
        return contentTokens.ifEmpty { rawTokens }
    }

    @VisibleForTesting
    internal fun isPlausibleSemanticQuery(tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        for (token in tokens) {
            val lower = token.lowercase()
            if (lower in KNOWN_TECH_VOWELLESS) continue
            // Reject 4+ identical consecutive characters (e.g. "aaaa")
            if (REPEATED_CHAR_REGEX.containsMatchIn(lower)) return false
            // Words of 4+ letters must contain at least one vowel or digit
            if (lower.length >= 4 && !lower.any { it in "aeiouy0123456789" }) return false
            // Reject 5+ consecutive consonants or 4+ consecutive vowels
            if (CONSONANT_CLUSTER_REGEX.containsMatchIn(lower)) return false
            if (VOWEL_CLUSTER_REGEX.containsMatchIn(lower)) return false
            // Reject 4+ char keyboard-row runs (e.g. "qwert", "asdf", "zxcv", "uiop", "hjkl")
            if (lower.length >= 4 && containsKeyboardMash(lower)) return false
        }
        return true
    }

    private fun containsKeyboardMash(lower: String): Boolean {
        for (row in KEYBOARD_ROWS) {
            for (i in 0..(row.length - 4)) {
                val sub = row.substring(i, i + 4)
                if (lower.contains(sub) || lower.contains(sub.reversed())) return true
            }
        }
        return false
    }

    @VisibleForTesting
    internal fun toFtsQuery(query: String): String {
        val tokens = extractSearchTokens(query)
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" AND ") { "\"$it\"*" }
    }
}

private fun StashEntity.toModel(imageDir: File? = null) = StashItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    category = category,
    imagePath = imageFile.takeIf { it.isNotBlank() && imageDir != null }
        ?.let { File(imageDir, it).takeIf(File::exists)?.absolutePath },
    content = content,
    headline = resolveCompleteHeadline(headline, summary),
    summary = summary,
    tags = tags.split(TAG_SEPARATOR).mapNotNull(TagNormalizer::normalize).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
    seedColor = seedColor,
    cropBias = cropBias,
    topic = topic,
)

private fun StashListRow.toModel(imageDir: File? = null) = StashItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    category = category,
    imagePath = imageFile.takeIf { it.isNotBlank() && imageDir != null }
        ?.let { File(imageDir, it).takeIf(File::exists)?.absolutePath },
    headline = resolveCompleteHeadline(headline, summary),
    summary = summary,
    tags = tags.split(TAG_SEPARATOR).mapNotNull(TagNormalizer::normalize).filter(String::isNotBlank),
    readTime = readTime,
    savedAtEpochMillis = savedAtEpochMillis,
    isRead = isRead,
    aiState = AiState.valueOf(aiState),
    seedColor = seedColor,
    cropBias = cropBias,
    topic = topic,
)

private const val TAG_SEPARATOR = " | "
private const val MAX_TAGS_PER_ITEM = 6
private const val MAX_SEARCH_RESULTS = 20
private const val MAX_SEMANTIC_RESULTS = 5

/**
 * Repairs legacy `headline` strings that were previously truncated at ~90 characters mid-sentence
 * by falling back to the first complete bullet point in [summary].
 */
@VisibleForTesting
internal fun resolveCompleteHeadline(headline: String, summary: String): String {
    val cleanHeadline = headline.trim()
    val firstBullet = summary.lineSequence()
        .map { it.removePrefix("- ").trim() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    if (cleanHeadline.isBlank()) return firstBullet
    if (firstBullet.isBlank()) return cleanHeadline

    if (firstBullet.length > cleanHeadline.length &&
        firstBullet.startsWith(cleanHeadline, ignoreCase = true)
    ) {
        return firstBullet
    }

    val lastWord = cleanHeadline.substringAfterLast(' ').lowercase().trimEnd('.', ',', ';', ':')
    val looksTruncatedAt90 = cleanHeadline.length in 74..90 && !cleanHeadline.endsWith(".")
    if (looksTruncatedAt90 || lastWord in DANGLING_END_WORDS) {
        return firstBullet
    }

    return cleanHeadline
}

private val DANGLING_END_WORDS = setOf(
    "the", "a", "an", "for", "to", "of", "in", "on", "with", "and", "or", "by",
    "from", "as", "at", "into", "their", "its", "our", "your", "this", "that",
    "these", "those", "evolving", "complex", "advanced", "modern", "new", "more",
    "most", "other", "such", "between", "through", "without", "within", "using",
)
private const val MIN_EXTRACT_CHARS = 120
private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
private const val IMAGE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Stash/1.0"

private val SEARCH_STOP_WORDS = setOf(
    "what", "whats", "is", "are", "was", "were", "be", "been", "being",
    "the", "a", "an", "how", "why", "where", "when", "who", "whom", "which",
    "do", "does", "did", "can", "could", "should", "would", "will", "shall",
    "to", "in", "on", "of", "for", "with", "about", "from", "by", "at", "as",
    "into", "like", "through", "after", "over", "between", "out", "against",
    "during", "without", "before", "under", "around", "among",
    "my", "your", "his", "her", "its", "our", "their", "me", "you", "him", "them",
    "it", "this", "that", "these", "those", "and", "or", "but", "if", "then",
    "so", "than", "too", "very", "just", "tell", "show", "find", "explain", "describe",
)

private val KNOWN_TECH_VOWELLESS = setOf(
    "llm", "llms", "gpu", "gpus", "cpu", "cpus", "npu", "tpu", "sdk", "ndk",
    "fts", "sql", "css", "html", "http", "https", "xml", "json", "png", "svg",
    "jpg", "rgb", "hct", "mrl", "rag", "mvvm", "mvi", "grpc", "jwt", "ssh",
    "tcp", "udp", "dns", "cli", "gui", "ksp", "jni", "apk", "adb", "jvm",
)

private val KEYBOARD_ROWS = listOf(
    "qwertyuiop",
    "qwertzuiop",
    "asdfghjkl",
    "zxcvbnm",
)

private val POSSESSIVE_SUFFIX_REGEX = Regex("(?i)(?:['’](?:s|t|re|ve|ll|d|m))+\\b")
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-zA-Z0-9\\s]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val REPEATED_CHAR_REGEX = Regex("(.)\\1{3,}")
private val CONSONANT_CLUSTER_REGEX = Regex("[bcdfghjklmnpqrstvwxz]{5,}")
private val VOWEL_CLUSTER_REGEX = Regex("[aeiouy]{4,}")

private data class CachedImage(
    val fileName: String,
    val seedColor: Int,
    val cropBias: Float,
)

private val PLACEHOLDER_TITLES = setOf(
    "twitter post", "x post", "tweet", "social media post", "post",
    "untitled", "unknown", "web page", "webpage", "website", "link", "saved link",
    "x.com", "twitter.com", "x", "twitter",
)
