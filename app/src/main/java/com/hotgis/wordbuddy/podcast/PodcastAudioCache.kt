package com.hotgis.wordbuddy.podcast

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DataSpec
import java.io.File

/**
 * On-disk audio cache so idle prefetch and later playback share the same bytes.
 */
@UnstableApi
object PodcastAudioCache {
    private const val CACHE_DIR = "podcast_audio"
    private const val MAX_BYTES = 80L * 1024 * 1024
    const val USER_AGENT = "WordBuddyPodcast/1.0"

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun simpleCache(context: Context): SimpleCache {
        cache?.let { return it }
        val dir = File(context.applicationContext.cacheDir, CACHE_DIR)
        dir.mkdirs()
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    fun httpFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache(context))
            .setUpstreamDataSourceFactory(httpFactory())
            .setFlags(
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                    CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS,
            )

    fun isLiveUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains(".m3u8") ||
            u.contains("/live") ||
            u.contains("ice.") ||
            u.contains("streamguys") ||
            u.contains("musicradio.com/lbc")
    }

    /** Download the first [byteCount] bytes into cache. No-op for live streams. */
    fun prefetchHead(context: Context, url: String, byteCount: Long): Boolean {
        if (url.isBlank() || isLiveUrl(url) || byteCount <= 0L) return false
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(0)
            .setLength(byteCount)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build()
        val source = dataSourceFactory(context).createDataSource()
        return runCatching {
            CacheWriter(source, spec, /* temporaryBuffer = */ null, /* listener = */ null).cache()
            true
        }.getOrDefault(false)
    }
}
