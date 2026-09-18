package com.hotgis.wordbuddy.podcast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.StateFlow

/**
 * App-side handle that starts [PodcastPlayerService] and forwards play commands.
 */
@UnstableApi
object PodcastPlayerBridge {
    val playback: StateFlow<PlaybackUiState> = PodcastPlayerService.playback

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val main = Handler(Looper.getMainLooper())

    fun ensureStarted(context: Context) {
        val app = context.applicationContext
        if (controllerFuture == null) {
            val token = SessionToken(app, ComponentName(app, PodcastPlayerService::class.java))
            controllerFuture = MediaController.Builder(app, token).buildAsync().also { future ->
                future.addListener({}, MoreExecutors.directExecutor())
            }
        }
    }

    fun play(context: Context, show: PodcastShow, episode: PodcastEpisode, queue: List<PodcastEpisode>) {
        PodcastPlayerService.markPending(show, episode)
        ensureStarted(context)
        val svc = PodcastPlayerService.instance
        if (svc != null) {
            svc.playEpisode(show, episode, queue)
            return
        }
        runCatching {
            context.applicationContext.startService(
                Intent(context.applicationContext, PodcastPlayerService::class.java),
            )
        }
        retryPlay(show, episode, queue, attempt = 0)
    }

    private fun retryPlay(
        show: PodcastShow,
        episode: PodcastEpisode,
        queue: List<PodcastEpisode>,
        attempt: Int,
    ) {
        if (attempt > 24) return
        main.postDelayed({
            val svc = PodcastPlayerService.instance
            if (svc != null) {
                svc.playEpisode(show, episode, queue)
            } else {
                retryPlay(show, episode, queue, attempt + 1)
            }
        }, 120)
    }

    fun togglePlayPause() {
        PodcastPlayerService.instance?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        PodcastPlayerService.instance?.seekTo(positionMs)
    }

    fun next() {
        PodcastPlayerService.instance?.skipToNext()
    }

    fun previous() {
        PodcastPlayerService.instance?.skipToPrevious()
    }

    fun setSleepMinutes(minutes: Int) {
        PodcastPlayerService.instance?.setSleepMinutes(minutes)
    }
}
