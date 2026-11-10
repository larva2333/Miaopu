package dev.kiritoxd.miaopu.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.ErrorResult
import coil3.toBitmap
import dev.kiritoxd.miaopu.data.secureImageUrl
import dev.kiritoxd.miaopu.data.trimTransparent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest

internal class WidgetLogoStore(private val context: Context) {
    // v3: logos are stored transparent-trimmed; bump to invalidate the v2 untrimmed cache.
    private val directory = File(context.cacheDir, "widget-logos-v3")

    fun cached(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val file = fileFor(url)
        if (!file.isFile) return null
        val key = cacheKey(file)
        return synchronized(bitmaps) {
            val hit = bitmaps.get(key)
            if (hit != null) return@synchronized hit
            val decoded = BitmapFactory.decodeFile(file.path)
            if (decoded != null) {
                bitmaps.put(key, decoded)
            } else {
                // A truncated or zero-byte thumbnail would otherwise be pinned forever: the loader
                // skips any URL whose file exists, so nothing ever retries a file that cannot decode.
                Log.w(LOGO_TAG, "Dropping unreadable crest ${file.name} (${file.length()} bytes)")
                file.delete()
            }
            decoded
        }
    }

    suspend fun load(matches: List<WidgetMatch>): Boolean = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            directory.mkdirs()
            val changed = coroutineScope {
                matches.flatMap { it.teams }.mapNotNull { it.logoUrl?.secureImageUrl() }
                    .filter { it.startsWith("https://") }.distinct()
                    .take(WidgetScheduleSizing.MAX_VISIBLE_LOGOS).map { url ->
                        async {
                            val existing = fileFor(url)
                            // An empty file left behind by a killed process must not count as cached.
                            if (existing.isFile && existing.length() > 0) return@async false
                            existing.delete()
                            Log.d(LOGO_TAG, "Fetching crest $url")
                            fetch(url)
                        }
                    }.awaitAll().any { it }
            }
            directory.listFiles()?.filter { it.extension == "png" }?.sortedByDescending(File::lastModified)
                ?.drop(64)?.forEach(File::delete)
            changed
        }
    }

    /** Downloads one crest; false when nothing new was stored so the caller can skip a repaint. */
    private suspend fun fetch(url: String): Boolean = withTimeoutOrNull(LOGO_TIMEOUT_MILLIS) {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build(),
        )
        val image = result.image
        if (image == null) {
            val reason = (result as? ErrorResult)?.throwable?.message
            Log.w(LOGO_TAG, "Crest fetch failed for $url: $reason")
            return@withTimeoutOrNull false
        }
        // Keep the decoded aspect ratio so ImageView can center wide flags and tall crests,
        // but trim transparent canvas padding so every crest fills its box the same way.
        val bitmap = image.toBitmap().trimTransparent()
        // Atomically replace a complete thumbnail; never expose a partial PNG to a renderer.
        val temporary = File.createTempFile("logo", ".tmp", directory)
        try {
            val encoded = temporary.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (!encoded) {
                Log.w(LOGO_TAG, "Crest encode failed for $url")
                return@withTimeoutOrNull false
            }
            val destination = fileFor(url)
            temporary.renameTo(destination).also { saved ->
                if (saved) synchronized(bitmaps) { bitmaps.put(cacheKey(destination), bitmap) }
                else Log.w(LOGO_TAG, "Crest store failed for $url")
            }
        } finally {
            temporary.delete()
        }
    } ?: run {
        Log.w(LOGO_TAG, "Crest fetch timed out for $url")
        false
    }

    private fun cacheKey(file: File): String = "${file.absolutePath}:${file.lastModified()}:${file.length()}"

    private fun fileFor(url: String): File {
        // 规范化后取指纹：渲染侧传进来的还是原始地址，两边必须算出同一个文件名。
        val normalized = url.secureImageUrl().orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.png")
    }

    private companion object {
        const val LOGO_TAG = "MiaopuWidget"

        /** One crest alone rarely needs more than a second; leave room for a slow mobile route. */
        const val LOGO_TIMEOUT_MILLIS = 15_000L

        val loadMutex = Mutex()
        val bitmaps = object : LruCache<String, Bitmap>(1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }
}
