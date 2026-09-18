package com.hotgis.wordbuddy.podcast

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit

object PodcastRssFetcher {
    private const val TAG = "PodcastRss"
    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val client get() = httpClient
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<PodcastEpisode>>>()

    suspend fun loadEpisodes(show: PodcastShow, limit: Int = 12): List<PodcastEpisode> =
        withContext(Dispatchers.IO) {
            val cached = episodeCache[show.id]
            if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS &&
                cached.second.isNotEmpty()
            ) {
                return@withContext cached.second.take(limit)
            }
            val remote = show.rssUrl?.let { url ->
                runCatching { fetchAndParse(url, show.id, limit) }
                    .onFailure { Log.w(TAG, "rss fail ${show.id}: ${it.message}") }
                    .getOrNull()
            }.orEmpty()
            val result = when {
                remote.isNotEmpty() -> remote
                show.fallbackEpisodes.isNotEmpty() -> show.fallbackEpisodes
                else -> emptyList()
            }
            if (result.isNotEmpty()) {
                episodeCache[show.id] = System.currentTimeMillis() to result
            }
            result
        }

    private fun fetchAndParse(url: String, showId: String, limit: Int): List<PodcastEpisode> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WordBuddyPodcast/1.0")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.byteStream() ?: error("empty body")
            return parseRss(body, showId, limit)
        }
    }

    private fun parseRss(stream: java.io.InputStream, showId: String, limit: Int): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val out = ArrayList<PodcastEpisode>(limit)
        var event = parser.eventType
        var inItem = false
        var title: String? = null
        var summary: String? = null
        var audioUrl: String? = null
        var duration: String? = null
        var guid: String? = null

        fun flush() {
            val audio = audioUrl?.trim().orEmpty()
            val name = title?.trim().orEmpty()
            if (audio.isNotEmpty() && name.isNotEmpty() && out.size < limit) {
                out += PodcastEpisode(
                    id = "${showId}-${guid ?: audio.hashCode()}",
                    title = name,
                    audioUrl = audio,
                    summary = summary?.trim().orEmpty(),
                    durationLabel = duration?.trim().orEmpty(),
                )
            }
            title = null
            summary = null
            audioUrl = null
            duration = null
            guid = null
        }

        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase().orEmpty()
                    when {
                        name == "item" || name == "entry" -> {
                            inItem = true
                            title = null
                            summary = null
                            audioUrl = null
                            duration = null
                            guid = null
                        }
                        inItem && name == "title" -> title = parser.nextText()
                        inItem && (name == "description" || name == "summary") ->
                            summary = parser.nextText()
                        inItem && name == "guid" -> guid = parser.nextText()
                        inItem && name == "duration" -> duration = parser.nextText()
                        inItem && name == "enclosure" -> {
                            val encUrl = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (!encUrl.isNullOrBlank() &&
                                (type.contains("audio") || encUrl.contains(".mp3", true) ||
                                    encUrl.contains(".m4a", true))
                            ) {
                                audioUrl = encUrl
                            }
                        }
                        inItem && name == "content" -> {
                            // media:content
                            val contentUrl = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val medium = parser.getAttributeValue(null, "medium").orEmpty()
                            if (audioUrl.isNullOrBlank() &&
                                !contentUrl.isNullOrBlank() &&
                                (type.contains("audio") || medium == "audio" ||
                                    contentUrl.contains(".mp3", true) ||
                                    contentUrl.contains(".m4a", true))
                            ) {
                                audioUrl = contentUrl
                            }
                        }
                        inItem && name == "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                                ?: runCatching { parser.nextText() }.getOrNull()
                            val rel = parser.getAttributeValue(null, "rel").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (audioUrl.isNullOrBlank() &&
                                !href.isNullOrBlank() &&
                                (rel.contains("enclosure") || type.contains("audio") ||
                                    href.contains(".mp3", true) || href.contains(".m4a", true))
                            ) {
                                audioUrl = href
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.lowercase().orEmpty()
                    if (name == "item" || name == "entry") {
                        flush()
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return out
    }
}
