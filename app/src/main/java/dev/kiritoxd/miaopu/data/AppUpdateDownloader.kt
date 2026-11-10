package dev.kiritoxd.miaopu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK into the app's own external files dir so the update can be
 * installed in-place instead of bouncing the user to a browser download page.
 *
 * The native GitHub URL is tried first with an 8-second connect/read timeout. Switching to a
 * mirror is an explicit user action: re-invoke with [useMirror] = true to download through
 * the public mirrors from the start. A healthy transfer is never cut short by a time budget.
 */
class AppUpdateDownloader(private val context: Context) {
    private val directClient = OkHttpClient.Builder()
        .connectTimeout(DIRECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DIRECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val proxyClient = OkHttpClient.Builder()
        .connectTimeout(DIRECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROXY_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        url: String,
        fileName: String,
        useMirror: Boolean = false,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, fileName)
        val partial = File(dir, "$fileName.part")
        val routes = buildList {
            if (useMirror && url.startsWith("https://github.com/")) {
                // Only known GitHub release assets are sent through a mirror; never forward an
                // arbitrary URL returned by the API to a third party.
                GitHubReleaseAdapter.DOWNLOAD_PROXIES.forEach { prefix ->
                    add(prefix + url to proxyClient)
                }
            } else {
                // First attempt is always the native link. Only connect/read timeouts apply:
                // a slow but steady download is allowed to finish rather than being restarted.
                add(url to if (useMirror) proxyClient else directClient)
            }
        }
        var lastError: IOException? = null
        for ((route, client) in routes) {
            try {
                return@withContext downloadFrom(route, client, target, partial, onProgress)
            } catch (error: IOException) {
                lastError = error
                // A partially written file must not be mistaken for a resume point.
                partial.delete()
            }
        }
        throw IOException("下载失败：${lastError?.message ?: "没有可用线路"}")
    }

    private fun downloadFrom(
        url: String,
        client: OkHttpClient,
        target: File,
        partial: File,
        onProgress: (Int) -> Unit,
    ): File {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "Miaopu-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("下载失败：响应为空")
            val total = body.contentLength()
            partial.delete()
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (total > 0 && partial.length() != total) throw IOException("下载不完整，请重试")
            target.delete()
            if (!partial.renameTo(target)) throw IOException("无法保存安装包")
        }
        onProgress(100)
        return target
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
        const val DIRECT_TIMEOUT_SECONDS = 8L
        const val PROXY_READ_TIMEOUT_SECONDS = 30L
    }
}
