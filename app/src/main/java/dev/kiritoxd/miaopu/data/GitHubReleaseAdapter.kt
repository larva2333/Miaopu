package dev.kiritoxd.miaopu.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class GitHubRelease(
    val tagName: String,
    val title: String,
    val pageUrl: String,
    val publishedAt: String?,
    val apkUrl: String? = null,
    val apkSizeBytes: Long = 0L,
)

class GitHubReleaseAdapter {
    /** Try the native GitHub API first, then fall back to a mirror within the same 8s budget. */
    suspend fun latest(): AdapterResult<GitHubRelease> = withContext(Dispatchers.IO) {
        var lastFailure: AdapterResult<GitHubRelease>? = null
        for (url in RELEASE_API_URLS) {
            val result = fetch(url)
            // 404 means "no release published", not a network problem; another route agrees.
            if (result.data != null || result.status == AdapterStatus.NOT_CONFIGURED) return@withContext result
            lastFailure = result
        }
        lastFailure ?: AdapterResult.failure(
            source = SOURCE,
            status = AdapterStatus.TRANSIENT_FAILURE,
            message = "GitHub 更新服务暂时不可用",
            retryable = true,
        )
    }

    private fun fetch(url: String): AdapterResult<GitHubRelease> {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = ROUTE_TIMEOUT_MILLIS
            readTimeout = ROUTE_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Miaopu-Android")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when {
                status == HttpURLConnection.HTTP_NOT_FOUND -> AdapterResult.failure(
                    source = SOURCE,
                    status = AdapterStatus.NOT_CONFIGURED,
                    message = "仓库暂未发布 Release",
                    retryable = false,
                    httpCode = status,
                )
                status !in 200..299 -> AdapterResult.failure(
                    source = SOURCE,
                    status = AdapterStatus.TRANSIENT_FAILURE,
                    message = "GitHub 更新服务暂时不可用",
                    retryable = true,
                    httpCode = status,
                )
                else -> runCatching { parseGitHubRelease(body) }
                    .fold(
                        onSuccess = { AdapterResult.success(SOURCE, it) },
                        onFailure = {
                            AdapterResult.failure(
                                source = SOURCE,
                                status = AdapterStatus.INVALID_RESPONSE,
                                message = it.message ?: "GitHub Release 响应格式已变化",
                                retryable = false,
                                httpCode = status,
                            )
                        },
                    )
            }
        } catch (error: IOException) {
            AdapterResult.failure(
                source = SOURCE,
                status = AdapterStatus.TRANSIENT_FAILURE,
                message = error.message ?: "无法连接 GitHub",
                retryable = true,
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPOSITORY_URL = "https://github.com/larva2333/Miaopu"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/larva2333/Miaopu/releases/latest"
        /** Prefixes tried in order after a direct download stalls. Consumed by AppUpdateDownloader. */
        internal val DOWNLOAD_PROXIES = listOf("https://gh-proxy.com/", "https://ghfast.top/")
        private const val SOURCE = "github.release.latest"
        private const val ROUTE_TIMEOUT_MILLIS = 8_000
        private const val API_PROXY = "https://gh-proxy.com/"
        private val RELEASE_API_URLS = listOf(LATEST_RELEASE_API, API_PROXY + LATEST_RELEASE_API)
    }
}

internal fun parseGitHubRelease(json: String): GitHubRelease {
    val source = JSONObject(json)
    val tagName = source.optString("tag_name").takeIf(String::isNotBlank)
        ?: error("Release 缺少 tag_name")
    val pageUrl = source.optString("html_url").takeIf(String::isNotBlank)
        ?: error("Release 缺少 html_url")
    val apk = source.optJSONArray("assets")?.let { assets ->
        (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { asset ->
                asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                    asset.optString("browser_download_url").isNotBlank()
            }
    }
    return GitHubRelease(
        tagName = tagName,
        title = source.optString("name").takeIf(String::isNotBlank) ?: tagName,
        pageUrl = pageUrl,
        publishedAt = source.optString("published_at").takeIf(String::isNotBlank),
        apkUrl = apk?.optString("browser_download_url")?.takeIf(String::isNotBlank),
        apkSizeBytes = apk?.optLong("size") ?: 0L,
    )
}

internal fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.versionParts()
    val currentParts = current.versionParts()
    val count = maxOf(latestParts.size, currentParts.size)
    for (index in 0 until count) {
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun String.versionParts(): List<Int> =
    trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
        .ifEmpty { listOf(0) }
