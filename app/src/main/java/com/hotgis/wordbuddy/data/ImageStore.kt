package com.hotgis.wordbuddy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
