package com.hotgis.wordbuddy.podcast

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quietly warms RSS + the start of likely-next episodes while the user is idle
 * on the podcast shelf, so the first Play does not sit on a cold network.
 */
@UnstableApi
object PodcastPrefetcher {
    private const val TAG = "PodcastPrefetch"
    private const val WIFI_HEAD_BYTES = 512L * 1024
    private const val CELL_HEAD_BYTES = 256L * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val warmed = LinkedHashSet<String>()
    private val running = AtomicBoolean(false)
    private var idleJob: Job? = null

    fun startIdleWarmup(context: Context) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        idleJob = scope.launch {
            delay(1_200)
            runCatching { warmCatalog(app) }
                .onFailure { Log.w(TAG, "idle warmup: ${it.message}") }
            running.set(false)
        }
    }

    fun prefetchShow(context: Context, show: PodcastShow, episodes: List<PodcastEpisode> = emptyList()) {
        val app = context.applicationContext
        scope.launch {
            runCatching { warmShow(app, show, episodes, prioritize = true) }
                .onFailure { Log.w(TAG, "show warmup ${show.id}: ${it.message}") }
        }
    }

    fun stop() {
        idleJob?.cancel()
        idleJob = null
        running.set(false)
    }

    private suspend fun warmCatalog(context: Context) {
        val unmetered = isUnmetered(context)
        val headBytes = if (unmetered) WIFI_HEAD_BYTES else CELL_HEAD_BYTES
        val shows = if (unmetered) {
            PodcastCatalog.shows
        } else {
            // Cellular: only official / live-adjacent China sources + first two foreign.
            PodcastCatalog.shows.filter {
                it.id.startsWith("china-plus") || it.id == "cri-live" ||
                    it.id == "npr-live" || it.id == "bbc6min"
            }
        }
        for (show in shows) {
            if (PodcastPlayerService.playback.value.isPlaying) return
            warmShow(context, show, emptyList(), prioritize = false, headBytes = headBytes)
            delay(if (unmetered) 280 else 700)
        }
    }

    private suspend fun warmShow(
        context: Context,
        show: PodcastShow,
        knownEpisodes: List<PodcastEpisode>,
        prioritize: Boolean,
        headBytes: Long = if (isUnmetered(context)) WIFI_HEAD_BYTES else CELL_HEAD_BYTES,
    ) {
        val episodes = knownEpisodes.ifEmpty {
            runCatching { PodcastRssFetcher.loadEpisodes(show, limit = 4) }.getOrDefault(emptyList())
        }
        val take = if (prioritize) 2 else 1
        for (ep in episodes.take(take)) {
            if (PodcastPlayerService.playback.value.episodeId == ep.id) continue
            mutex.withLock {
                if (!warmed.add(ep.audioUrl)) return@withLock
            }
            if (PodcastAudioCache.isLiveUrl(ep.audioUrl)) {
                warmLiveHead(ep.audioUrl)
            } else {
                val ok = PodcastAudioCache.prefetchHead(context, ep.audioUrl, headBytes)
                Log.d(TAG, "prefetch ${show.id} ${if (ok) "ok" else "skip"} ${ep.title.take(40)}")
            }
        }
    }

    /** DNS + TLS + first playlist/chunk, then abort so ice/HLS does not stream forever. */
    private fun warmLiveHead(url: String) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", PodcastAudioCache.USER_AGENT)
            .header("Range", "bytes=0-2047")
            .get()
            .build()
        runCatching {
            PodcastRssFetcher.httpClient.newCall(req).execute().use { resp ->
                resp.body?.source()?.use { source ->
                    source.request(2_048)
                    val n = minOf(2_048L, source.buffer.size)
                    if (n > 0) source.skip(n)
                }
            }
        }
    }

    private fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return runCatching { !cm.isActiveNetworkMetered }.getOrDefault(false)
    }
}
