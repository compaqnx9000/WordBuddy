package com.hotgis.wordbuddy.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Resolves a Kuaishou share blob, then saves the video into Movies/WordBuddy.
 * Short links land on the mobile share page; `window.INIT_STATE` holds `photo.mainMvUrls`.
 */
class KuaishouDownloader(private val context: Context) {
    fun download(shareText: String, onProgress: (done: Long, total: Long) -> Unit): SavedKuaishouVideo {
        val shareUrl = extractKuaishouUrl(shareText)
        val jar = MemoryCookieJar()
        val client = httpClient(jar, readTimeoutSec = 20)
        val page = fetch(client, shareUrl)
            ?: throw IllegalStateException("打不开这条快手链接，请稍后再试")
        val photo = parsePhoto(page.body)
            ?: throw IllegalStateException("快手暂时没有返回视频信息，请稍后再试")
        val playUrls = collectPlayUrls(photo)
        if (playUrls.isEmpty()) {
            if (photo.has("atlas") || photo.optJSONObject("atlas") != null) {
                throw IllegalStateException("这是图集，暂时只支持下载视频")
            }
            throw IllegalStateException("这条作品没有可下载的视频")
        }
        val photoId = photo.optString("photoId").ifBlank {
            photoIdFrom(page.finalUrl) ?: "kuaishou"
        }
        val title = photo.optString("caption").trim()
            .takeIf { it.isNotBlank() && it != "..." }
            ?: photo.optString("userName").trim().ifBlank { "快手视频" }
        val downloader = httpClient(jar, readTimeoutSec = 60)
        val saved = saveFirstVideo(downloader, playUrls, fileName(title, photoId), onProgress)
        return SavedKuaishouVideo(title = title, uri = saved)
    }

    private fun collectPlayUrls(photo: JSONObject): List<String> {
        val seen = linkedSetOf<String>()
        fun add(raw: String?) {
            val url = raw?.trim().orEmpty()
            if (url.startsWith("http")) seen += url
        }
        fun addList(list: JSONArray?) {
            if (list == null) return
            for (i in 0 until list.length()) {
                val item = list.opt(i)
                when (item) {
                    is JSONObject -> add(item.optString("url"))
                    is String -> add(item)
                }
            }
        }
        addList(photo.optJSONArray("mainMvUrls"))
        add(photo.optString("photoUrl"))
        add(photo.optString("photoH265Url"))
        add(photo.optString("mainMvUrl"))
        add(photo.optString("srcUrl"))
        val manifestRaw = photo.opt("manifest")
        val manifest = when (manifestRaw) {
            is JSONObject -> manifestRaw
            is String -> runCatching { JSONObject(manifestRaw) }.getOrNull()
            else -> null
        }
        val sets = manifest?.optJSONArray("adaptationSet")
        if (sets != null) {
            for (i in 0 until sets.length()) {
                val reps = sets.optJSONObject(i)?.optJSONArray("representation") ?: continue
                for (j in 0 until reps.length()) {
                    val rep = reps.optJSONObject(j) ?: continue
                    add(rep.optString("url"))
                    val backup = rep.opt("backupUrl")
                    when (backup) {
                        is String -> add(backup)
                        is JSONArray -> addList(backup)
                    }
                }
            }
        }
        return seen.toList()
    }

    private fun saveFirstVideo(
        client: OkHttpClient,
        playUrls: List<String>,
        displayName: String,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Uri {
        var lastError: Exception? = null
        for (playUrl in playUrls) {
            try {
                return saveVideo(client, playUrl, displayName, onProgress)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("没有拿到视频文件，请稍后再试")
    }

    private fun saveVideo(
        client: OkHttpClient,
        playUrl: String,
        displayName: String,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Uri {
        val request = Request.Builder()
            .url(playUrl)
            .headers(playHeaders())
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("视频请求失败（${resp.code}）")
            }
            val type = resp.header("content-type").orEmpty()
            if (type.contains("text/html") || type.contains("json")) {
                throw IllegalStateException("没有拿到视频文件，请稍后再试")
            }
            val body = resp.body ?: throw IllegalStateException("没有拿到视频文件，请稍后再试")
            val total = body.contentLength()
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/WordBuddy")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val target = resolver.insert(collection, values)
                ?: throw IllegalStateException("无法保存到相册")
            try {
                resolver.openOutputStream(target)?.use { out ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, out, total, onProgress)
                    }
                } ?: throw IllegalStateException("无法写入相册")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(target, values, null, null)
                }
                return target
            } catch (error: Exception) {
                resolver.delete(target, null, null)
                throw error
            }
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        var sinceReport = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            done += count
            sinceReport += count
            if (sinceReport >= 256 * 1024) {
                sinceReport = 0
                onProgress(done, total)
            }
        }
        onProgress(done, if (total > 0) total else done)
        if (done < 1024) {
            throw IllegalStateException("下载到的文件太小，请稍后再试")
        }
    }

    private fun fetch(client: OkHttpClient, url: String): Fetched? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(pageHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful && resp.body == null) return@use null
                Fetched(
                    finalUrl = resp.request.url.toString(),
                    body = resp.body?.string().orEmpty(),
                )
            }
        }.getOrNull()
    }

    private class MemoryCookieJar : CookieJar {
        private val lock = Any()
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(lock) {
                cookies.forEach { incoming ->
                    this.cookies.removeAll { existing ->
                        existing.name == incoming.name &&
                            existing.domain == incoming.domain &&
                            existing.path == incoming.path
                    }
                    this.cookies += incoming
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(lock) {
                return cookies.filter { it.matches(url) }
            }
        }
    }

    private data class Fetched(
        val finalUrl: String,
        val body: String,
    )

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
        private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val photoPathRegex = Regex("""/(?:fw/photo|short-video|photo)/([A-Za-z0-9_-]+)""")
        private val photoQueryRegex = Regex("""(?:photoId|photo_id)=([A-Za-z0-9_-]+)""")

        private fun httpClient(jar: CookieJar, readTimeoutSec: Long): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(jar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        private fun pageHeaders(): okhttp3.Headers {
            return okhttp3.Headers.Builder()
                .add("User-Agent", MOBILE_UA)
                .add(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                )
                .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .add("Referer", "https://www.kuaishou.com/")
                .build()
        }

        private fun playHeaders(): okhttp3.Headers {
            return okhttp3.Headers.Builder()
                .add("User-Agent", MOBILE_UA)
                .add("Accept", "*/*")
                .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .add("Referer", "https://www.kuaishou.com/")
                .build()
        }

        private fun extractKuaishouUrl(text: String): String {
            val url = urlRegex.findAll(text)
                .map { it.value.trimEnd('.', ',', '，', '。', '!', '！', ')', '）') }
                .firstOrNull { isKuaishouHost(it) }
            return url ?: throw IllegalStateException("没有找到快手链接，请粘贴完整分享文案")
        }

        private fun isKuaishouHost(url: String): Boolean {
            val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
            return host == "kuaishou.com" ||
                host.endsWith(".kuaishou.com") ||
                host == "chenzhongtech.com" ||
                host.endsWith(".chenzhongtech.com") ||
                host == "gifshow.com" ||
                host.endsWith(".gifshow.com") ||
                host == "kwai.com" ||
                host.endsWith(".kwai.com")
        }

        private fun photoIdFrom(text: String): String? {
            return photoPathRegex.find(text)?.groupValues?.getOrNull(1)
                ?: photoQueryRegex.find(text)?.groupValues?.getOrNull(1)
        }

        private fun parsePhoto(html: String): JSONObject? {
            val state = extractInitState(html) ?: return null
            return findPhoto(state)
        }

        private fun extractInitState(html: String): JSONObject? {
            val marker = html.indexOf("INIT_STATE")
            if (marker < 0) return null
            val start = html.indexOf('{', marker)
            if (start < 0) return null
            val end = endOfJsonObject(html, start) ?: return null
            return runCatching { JSONObject(html.substring(start, end)) }.getOrNull()
        }

        private fun endOfJsonObject(text: String, start: Int): Int? {
            var depth = 0
            var inString = false
            var escape = false
            for (index in start until text.length) {
                val ch = text[index]
                if (inString) {
                    when {
                        escape -> escape = false
                        ch == '\\' -> escape = true
                        ch == '"' -> inString = false
                    }
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return index + 1
                    }
                }
            }
            return null
        }

        private fun findPhoto(node: Any?): JSONObject? {
            when (node) {
                is JSONObject -> {
                    if (node.has("mainMvUrls") || node.has("photoUrl") || node.has("atlas")) {
                        return node
                    }
                    node.optJSONObject("photo")?.let { nested ->
                        if (nested.has("mainMvUrls") || nested.has("photoUrl") || nested.has("atlas")) {
                            return nested
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        findPhoto(node.opt(keys.next()))?.let { return it }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        findPhoto(node.opt(i))?.let { return it }
                    }
                }
            }
            return null
        }

        private fun fileName(title: String, photoId: String): String {
            val cleaned = title
                .replace(Regex("""[\\/:*?"<>|\n\r]"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(40)
            val base = cleaned.ifBlank { photoId }
            return "$base.mp4"
        }
    }
}

data class SavedKuaishouVideo(
    val title: String,
    val uri: Uri,
)
