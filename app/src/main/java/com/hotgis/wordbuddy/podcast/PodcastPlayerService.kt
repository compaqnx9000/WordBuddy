package com.hotgis.wordbuddy.podcast

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hotgis.wordbuddy.MainActivity
import com.hotgis.wordbuddy.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class PodcastPlayerService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private lateinit var settingsStore: SettingsStore
    private var screenOffReceiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (settingsStore.isPodcastPlayWhenScreenOff()) return
            val exo = player ?: return
            if (exo.playWhenReady || exo.isPlaying) {
                exo.pause()
                publishPlayerState(exo)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsStore = SettingsStore(this)
        registerScreenOffReceiver()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 12_000,
                /* maxBufferMs */ 48_000,
                /* bufferForPlaybackMs */ 700,
                /* bufferForPlaybackAfterRebufferMs */ 1_800,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(PodcastAudioCache.dataSourceFactory(this)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .setCallback(PodcastSessionCallback())
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishPlayerState(exo)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publishPlayerState(exo)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                publishPlayerState(exo)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishPlayerState(exo)
            }
        })
        scope.launch {
            while (isActive) {
                player?.let { publishPlayerState(it) }
                delay(400)
            }
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffReceiverRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenOffReceiverRegistered = false
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        sleepJob?.cancel()
        unregisterScreenOffReceiver()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        if (instance === this) instance = null
        serviceJob.cancel()
        super.onDestroy()
    }

    fun playEpisode(show: PodcastShow, episode: PodcastEpisode, queue: List<PodcastEpisode>) {
        val exo = player ?: return
        if (_playback.value.episodeId == episode.id && (exo.playWhenReady || exo.isPlaying)) {
            if (!exo.isPlaying) exo.play()
            return
        }
        val items = queue.map { ep ->
            MediaItem.Builder()
                .setMediaId(ep.id)
                .setUri(ep.audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ep.title)
                        .setArtist(show.title)
                        .setAlbumTitle(show.host)
                        .setDescription(ep.summary)
                        .build(),
                )
                .build()
        }
        val index = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        exo.setMediaItems(items, index, 0L)
        exo.prepare()
        exo.playWhenReady = true
        _playback.value = _playback.value.copy(
            showId = show.id,
            episodeId = episode.id,
            title = episode.title,
            showTitle = show.title,
            isPlaying = false,
            buffering = true,
            isLive = PodcastAudioCache.isLiveUrl(episode.audioUrl),
        )
        publishPlayerState(exo)
    }

    fun togglePlayPause() {
        val exo = player ?: return
        // Live HLS often keeps isPlaying=false while audio is already coming out.
        if (exo.playWhenReady) {
            exo.pause()
        } else {
            exo.playWhenReady = true
            exo.play()
        }
        publishPlayerState(exo)
    }

    private fun publishPlayerState(exo: ExoPlayer) {
        val duration = exo.duration
        val live = exo.isCurrentMediaItemLive ||
            duration == C.TIME_UNSET ||
            duration <= 0L ||
            duration > TWO_HOURS_MS ||
            PodcastAudioCache.isLiveUrl(exo.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())
        val position = exo.currentPosition.coerceAtLeast(0L)
        val started = position > 400L
        val audible = exo.playWhenReady && (exo.isPlaying || started)
        val waiting = exo.playWhenReady && !audible
        val meta = exo.currentMediaItem?.mediaMetadata
        _playback.value = _playback.value.copy(
            episodeId = exo.currentMediaItem?.mediaId ?: _playback.value.episodeId,
            title = meta?.title?.toString()?.ifBlank { _playback.value.title }.orEmpty().ifBlank { _playback.value.title },
            showTitle = meta?.artist?.toString()?.ifBlank { _playback.value.showTitle }.orEmpty().ifBlank { _playback.value.showTitle },
            isPlaying = audible,
            buffering = waiting,
            positionMs = position,
            durationMs = if (live) 0L else duration.coerceAtLeast(0L),
            isLive = live,
        )
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skipToNext() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val exo = player ?: return
        if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem()
    }

    /** Minutes until auto-stop; 0 clears the timer. */
    fun setSleepMinutes(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _playback.value = _playback.value.copy(sleepRemainingMs = 0L, sleepMinutes = 0)
            return
        }
        val total = minutes * 60_000L
        _playback.value = _playback.value.copy(sleepMinutes = minutes, sleepRemainingMs = total)
        sleepJob = scope.launch {
            var left = total
            while (left > 0 && isActive) {
                delay(1_000)
                left -= 1_000
                _playback.value = _playback.value.copy(sleepRemainingMs = left.coerceAtLeast(0L))
            }
            if (isActive) {
                player?.pause()
                _playback.value = _playback.value.copy(
                    sleepRemainingMs = 0L,
                    sleepMinutes = 0,
                    isPlaying = false,
                )
            }
        }
    }

    private inner class PodcastSessionCallback : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SLEEP -> {
                    val minutes = args.getInt(EXTRA_SLEEP_MINUTES, 0)
                    setSleepMinutes(minutes)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val ACTION_SLEEP = "wordbuddy.podcast.SLEEP"
        const val EXTRA_SLEEP_MINUTES = "minutes"

        fun markPending(show: PodcastShow, episode: PodcastEpisode) {
            _playback.value = _playback.value.copy(
                showId = show.id,
                episodeId = episode.id,
                title = episode.title,
                showTitle = show.title,
                isPlaying = false,
                buffering = true,
                isLive = PodcastAudioCache.isLiveUrl(episode.audioUrl),
                durationMs = 0L,
            )
        }

        private const val TWO_HOURS_MS = 2L * 60 * 60 * 1000

        @Volatile
        var instance: PodcastPlayerService? = null
            private set

        private val _playback = MutableStateFlow(PlaybackUiState())
        val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()
    }
}

data class PlaybackUiState(
    val showId: String? = null,
    val episodeId: String? = null,
    val title: String = "",
    val showTitle: String = "",
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val isLive: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val sleepMinutes: Int = 0,
    val sleepRemainingMs: Long = 0L,
)
