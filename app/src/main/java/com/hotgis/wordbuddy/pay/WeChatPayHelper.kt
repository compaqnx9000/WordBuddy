package com.hotgis.wordbuddy.pay

import android.content.Context
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.modelpay.PayResp
import kotlinx.coroutines.CompletableDeferred

data class WeChatPayParams(
    val appId: String,
    val partnerId: String,
    val prepayId: String,
    val packageValue: String,
    val nonceStr: String,
    val timeStamp: String,
    val sign: String,
)

data class WeChatPayResult(
    val errCode: Int,
    val errStr: String?,
) {
    val success: Boolean get() = errCode == BaseResp.ErrCode.ERR_OK
    val cancelled: Boolean get() = errCode == BaseResp.ErrCode.ERR_USER_CANCEL
}

object WeChatPayHelper {
    @Volatile
    private var pending: CompletableDeferred<WeChatPayResult>? = null

    fun isInstalled(context: Context): Boolean =
        WeChatAuth.api(context).isWXAppInstalled

    fun pay(context: Context, params: WeChatPayParams): CompletableDeferred<WeChatPayResult> {
        pending?.cancel()
        val deferred = CompletableDeferred<WeChatPayResult>()
        pending = deferred
        if (!WeChatAuth.isConfigured()) {
            deferred.completeExceptionally(IllegalStateException("微信尚未配置"))
            return deferred
        }
        val wx = WeChatAuth.api(context)
        if (!wx.isWXAppInstalled) {
            deferred.completeExceptionally(IllegalStateException("请先安装微信"))
            return deferred
        }
        val req = PayReq()
        req.appId = params.appId
        req.partnerId = params.partnerId
        req.prepayId = params.prepayId
        req.packageValue = params.packageValue.ifBlank { "Sign=WXPay" }
        req.nonceStr = params.nonceStr
        req.timeStamp = params.timeStamp
        req.sign = params.sign
        if (!wx.sendReq(req)) {
            pending = null
            deferred.completeExceptionally(IllegalStateException("无法打开微信支付"))
        }
        return deferred
    }

    fun deliver(resp: PayResp?) {
        val deferred = pending ?: return
        pending = null
        if (resp == null) {
            deferred.completeExceptionally(IllegalStateException("微信支付无结果"))
            return
        }
        deferred.complete(
            WeChatPayResult(
                errCode = resp.errCode,
                errStr = resp.errStr,
            ),
        )
    }
}
