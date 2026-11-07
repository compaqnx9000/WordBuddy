package com.hotgis.wordbuddy.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.bytedance.ads.convert.BDConvert
import com.bytedance.ads.convert.config.BDConvertConfig
import com.bytedance.ads.convert.event.ConvertReportHelper
import com.hotgis.wordbuddy.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 巨量广告转化 SDK。只在用户同意隐私政策之后、从启动页初始化一次。
 * 初始化会自动上报启动与心跳；注册、付费由业务成功回调主动上报。
 */
object OceanConvertHolder {
    private const val TAG = "OceanConvert"
    private const val PREFS = "ocean_convert"
    private const val KEY_REGISTERED = "register_reported"
    private val started = AtomicBoolean(false)

    fun init(activity: Activity) {
        if (!PrivacyConsentStore.hasAccepted(activity)) {
            Log.w(TAG, "skip init: privacy not accepted")
            return
        }
        if (!started.compareAndSet(false, true)) return
        try {
            val config = BDConvertConfig().apply {
                autoSendLaunchEvent = true
                enableOAID = true
                enableLog = BuildConfig.DEBUG
                enableIPFetch = false
            }
            BDConvert.init(activity, config, activity)
            Log.i(TAG, "BDConvert init requested")
        } catch (t: Throwable) {
            started.set(false)
            Log.e(TAG, "BDConvert init failed", t)
        }
    }

    /** 新用户注册成功后调用；同一安装只上报一次。 */
    fun reportRegister(context: Context, method: String = "mobile") {
        if (!PrivacyConsentStore.hasAccepted(context)) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REGISTERED, false)) return
        try {
            ConvertReportHelper.onEventRegister(method, true)
            prefs.edit().putBoolean(KEY_REGISTERED, true).apply()
            Log.i(TAG, "register reported method=$method")
        } catch (t: Throwable) {
            Log.e(TAG, "register report failed", t)
        }
    }

    /**
     * 支付成功后调用。
     * @param amountFen 金额，单位分
     * @param channel wechat / alipay
     */
    fun reportPurchase(
        context: Context,
        productName: String,
        productId: String,
        amountFen: Int,
        channel: String,
    ) {
        if (!PrivacyConsentStore.hasAccepted(context)) return
        if (amountFen <= 0) return
        try {
            ConvertReportHelper.onEventPurchase(
                "virtual",
                productName,
                productId,
                1,
                channel,
                "CNY",
                true,
                amountFen,
            )
            Log.i(TAG, "purchase reported id=$productId fen=$amountFen channel=$channel")
        } catch (t: Throwable) {
            Log.e(TAG, "purchase report failed", t)
        }
    }
}
