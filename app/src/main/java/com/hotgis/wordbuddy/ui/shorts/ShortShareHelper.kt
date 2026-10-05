package com.hotgis.wordbuddy.ui.shorts

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hotgis.wordbuddy.BuildConfig
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.hotgis.wordbuddy.auth.WeChatShare
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX

object ShortShareHelper {
    fun share(activity: Activity, clip: ShortClip, inviteCode: String) {
        val page = watchPageUrl(clip, inviteCode)
        val text = buildShareText(clip, inviteCode, page)
        val hasHttp = page.startsWith("http://") || page.startsWith("https://")
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
                        0 -> shareWeChatPage(activity, clip, inviteCode, page, SendMessageToWX.Req.WXSceneSession)
                        1 -> shareWeChatPage(activity, clip, inviteCode, page, SendMessageToWX.Req.WXSceneTimeline)
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

    fun watchPageUrl(clip: ShortClip, inviteCode: String): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return Uri.parse("$base/watch").buildUpon()
            .appendQueryParameter("v", clip.videoUrl.trim())
            .appendQueryParameter("code", inviteCode.trim())
            .appendQueryParameter("title", clip.title.ifBlank { "英语短视频" })
            .build()
            .toString()
    }

    fun buildShareText(clip: ShortClip, inviteCode: String, page: String = watchPageUrl(clip, inviteCode)): String {
        val code = inviteCode.trim()
        return buildString {
            append("【词搭子】")
            append(clip.title.ifBlank { "英语短视频" })
            append('\n')
            if (clip.caption.isNotBlank()) {
                append(clip.caption.trim())
                append('\n')
            }
            if (code.isNotEmpty()) {
                append("邀请码：")
                append(code)
                append('\n')
            }
            append("打开观看并下载：")
            append('\n')
            append(page)
        }.trim()
    }

    private fun shareWeChatPage(
        activity: Activity,
        clip: ShortClip,
        inviteCode: String,
        page: String,
        scene: Int,
    ) {
        val code = inviteCode.trim()
        val description = if (code.isNotEmpty()) {
            "邀请码 $code，点开看视频并下载词搭子"
        } else {
            "点开看视频并下载词搭子"
        }
        val ok = WeChatShare.shareWebpage(
            context = activity,
            title = clip.title.ifBlank { "词搭子短视频" },
            description = description,
            webpageUrl = page,
            scene = scene,
            showPlayBadge = true,
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
