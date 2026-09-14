package com.hotgis.wordbuddy.ads

import android.content.Context
import android.util.Log
import com.bytedance.sdk.openadsdk.TTAdConfig
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTCustomController
import com.bytedance.sdk.openadsdk.mediation.init.MediationConfig
import com.hotgis.wordbuddy.BuildConfig

/**
 * Holds Pangle (穿山甲) SDK init/start. Call [initAndStart] only after privacy consent.
 * All native calls are wrapped so a bad .so / 16KB-page device cannot crash the process.
 */
object CsjSdkHolder {
    private const val TAG = "CsjSdk"

    @Volatile
    private var starting = false

    @Volatile
    var ready: Boolean = false
        private set

    val isConfigured: Boolean
        get() = BuildConfig.CSJ_APP_ID.isNotBlank() &&
            BuildConfig.CSJ_SPLASH_CODE_ID.isNotBlank()

    fun initAndStart(
        context: Context,
        onReady: (success: Boolean) -> Unit,
    ) {
        if (!isConfigured) {
            Log.w(TAG, "CSJ_APP_ID / CSJ_SPLASH_CODE_ID empty — skip SDK init")
            onReady(false)
            return
        }
        try {
            if (ready || TTAdSdk.isSdkReady()) {
                ready = true
                onReady(true)
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "isSdkReady check failed", t)
            onReady(false)
            return
        }
        if (starting) {
            onReady(ready)
            return
        }
        starting = true
        val app = context.applicationContext
        try {
            val config = TTAdConfig.Builder()
                .appId(BuildConfig.CSJ_APP_ID)
                .appName("词搭子")
                .titleBarTheme(TTAdConstant.TITLE_BAR_THEME_DARK)
                .allowShowNotify(true)
                .debug(BuildConfig.DEBUG)
                // GroMore 广告位（如 104529400）必须开启聚合，否则会 40006「广告位ID不合法」
                .useMediation(true)
                .setMediationConfig(
                    MediationConfig.Builder()
                        .setOpenAdnTest(BuildConfig.DEBUG)
                        .build(),
                )
                .directDownloadNetworkType(
                    TTAdConstant.NETWORK_STATE_WIFI,
                    TTAdConstant.NETWORK_STATE_4G,
                )
                .supportMultiProcess(false)
                .customController(
                    object : TTCustomController() {
                        override fun isCanUseLocation(): Boolean = false
                        override fun isCanUsePhoneState(): Boolean = false
                        override fun isCanUseWifiState(): Boolean = true
                        override fun isCanUseWriteExternal(): Boolean = false
                        override fun alist(): Boolean = false
                        override fun isCanUseAndroidId(): Boolean = true
                    },
                )
                .build()

            TTAdSdk.init(app, config)
            TTAdSdk.start(
                object : TTAdSdk.Callback {
                    override fun success() {
                        starting = false
                        ready = true
                        runCatching {
                            Log.i(TAG, "Pangle SDK ready, version=${TTAdSdk.getAdManager().sdkVersion}")
                        }
                        onReady(true)
                    }

                    override fun fail(code: Int, msg: String?) {
                        starting = false
                        ready = false
                        Log.e(TAG, "Pangle SDK start failed: $code $msg")
                        onReady(false)
                    }
                },
            )
        } catch (t: Throwable) {
            starting = false
            ready = false
            Log.e(TAG, "Pangle SDK init/start crashed", t)
            onReady(false)
        }
    }
}
