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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resolves a Douyin share blob the user pasted, then saves the video into Movies/WordBuddy.
 *
 * This follows the same path used by common share-link tools:
 * 1. Follow the short link to get the aweme id.
 * 2. Open the mobile share page so Douyin sets `ttwid`.
 * 3. Read `window._ROUTER_DATA` / the public detail API for `play_addr`.
 * 4. Download the no-watermark play URL (`playwm` → `play`).
 */
class DouyinDownloader(private val context: Context) {
    fun download(shareText: String, onProgress: (done: Long, total: Long) -> Unit): SavedDouyinVideo {
        val shareUrl = extractDouyinUrl(shareText)
        val jar = MemoryCookieJar()
        val client = httpClient(jar, readTimeoutSec = 20)
        val awemeId = resolveAwemeId(client, shareUrl)
        val item = loadItem(client, awemeId)
        val title = item.optString("desc").trim().ifBlank { "抖音视频" }
        val playUrls = collectPlayUrls(item)
        if (playUrls.isEmpty()) {
            val images = item.optJSONArray("images")
            if (images != null && images.length() > 0) {
                throw IllegalStateException("这是图集，暂时只支持下载视频")
            }
            throw IllegalStateException("这条作品没有可下载的视频")
        }
        val downloader = httpClient(jar, readTimeoutSec = 60)
        val saved = saveFirstVideo(downloader, playUrls, fileName(title, awemeId), onProgress)
        return SavedDouyinVideo(title = title, uri = saved)
    }

    private fun loadItem(client: OkHttpClient, awemeId: String): JSONObject {
        var shareHtml = fetchQuiet(client, SHARE_VIDEO.format(awemeId))?.body
        parseShareItem(shareHtml)?.let { return it }

        fetchQuiet(client, WARM_PAGE.format(awemeId))
        shareHtml = fetchQuiet(client, SHARE_VIDEO.format(awemeId))?.body
        parseShareItem(shareHtml)?.let { return it }

        val detail = fetchQuiet(client, detailUrl(awemeId))
        detail?.jsonObject()?.optJSONObject("aweme_detail")?.let { return it }
        parseShareItem(detail?.body)?.let { return it }
        itemFromPlayUri(shareHtml ?: detail?.body)?.let { return it }
        throw IllegalStateException("抖音暂时没有返回视频信息，请稍后再试")
    }

    private fun collectPlayUrls(item: JSONObject): List<String> {
        val video = item.optJSONObject("video") ?: JSONObject()
        val seen = linkedSetOf<String>()
        fun add(raw: String?) {
            val url = raw?.replace("playwm", "play")?.trim().orEmpty()
            if (url.startsWith("http")) seen += url
        }
        fun addAddr(addr: JSONObject?) {
            if (addr == null) return
            val list = addr.optJSONArray("url_list")
            if (list != null) {
                for (i in 0 until list.length()) add(list.optString(i))
            }
            add(addr.optString("url"))
            val uri = addr.optString("uri")
            if (uri.startsWith("http")) {
                add(uri)
            } else if (uri.isNotBlank()) {
                PLAY_HOSTS.forEach { host ->
                    RATIOS.forEach { ratio ->
                        add("$host?video_id=${URLEncoder.encode(uri, "UTF-8")}&ratio=$ratio&line=0")
                    }
                }
            }
        }
        addAddr(video.optJSONObject("play_addr"))
        addAddr(video.optJSONObject("play_addr_h264"))
        addAddr(video.optJSONObject("download_addr"))
        val bitRates = video.optJSONArray("bit_rate")
        if (bitRates != null) {
            for (i in 0 until bitRates.length()) {
                addAddr(bitRates.optJSONObject(i)?.optJSONObject("play_addr"))
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

    private fun resolveAwemeId(client: OkHttpClient, startUrl: String): String {
        awemeIdFrom(startUrl)?.let { return it }
        val fetched = fetchQuiet(client, startUrl)
        val finalUrl = fetched?.finalUrl ?: startUrl
        return awemeIdFrom(finalUrl)
            ?: awemeIdFrom(fetched?.body.orEmpty())
            ?: throw IllegalStateException("这不是一个抖音视频链接")
    }

    private fun fetchQuiet(client: OkHttpClient, url: String): Fetched? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(pageHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                Fetched(
                    code = resp.code,
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
        val code: Int,
        val finalUrl: String,
        val body: String,
    ) {
        fun jsonObject(): JSONObject? {
            val text = body.trim()
            if (text.isEmpty() || !text.startsWith("{")) return null
            return runCatching { JSONObject(text) }.getOrNull()
        }
    }

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
        private const val WARM_PAGE = "https://m.douyin.com/share/video/%s"
        private const val SHARE_VIDEO = "https://www.iesdouyin.com/share/video/%s/"
        private val PLAY_HOSTS = listOf(
            "https://aweme.snssdk.com/aweme/v1/play/",
            "https://www.iesdouyin.com/aweme/v1/play/",
        )
        private val RATIOS = listOf("720p", "1080p")
        private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val pathIdRegex = Regex("""/(?:video|note|slides)/(\d{8,})""")
        private val queryIdRegex = Regex("""(?:modal_id|aweme_id|item_ids)=(\d{8,})""")
        private val playUriRegex = Regex("""v0\d{3}[a-z0-9]{10,}""")

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
                .add("Referer", "https://www.iesdouyin.com/")
                .build()
        }

        private fun playHeaders(): okhttp3.Headers {
            return okhttp3.Headers.Builder()
                .add("User-Agent", MOBILE_UA)
                .add("Accept", "*/*")
                .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .add("Referer", "https://www.iesdouyin.com/")
                .build()
        }

        private fun parseShareItem(html: String?): JSONObject? {
            if (html.isNullOrBlank()) return null
            val router = extractRouterData(html) ?: return null
            val info = findVideoInfo(router) ?: return null
            val items = info.optJSONArray("item_list")
            if (items != null && items.length() > 0) {
                return items.optJSONObject(0)
            }
            return info.optJSONObject("aweme_detail")
        }

        private fun itemFromPlayUri(html: String?): JSONObject? {
            val uri = html?.let { playUriRegex.find(it)?.value }.orEmpty()
            if (uri.isBlank()) return null
            val addr = JSONObject().put("uri", uri)
            return JSONObject().put("video", JSONObject().put("play_addr", addr))
        }

        private fun extractRouterData(html: String): JSONObject? {
            val marker = html.indexOf("_ROUTER_DATA")
            if (marker < 0) return null
            val start = html.indexOf('{', marker)
            if (start < 0) return null
            var depth = 0
            for (index in start until html.length) {
                when (html[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return runCatching {
                                JSONObject(html.substring(start, index + 1))
                            }.getOrNull()
                        }
                    }
                }
            }
            return null
        }

        private fun findVideoInfo(node: Any?): JSONObject? {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("videoInfoRes")?.let { return it }
                    if (node.has("item_list") || node.has("aweme_detail")) return node
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        findVideoInfo(node.opt(keys.next()))?.let { return it }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        findVideoInfo(node.opt(i))?.let { return it }
                    }
                }
            }
            return null
        }

        private fun extractDouyinUrl(text: String): String {
            val url = urlRegex.findAll(text)
                .map { it.value.trimEnd('.', ',', '，', '。', '!', '！', ')', '）') }
                .firstOrNull { isDouyinHost(it) }
            return url ?: throw IllegalStateException("没有找到抖音链接，请粘贴完整分享文案")
        }

        private fun isDouyinHost(url: String): Boolean {
            val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
            return host == "douyin.com" ||
                host.endsWith(".douyin.com") ||
                host == "iesdouyin.com" ||
                host.endsWith(".iesdouyin.com")
        }

        private fun awemeIdFrom(text: String): String? {
            return pathIdRegex.find(text)?.groupValues?.getOrNull(1)
                ?: queryIdRegex.find(text)?.groupValues?.getOrNull(1)
        }

        private fun detailUrl(awemeId: String): String {
            return "https://www.douyin.com/aweme/v1/web/aweme/detail/" +
                "?aweme_id=$awemeId&aid=6383&channel=channel_pc_web"
        }

        private fun fileName(title: String, awemeId: String): String {
            val cleaned = title
                .replace(Regex("""[\\/:*?"<>|\n\r]"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(40)
            val base = cleaned.ifBlank { awemeId }
            return "$base.mp4"
        }
    }
}

data class SavedDouyinVideo(
    val title: String,
    val uri: Uri,
)
