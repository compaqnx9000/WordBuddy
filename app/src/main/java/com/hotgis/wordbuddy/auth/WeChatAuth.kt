package com.hotgis.wordbuddy.auth

import android.content.Context
import com.hotgis.wordbuddy.BuildConfig
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.CompletableDeferred

object WeChatAuth {
    const val STATE = "wordbuddy_login"

    @Volatile
    private var api: IWXAPI? = null

    @Volatile
    private var pending: CompletableDeferred<String>? = null

    fun appId(): String = BuildConfig.WECHAT_APP_ID.trim()

    fun isConfigured(): Boolean = appId().isNotEmpty()

    fun api(context: Context): IWXAPI {
        val existing = api
        if (existing != null) return existing
        val created = WXAPIFactory.createWXAPI(context.applicationContext, appId(), true)
        if (isConfigured()) created.registerApp(appId())
        api = created
        return created
    }

    fun signIn(context: Context): CompletableDeferred<String> {
        pending?.cancel()
        val deferred = CompletableDeferred<String>()
        pending = deferred
        if (!isConfigured()) {
            deferred.completeExceptionally(IllegalStateException("微信登录尚未配置"))
            return deferred
        }
        val wx = api(context)
        if (!wx.isWXAppInstalled) {
            deferred.completeExceptionally(IllegalStateException("请先安装微信"))
            return deferred
        }
        val req = SendAuth.Req()
        req.scope = "snsapi_userinfo"
        req.state = STATE
        if (!wx.sendReq(req)) {
            deferred.completeExceptionally(IllegalStateException("无法打开微信"))
        }
        return deferred
    }

    fun deliver(code: String?, error: String?) {
        val deferred = pending ?: return
        pending = null
        when {
            !error.isNullOrBlank() -> deferred.completeExceptionally(IllegalStateException(error))
            code.isNullOrBlank() -> deferred.completeExceptionally(IllegalStateException("未获得微信授权"))
            else -> deferred.complete(code)
        }
    }
}
