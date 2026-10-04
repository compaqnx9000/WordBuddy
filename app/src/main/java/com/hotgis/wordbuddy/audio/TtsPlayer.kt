package com.hotgis.wordbuddy.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hotgis.wordbuddy.data.Accent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Word and sentence audio.
 *
 * Youdao dict voice first (words are MPEG-1 48 kHz). Example sentences from the same
 * endpoint are MPEG-2 24 kHz, which Redmi's MediaPlayer rejects while Samsung plays
 * them. ExoPlayer decodes both. If Youdao has no clip, Baidu sentence synthesis is
 * next. The system engine is only the last resort: Xiaomi's Chinese TTS silently
 * drops English that Samsung TTS speaks.
 */
class TtsPlayer(context: Context) {
    private val app = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playMutex = Mutex()
    private val stopGeneration = AtomicInteger()
    private var exoPlayer: ExoPlayer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var lastVoiceNoticeAt = 0L

    init {
        val preferredEngine = preferredEnglishTtsEngine()
        val listener = TextToSpeech.OnInitListener { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val engine = tts
            if (ttsReady && engine != null) {
                engine.setSpeechRate(DEFAULT_SPEECH_RATE)
                engine.setPitch(1.0f)
            } else if (!ttsReady) {
                Log.w(TAG, "System TTS init failed: $status engine=$preferredEngine")
            }
        }
        tts = if (preferredEngine != null) {
            Log.i(TAG, "Using TTS engine: $preferredEngine")
            TextToSpeech(app, listener, preferredEngine)
        } else {
            TextToSpeech(app, listener)
        }
    }

    suspend fun speak(text: String, accent: Accent, slow: Boolean = false) {
        val word = text.trim()
        if (word.isEmpty()) return
        playMutex.withLock {
            stopInternal()
            when (playOnline(word, accent, slow)) {
                OnlinePlay.Played, OnlinePlay.Stopped -> return@withLock
                OnlinePlay.Failed -> Unit
            }
            val spoken = speakWithSystemTts(
                word,
                accent,
                speechRate = if (slow) SLOW_SPEECH_RATE else DEFAULT_SPEECH_RATE,
            )
            if (!spoken) notifyVoiceUnavailable()
        }
    }

    /**
     * Speak syllable/parts with a short pause between each (for natural phonics).
     * Online clips first: Xiaomi's Chinese TTS silently skips short English fragments.
     */
    suspend fun speakSequence(
        parts: List<String>,
        accent: Accent,
        pauseMs: Long = 320L,
    ) {
        val cleaned = parts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        if (cleaned.size == 1) {
            speak(cleaned.first(), accent)
            return
        }
        playMutex.withLock {
            stopInternal()
            val generation = stopGeneration.get()
            cleaned.forEachIndexed { index, part ->
                if (stopGeneration.get() != generation) return@withLock
                if (playOnline(part, accent, slow = false) == OnlinePlay.Failed) {
                    speakWithSystemTts(part, accent)
                }
                if (index < cleaned.lastIndex) {
                    delay(pauseMs)
                }
            }
        }
    }

    fun stop() {
        stopInternal()
    }

    fun shutdown() {
        stopInternal()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun stopInternal() {
        stopGeneration.incrementAndGet()
        val player = exoPlayer
        exoPlayer = null
        if (player != null) {
            val release = {
                try {
                    player.release()
                } catch (_: Exception) {
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) release() else mainHandler.post(release)
        }
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    /** Youdao, then Baidu sentence synthesis. [OnlinePlay.Stopped] means the user interrupted. */
    private suspend fun playOnline(text: String, accent: Accent, slow: Boolean): OnlinePlay {
        val generation = stopGeneration.get()
        for (source in voiceSources(text, accent)) {
            if (stopGeneration.get() != generation) return OnlinePlay.Stopped
            val file = withContext(Dispatchers.IO) { downloadVoice(source.url, source.name) } ?: continue
            if (stopGeneration.get() != generation) {
                file.delete()
                return OnlinePlay.Stopped
            }
            try {
                playFile(file, slow)
                return OnlinePlay.Played
            } catch (error: TimeoutCancellationException) {
                if (stopGeneration.get() != generation) return OnlinePlay.Stopped
                Log.w(TAG, "${source.name} playback timed out, trying next", error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (stopGeneration.get() != generation) return OnlinePlay.Stopped
                Log.w(TAG, "${source.name} playback failed, trying next", error)
            } finally {
                file.delete()
            }
        }
        return if (stopGeneration.get() != generation) OnlinePlay.Stopped else OnlinePlay.Failed
    }

    private fun voiceSources(text: String, accent: Accent): List<VoiceSource> {
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val youdaoType = if (accent == Accent.UK) 1 else 2
        val baiduLan = if (accent == Accent.UK) "uk" else "en"
        return listOf(
            VoiceSource(
                "youdao",
                "https://dict.youdao.com/dictvoice?audio=$encoded&type=$youdaoType",
            ),
            VoiceSource(
                "baidu",
                "https://fanyi.baidu.com/gettts?lan=$baiduLan&text=$encoded&spd=3&source=web",
            ),
        )
    }

    private fun downloadVoice(url: String, label: String): File? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            connection.connect()
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            if (code !in 200..299 || contentType.contains("text/html", ignoreCase = true)) {
                connection.disconnect()
                Log.w(TAG, "$label HTTP $code type=$contentType")
                return null
            }
            val file = File(app.cacheDir, "hotwords_${UUID.randomUUID()}.mp3")
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (file.length() < 256) {
                file.delete()
                null
            } else {
                file
            }
        } catch (error: Exception) {
            Log.w(TAG, "$label download failed", error)
            null
        }
    }

    private suspend fun playFile(file: File, slow: Boolean) {
        val timeoutMs = if (slow) 45_000L else 25_000L
        withContext(Dispatchers.Main.immediate) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val player = ExoPlayer.Builder(app).build()
                    exoPlayer = player
                    val finished = AtomicBoolean(false)
                    val finish: (Throwable?) -> Unit = { error ->
                        if (finished.compareAndSet(false, true)) {
                            releasePlayer(player)
                            if (continuation.isActive) {
                                if (error == null) continuation.resume(Unit)
                                else continuation.resumeWithException(error)
                            }
                        }
                    }
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        true,
                    )
                    if (slow) player.playbackParameters = PlaybackParameters(SLOW_PLAYBACK_SPEED)
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) finish(null)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "ExoPlayer error ${error.errorCodeName}", error)
                            finish(error)
                        }
                    })
                    continuation.invokeOnCancellation { releasePlayer(player) }
                    try {
                        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        player.prepare()
                        player.play()
                    } catch (error: Exception) {
                        finish(error)
                    }
                }
            }
        }
    }

    private fun releasePlayer(player: ExoPlayer) {
        val release = {
            try {
                player.release()
            } catch (_: Exception) {
            }
            if (exoPlayer === player) exoPlayer = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) release() else mainHandler.post(release)
    }

    /** @return false when this device's engine cannot speak the text. */
    private suspend fun speakWithSystemTts(
        word: String,
        accent: Accent,
        speechRate: Float = DEFAULT_SPEECH_RATE,
    ): Boolean {
        val engine = tts
        if (engine == null || !ttsReady) {
            Log.w(TAG, "System TTS not ready")
            return false
        }
        if (!applyAccent(engine, accent)) {
            Log.w(TAG, "No English TTS voice installed")
            return false
        }
        engine.setSpeechRate(speechRate)
        val startedAt = SystemClock.elapsedRealtime()
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
        val skipped = AtomicBoolean(false)
        val utteranceId = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) started.set(true)
            }

            override fun onDone(id: String?) {
                if (id != utteranceId) return
                // Xiaomi reports done immediately and never speaks longer English.
                if (word.length > 12 && SystemClock.elapsedRealtime() - startedAt < 120) {
                    skipped.set(true)
                }
                ended.set(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) ended.set(true)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) ended.set(true)
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                if (id == utteranceId) ended.set(true)
            }
        })
        val spoken = engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (spoken == TextToSpeech.ERROR) {
            engine.setSpeechRate(DEFAULT_SPEECH_RATE)
            return false
        }
        // Do not stop the engine on a timer: OEM engines often never fire onDone,
        // and cancelling the wait used to cut the sentence off.
        val generation = stopGeneration.get()
        val deadline = startedAt + (word.length * 90L + 1_500L).coerceIn(2_500L, 30_000L)
        while (!ended.get() && SystemClock.elapsedRealtime() < deadline) {
            if (stopGeneration.get() != generation) return true
            if (started.get() && !engine.isSpeaking) break
            delay(40)
        }
        engine.setSpeechRate(DEFAULT_SPEECH_RATE)
        return !skipped.get()
    }

    private fun notifyVoiceUnavailable() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVoiceNoticeAt < 4_000) return
        lastVoiceNoticeAt = now
        mainHandler.post {
            Toast.makeText(
                app,
                "这台手机没有英文语音，请在系统设置中安装英文语音包",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun applyAccent(engine: TextToSpeech, accent: Accent): Boolean {
        val locales = if (accent == Accent.UK) {
            listOf(Locale.UK, Locale.US, Locale.ENGLISH)
        } else {
            listOf(Locale.US, Locale.UK, Locale.ENGLISH)
        }
        return locales.any { locale ->
            val result = engine.setLanguage(locale)
            result >= TextToSpeech.LANG_AVAILABLE
        }
    }

    /** Prefer Google/Samsung English engines over OEM Chinese-only defaults. */
    private fun preferredEnglishTtsEngine(): String? {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val flags = if (Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_ALL else 0
        val packages = app.packageManager
            .queryIntentServices(intent, flags)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
        if (packages.isEmpty()) return null
        val preferred = listOf(
            "com.google.android.tts",
            "com.samsung.SMT",
            "com.samsung.android.tts",
        )
        return preferred.firstOrNull { it in packages }
            ?: packages.firstOrNull { pkg ->
                pkg.contains("google", ignoreCase = true) ||
                    pkg.contains("samsung", ignoreCase = true)
            }
    }

    private data class VoiceSource(val name: String, val url: String)

    private enum class OnlinePlay { Played, Failed, Stopped }

    private companion object {
        const val TAG = "HotWordsTts"
        const val DEFAULT_SPEECH_RATE = 0.9f
        const val SLOW_SPEECH_RATE = 0.55f
        const val SLOW_PLAYBACK_SPEED = 0.55f
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
