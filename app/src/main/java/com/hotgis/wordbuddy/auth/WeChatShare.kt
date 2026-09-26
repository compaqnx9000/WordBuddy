package com.hotgis.wordbuddy.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.hotgis.wordbuddy.R
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage
import com.tencent.mm.opensdk.modelmsg.WXVideoObject
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject
import java.io.ByteArrayOutputStream

/**
 * WeChat native share (session / timeline).
 * Requires [WeChatAuth] AppId and open-platform share capability.
 */
object WeChatShare {
    fun isReady(context: Context): Boolean {
        if (!WeChatAuth.isConfigured()) return false
        return WeChatAuth.api(context).isWXAppInstalled
    }

    /**
     * Share a short-video link as a WeChat video card.
     * Falls back to webpage card if video object is rejected.
     */
    fun shareVideo(
        context: Context,
        title: String,
        description: String,
        videoUrl: String,
        scene: Int = SendMessageToWX.Req.WXSceneSession,
    ): Boolean {
        if (!isReady(context)) return false
        val url = videoUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false

        val video = WXVideoObject().apply {
            this.videoUrl = url
            videoLowBandUrl = url
        }
        val msg = WXMediaMessage(video).apply {
            this.title = title.trim().ifBlank { "词搭子短视频" }.take(200)
            this.description = description.trim().ifBlank { "来自词搭子" }.take(300)
            thumbData = thumbBytes(context)
        }
        val req = SendMessageToWX.Req().apply {
            transaction = "wb_video_${System.currentTimeMillis()}"
            message = msg
            this.scene = scene
        }
        val wx = WeChatAuth.api(context)
        if (wx.sendReq(req)) return true

        // Fallback: webpage card with the same URL.
        return shareWebpage(context, title, description, url, scene)
    }

    fun shareWebpage(
        context: Context,
        title: String,
        description: String,
        webpageUrl: String,
        scene: Int = SendMessageToWX.Req.WXSceneSession,
    ): Boolean {
        if (!isReady(context)) return false
        val url = webpageUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val webpage = WXWebpageObject().apply { this.webpageUrl = url }
        val msg = WXMediaMessage(webpage).apply {
            this.title = title.trim().ifBlank { "词搭子短视频" }.take(200)
            this.description = description.trim().take(300)
            thumbData = thumbBytes(context)
        }
        val req = SendMessageToWX.Req().apply {
            transaction = "wb_web_${System.currentTimeMillis()}"
            message = msg
            this.scene = scene
        }
        return WeChatAuth.api(context).sendReq(req)
    }

    private fun thumbBytes(context: Context): ByteArray {
        val src = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull() ?: solidThumb()
        val scaled = Bitmap.createScaledBitmap(src, 120, 120, true)
        if (scaled !== src) src.recycle()
        var quality = 80
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > 32 * 1024 && quality >= 20)
        scaled.recycle()
        return bytes
    }

    private fun solidThumb(): Bitmap {
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.parseColor("#0D2219"))
        return bmp
    }
}
