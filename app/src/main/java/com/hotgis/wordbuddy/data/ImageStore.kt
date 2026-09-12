package com.hotgis.wordbuddy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageCodec {
    fun fromUri(context: Context, uri: Uri, maxEdge: Int = 1024): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片")

        // First pass: decode dimensions only to compute sample size and prevent OOM on huge photos
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        var sampleSize = 1
        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (maxDim / (sampleSize * 2) >= maxEdge) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: error("无法读取图片")
        val bitmap = applyExifOrientation(bytes, decoded)
        return encodeJpeg(scale(bitmap, maxEdge))
    }

    private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun fromDrawable(context: Context, drawableName: String): ByteArray? {
        val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        if (resId == 0) return null
        val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        return encodeJpeg(scale(bitmap, 1024)).also {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int = 88): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun scale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

object AiImageClient {
    suspend fun generate(word: String, meaningHint: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val hint = meaningHint?.take(80).orEmpty()
        val prompt = buildString {
            append("simple cute educational mnemonic illustration for English word \"")
            append(word)
            append('"')
            if (hint.isNotBlank()) {
                append(", specifically meaning: ")
                append(hint)
            }
            append(", clean white background, no text, no letters, no watermark, everyday life scene")
        }
        val encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8.name())
        val seed = Random.nextInt(1, 999_999)
        val url =
            "https://image.pollinations.ai/prompt/$encoded?width=768&height=768&nologo=true&seed=$seed"
        val bytes = httpGetBytes(url)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("AI 图片损坏")
        ImageCodec.encodeJpeg(ImageCodec.scale(bitmap, 1024)).also {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun httpGetBytes(url: String): ByteArray {
        val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HotWords/0.1")
            conn.setRequestProperty("Accept", "image/*,*/*")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.readBytes()
            if (code !in 200..299) error("http $code")
            if (body.isEmpty()) error("empty image")
            return body
        } finally {
            conn.disconnect()
        }
    }
}

object MnemonicCatalog {
    private val bundled = mapOf(
        "pinky" to "mnemonic_pinky",
        "pinkie" to "mnemonic_pinky",
    )

    fun drawableNameFor(word: String): String? = bundled[word.trim().lowercase()]

    fun bytesFor(context: Context, word: String): ByteArray? {
        val name = drawableNameFor(word) ?: return null
        return ImageCodec.fromDrawable(context, name)
    }
}
