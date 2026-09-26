package com.hotgis.wordbuddy.ui.shorts

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.hotgis.wordbuddy.auth.WeChatShare
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX

object ShortShareHelper {
    fun share(activity: Activity, clip: ShortClip) {
        val text = buildShareText(clip)
        val url = clip.videoUrl.trim()
        val hasHttp = url.startsWith("http://") || url.startsWith("https://")
        val wechatInstalled = WeChatAuth.isConfigured() &&
            WeChatAuth.api(activity).isWXAppInstalled

        if (!WeChatAuth.isConfigured()) {
            Toast.makeText(activity, "微信 AppId 未配置", Toast.LENGTH_SHORT).show()
        }

        // Prefer WeChat when capability + install are ready.
        if (wechatInstalled && hasHttp) {
            AlertDialog.Builder(activity)
                .setTitle("分享到微信")
                .setItems(arrayOf("微信好友", "朋友圈", "系统分享", "复制文案")) { _, which ->
                    when (which) {
                        0 -> shareWeChatVideo(activity, clip, SendMessageToWX.Req.WXSceneSession)
                        1 -> shareWeChatVideo(activity, clip, SendMessageToWX.Req.WXSceneTimeline)
                        2 -> shareSystem(activity, text)
                        3 -> copyText(activity, text)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("分享短视频")
            .setItems(arrayOf("系统分享", "复制文案")) { _, which ->
                when (which) {
                    0 -> shareSystem(activity, text)
                    1 -> copyText(activity, text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun buildShareText(clip: ShortClip): String {
        val words = clip.relatedWords.take(20).joinToString(" · ")
        return buildString {
            append("【词搭子】")
            append(clip.title.ifBlank { "英语短视频" })
            append('\n')
            if (clip.caption.isNotBlank()) {
                append(clip.caption.trim())
                append('\n')
            }
            if (words.isNotBlank()) {
                append("关键词：")
                append(words)
                append('\n')
            }
            append("@")
            append(clip.author.ifBlank { "词搭子" })
            if (clip.videoUrl.isNotBlank()) {
                append('\n')
                append(clip.videoUrl.trim())
            }
        }.trim()
    }

    private fun shareWeChatVideo(activity: Activity, clip: ShortClip, scene: Int) {
        val ok = WeChatShare.shareVideo(
            context = activity,
            title = clip.title.ifBlank { "词搭子短视频" },
            description = clip.caption.ifBlank {
                clip.relatedWords.take(8).joinToString(" · ").ifBlank { "来自词搭子的英语短视频" }
            },
            videoUrl = clip.videoUrl.trim(),
            scene = scene,
        )
        if (!ok) {
            Toast.makeText(activity, "调起微信失败，请确认已安装微信", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSystem(activity: Activity, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "词搭子短视频")
        }
        activity.startActivity(Intent.createChooser(send, "分享短视频"))
    }

    private fun copyText(activity: Activity, text: String) {
        val cm = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("词搭子短视频", text))
        Toast.makeText(activity, "已复制分享文案", Toast.LENGTH_SHORT).show()
    }
}
