package dev.cburlacu.stash.ui.util

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fast in-memory cache for decoded header images so shared element transitions
 * have the bitmap immediately available on frame 0 without decoding latency or pop-in.
 */
object ImageBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(32)

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }

    fun evict(path: String) {
        cache.remove(path)
    }

    suspend fun load(path: String, targetHeightPx: Int = 600): ImageBitmap? {
        get(path)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, bounds.outHeight / targetHeightPx)
                }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()?.also { bitmap ->
                    put(path, bitmap)
                }
            }.getOrNull()
        }
    }
}
