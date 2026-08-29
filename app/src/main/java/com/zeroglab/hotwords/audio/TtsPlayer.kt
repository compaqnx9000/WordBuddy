package com.zeroglab.hotwords.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.zeroglab.hotwords.data.Accent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Free word audio: Youdao dict voice (no API key) with Android TTS as fallback.
 */
class TtsPlayer(context: Context) {
    private val app = context.applicationContext
    private val playMutex = Mutex()
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    init {
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val engine = tts
            if (ttsReady && engine != null) {
                engine.setSpeechRate(0.9f)
                engine.setPitch(1.0f)
            } else if (!ttsReady) {
                Log.w(TAG, "System TTS init failed: $status")
            }
        }
    }

    suspend fun speak(text: String, accent: Accent, slow: Boolean = false) {
        val word = text.trim()
        if (word.isEmpty()) return
        playMutex.withLock {
            stopInternal()
            if (!slow) {
                val file = withContext(Dispatchers.IO) { downloadDictVoice(word, accent) }
                if (file != null) {
                    try {
                        playFile(file)
                        return@withLock
                    } catch (error: Exception) {
                        Log.w(TAG, "Online audio playback failed, falling back to TTS", error)
                    } finally {
                        file.delete()
                    }
                }
            }
            speakWithSystemTts(
                word,
                accent,
                speechRate = if (slow) SLOW_SPEECH_RATE else DEFAULT_SPEECH_RATE,
            )
        }
    }

    /** Speak syllable/parts with a short pause between each (for natural phonics). */
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
            cleaned.forEachIndexed { index, part ->
                // Syllable pieces are more reliable via system TTS than dict clips.
                speakWithSystemTts(part, accent)
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
        try {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun downloadDictVoice(word: String, accent: Accent): File? {
        val encoded = URLEncoder.encode(word, StandardCharsets.UTF_8.name())
        val type = if (accent == Accent.UK) 1 else 2
        val url = "https://dict.youdao.com/dictvoice?audio=$encoded&type=$type"
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
                Log.w(TAG, "Dict voice HTTP $code type=$contentType")
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
            Log.w(TAG, "Dict voice download failed", error)
            null
        }
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        mediaPlayer = player
        val finished = AtomicBoolean(false)
        val finish = {
            if (finished.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(Unit)
            }
            releasePlayer(player)
        }
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        player.setOnCompletionListener { finish() }
        player.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
            finish()
            true
        }
        continuation.invokeOnCancellation { releasePlayer(player) }
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.start()
        } catch (error: Exception) {
            Log.w(TAG, "MediaPlayer start failed", error)
            finish()
        }
    }

    private fun releasePlayer(player: MediaPlayer) {
        try {
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) player.stop()
            player.reset()
            player.release()
        } catch (_: Exception) {
        }
        if (mediaPlayer === player) mediaPlayer = null
    }

    private suspend fun speakWithSystemTts(
        word: String,
        accent: Accent,
        speechRate: Float = DEFAULT_SPEECH_RATE,
    ) {
        val engine = tts
        if (engine == null || !ttsReady) {
            Log.w(TAG, "System TTS not ready")
            return
        }
        if (!applyAccent(engine, accent)) {
            Log.w(TAG, "No English TTS voice installed")
            return
        }
        engine.setSpeechRate(speechRate)
        suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            val finished = AtomicBoolean(false)
            val finish = {
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId) finish()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) finish()
                }
                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) finish()
                }
            })
            continuation.invokeOnCancellation { engine.stop() }
            val spoken = engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (spoken == TextToSpeech.ERROR) finish()
        }
        engine.setSpeechRate(DEFAULT_SPEECH_RATE)
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

    private companion object {
        const val TAG = "HotWordsTts"
        const val DEFAULT_SPEECH_RATE = 0.9f
        const val SLOW_SPEECH_RATE = 0.55f
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
