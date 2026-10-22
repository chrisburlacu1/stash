package dev.cburlacu.stash.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fast in-memory cache for decoded header images so shared element transitions
 * have the bitmap immediately available on frame 0 without decoding latency or pop-in.
 */
object ImageBitmapCache {
    // 32 MB byte-sized cache (measured in KB) so memory usage is strictly bounded.
    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES / 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }

    fun evict(path: String) {
        cache.remove(path)
    }

    suspend fun load(path: String, targetHeightPx: Int = DEFAULT_BITMAP_HEIGHT_PX): ImageBitmap? {
        val requestedHeight = targetHeightPx.coerceIn(MIN_BITMAP_HEIGHT_PX, MAX_BITMAP_HEIGHT_PX)
        val cached = get(path)
        if (cached != null && cached.height >= minOf(requestedHeight, MIN_REUSABLE_HERO_PX)) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, bounds.outHeight / requestedHeight)
                }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()?.also { bitmap ->
                    val existing = get(path)
                    if (existing == null || bitmap.height >= existing.height) {
                        put(path, bitmap)
                    }
                }
            }.getOrNull()
        }
    }

    private const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    private const val MIN_BITMAP_HEIGHT_PX = 144
    private const val MIN_REUSABLE_HERO_PX = 512
    private const val DEFAULT_BITMAP_HEIGHT_PX = 1080
    private const val MAX_BITMAP_HEIGHT_PX = 2048
}
