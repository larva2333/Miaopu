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
 */
class AppUpdateDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, fileName)
        val partial = File(dir, "$fileName.part")
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
        target
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
