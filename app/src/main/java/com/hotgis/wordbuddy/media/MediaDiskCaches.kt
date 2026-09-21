package com.hotgis.wordbuddy.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.hotgis.wordbuddy.podcast.PodcastAudioCache
import com.hotgis.wordbuddy.podcast.PodcastPlayerBridge
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared helpers for short-video / podcast disk caches and settings clear action.
 */
@UnstableApi
object MediaDiskCaches {
    fun usedBytes(context: Context): Long {
        val app = context.applicationContext
        return PodcastAudioCache.usedBytes(app) + ShortsVideoCache.usedBytes(app)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 100) {
            "${mb.roundToInt()} MB"
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * Pause podcast playback then wipe podcast + shorts SimpleCache contents.
     * Safe to call from settings (IO thread preferred).
     */
    fun clearAll(context: Context) {
        val app = context.applicationContext
        runCatching { PodcastPlayerBridge.pause() }
        PodcastAudioCache.clear(app)
        ShortsVideoCache.clear(app)
    }
}

/**
 * On-disk video cache so swiping back to a short prefers local bytes.
 */
@UnstableApi
object ShortsVideoCache {
    private const val CACHE_DIR = "shorts_video"
    private const val MAX_BYTES = 200L * 1024 * 1024
    const val USER_AGENT = "WordBuddyShorts/1.0"

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
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache(context))
            .setUpstreamDataSourceFactory(httpFactory())
            .setFlags(
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                    CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS,
            )

    fun usedBytes(context: Context): Long =
        runCatching { simpleCache(context).cacheSpace }.getOrDefault(0L)

    @Synchronized
    fun clear(context: Context) {
        val c = cache
        if (c != null) {
            runCatching {
                c.keys.toList().forEach { key ->
                    runCatching { c.removeResource(key) }
                }
            }
            return
        }
        val dir = File(context.applicationContext.cacheDir, CACHE_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }
}
