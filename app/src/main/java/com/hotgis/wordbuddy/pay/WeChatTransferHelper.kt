package com.hotgis.wordbuddy.pay

import android.content.Context
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.tencent.mm.opensdk.modelbiz.WXOpenBusinessView
import kotlinx.coroutines.CompletableDeferred
import java.net.URLEncoder

data class WeChatTransferLaunch(
    /** success：确认页已打开。fail：打不开。cancel：用户取消。页面打开不等于已经到账。 */
    val result: String,
)

/**
 * 商家转账要由 App 拉起微信确认收款页。微信不会主动推送待确认通知。
 * 微信 8.0.45 起支持，对应 OpenSDK 的支持版本号 0x28002d33。
 */
object WeChatTransferHelper {
    private const val MIN_WECHAT_SUPPORT = 0x28002d33

    @Volatile
    private var pending: CompletableDeferred<WeChatTransferLaunch>? = null

    fun open(
        context: Context,
        mchId: String,
        appId: String,
        packageInfo: String,
    ): CompletableDeferred<WeChatTransferLaunch> {
        pending?.cancel()
        val deferred = CompletableDeferred<WeChatTransferLaunch>()
        pending = deferred
        if (!WeChatAuth.isConfigured()) {
            deferred.completeExceptionally(IllegalStateException("微信尚未配置"))
            return deferred
        }
        val wx = WeChatAuth.api(context)
        if (!wx.isWXAppInstalled) {
            pending = null
            deferred.completeExceptionally(IllegalStateException("请先安装微信"))
            return deferred
        }
        if (wx.wxAppSupportAPI < MIN_WECHAT_SUPPORT) {
            pending = null
            deferred.completeExceptionally(IllegalStateException("请将微信升级到最新版本后再确认收款"))
            return deferred
        }
        val req = WXOpenBusinessView.Req()
        req.businessType = "requestMerchantTransfer"
        req.query = buildString {
            append("mchId=")
            append(encode(mchId))
            append("&appId=")
            append(encode(appId))
            append("&package=")
            append(encode(packageInfo))
        }
        if (!wx.sendReq(req)) {
            pending = null
            deferred.completeExceptionally(IllegalStateException("无法打开微信确认收款"))
        }
        return deferred
    }

    fun deliver(resp: WXOpenBusinessView.Resp?) {
        val deferred = pending ?: return
        pending = null
        if (resp == null || resp.businessType != "requestMerchantTransfer") {
            deferred.complete(WeChatTransferLaunch("fail"))
            return
        }
        val result = runCatching {
            val raw = resp.extMsg.orEmpty()
            if (raw.isBlank()) return@runCatching "success"
            org.json.JSONObject(raw).optString("result").ifBlank { "success" }
        }.getOrDefault("success")
        deferred.complete(WeChatTransferLaunch(result))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
