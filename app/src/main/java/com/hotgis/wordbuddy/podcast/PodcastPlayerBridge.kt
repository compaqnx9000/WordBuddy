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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shorts and podcast share one audible slot. The latest claim keeps the sound. */
enum class AudibleOwner { None, Shorts, Podcast }

object AudibleFocus {
    private val _owner = MutableStateFlow(AudibleOwner.None)
    val owner: StateFlow<AudibleOwner> = _owner.asStateFlow()

    fun claimShorts() {
        _owner.value = AudibleOwner.Shorts
    }

    fun claimPodcast() {
        _owner.value = AudibleOwner.Podcast
    }
}

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
        AudibleFocus.claimPodcast()
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
        val exo = PodcastPlayerService.instance?.playerForFocus()
        if (exo?.playWhenReady != true) AudibleFocus.claimPodcast()
        PodcastPlayerService.instance?.togglePlayPause()
    }

    fun pause() {
        PodcastPlayerService.instance?.pausePlayback()
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
