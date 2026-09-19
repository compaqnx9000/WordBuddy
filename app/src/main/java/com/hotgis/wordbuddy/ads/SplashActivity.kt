package com.hotgis.wordbuddy.ads

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.CSJAdError
import com.bytedance.sdk.openadsdk.CSJSplashAd
import com.bytedance.sdk.openadsdk.TTAdLoadType
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.mediation.MediationConstant
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
import com.bytedance.sdk.openadsdk.mediation.ad.MediationSplashRequestInfo
import com.hotgis.wordbuddy.BuildConfig
import com.hotgis.wordbuddy.MainActivity
import com.hotgis.wordbuddy.R

/**
 * Cold-start entry: privacy consent → Pangle init → splash ad → [MainActivity].
 * Any ad failure falls through to MainActivity (never blocks cold start).
 */
class SplashActivity : ComponentActivity() {
    private lateinit var splashContainer: FrameLayout
    private var finished = false
    private var paused = false
    private var needGoMainOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContentView(R.layout.activity_splash)
            splashContainer = findViewById(R.id.splash_container)
        } catch (t: Throwable) {
            Log.e(TAG, "splash layout failed", t)
            goMain()
            return
        }

        // Debug builds skip the dialog so emulator/CI can exercise the ad path.
        if (BuildConfig.DEBUG && !PrivacyConsentStore.hasAccepted(this)) {
            PrivacyConsentStore.setAccepted(this, true)
        }

        if (!PrivacyConsentStore.hasAccepted(this)) {
            showPrivacyDialog()
        } else {
            startAdsOrMain()
        }
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("服务协议和隐私政策")
            .setMessage(
                "欢迎使用词搭子！\n\n" +
                    "我们非常重视你的个人信息和隐私保护。为保障产品功能与服务正常运行，" +
                    "请你在使用前仔细阅读并充分理解《软件许可及服务协议》《隐私保护指引摘要》" +
                    "《隐私保护指引》。\n\n" +
                    "你可在应用内「我的 → 关于词搭子」随时查看上述全文。\n\n" +
                    "点击「同意」即表示你已阅读并同意上述内容；点击「不同意」将退出应用。",
            )
            .setCancelable(false)
            .setPositiveButton("同意") { _, _ ->
                PrivacyConsentStore.setAccepted(this, true)
                startAdsOrMain()
            }
            .setNegativeButton("不同意") { _, _ ->
                finish()
            }
            .show()
    }

    private fun startAdsOrMain() {
        if (!CsjSdkHolder.isConfigured) {
            Log.w(TAG, "CSJ IDs not configured — skip splash ad")
            toastFail("未配置广告位")
            goMain()
            return
        }
        CsjSdkHolder.initAndStart(this) { ok ->
            runOnUiThread {
                if (!ok) {
                    Log.w(TAG, "SDK not ready — skip splash")
                    toastFail("SDK初始化失败")
                    goMain()
                } else {
                    loadSplash()
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun loadSplash() {
        if (finished) return
        try {
            val metrics = resources.displayMetrics
            val widthPx = metrics.widthPixels
            val heightPx = metrics.heightPixels
            // GroMore：开屏高度建议 ≥ 屏幕 75%
            val acceptHeightPx = (heightPx * 0.85f).toInt().coerceAtLeast((heightPx * 0.75f).toInt())
            val widthDp = pxToDp(widthPx, metrics)
            val heightDp = pxToDp(acceptHeightPx, metrics)

            val mediationBuilder = MediationAdSlot.Builder()
                .setBidNotify(true)
            val fallbackCode = BuildConfig.CSJ_SPLASH_FALLBACK_CODE_ID
            if (fallbackCode.isNotBlank()) {
                // 冷启动自定义兜底：直接打穿山甲代码位，避免瀑布流配置未拉到时超时无广告
                mediationBuilder.setMediationSplashRequestInfo(
                    object : MediationSplashRequestInfo(
                        MediationConstant.ADN_PANGLE,
                        fallbackCode,
                        BuildConfig.CSJ_APP_ID,
                        "",
                    ) {},
                )
            }

            val adSlot = AdSlot.Builder()
                .setCodeId(BuildConfig.CSJ_SPLASH_CODE_ID)
                .setImageAcceptedSize(widthPx, acceptHeightPx)
                .setExpressViewAcceptedSize(widthDp, heightDp)
                .setAdLoadType(TTAdLoadType.LOAD)
                .setMediationAdSlot(mediationBuilder.build())
                .build()

            Log.i(
                TAG,
                "load splash slot=${BuildConfig.CSJ_SPLASH_CODE_ID} fallback=$fallbackCode " +
                    "size=${widthPx}x$acceptHeightPx",
            )

            val adNative: TTAdNative = TTAdSdk.getAdManager().createAdNative(this)
            adNative.loadSplashAd(
                adSlot,
                object : TTAdNative.CSJSplashAdListener {
                    override fun onSplashLoadSuccess(ad: CSJSplashAd?) {
                        Log.i(TAG, "splash load success")
                    }

                    override fun onSplashLoadFail(error: CSJAdError?) {
                        val msg = "${error?.code} ${error?.msg}"
                        Log.w(TAG, "splash load fail: $msg")
                        toastFail("开屏失败:$msg")
                        goMain()
                    }

                    override fun onSplashRenderSuccess(ad: CSJSplashAd?) {
                        if (ad == null || finished) {
                            goMain()
                            return
                        }
                        Log.i(TAG, "splash render success — showing")
                        ad.setSplashAdListener(
                            object : CSJSplashAd.SplashAdListener {
                                override fun onSplashAdShow(splashAd: CSJSplashAd?) = Unit

                                override fun onSplashAdClick(splashAd: CSJSplashAd?) = Unit

                                override fun onSplashAdClose(
                                    splashAd: CSJSplashAd?,
                                    closeType: Int,
                                ) {
                                    goMain()
                                }
                            },
                        )
                        splashContainer.visibility = View.VISIBLE
                        findViewById<View>(R.id.splash_brand)?.visibility = View.GONE
                        ad.showSplashView(splashContainer)
                    }

                    override fun onSplashRenderFail(ad: CSJSplashAd?, error: CSJAdError?) {
                        val msg = "${error?.code} ${error?.msg}"
                        Log.w(TAG, "splash render fail: $msg")
                        toastFail("开屏渲染失败:$msg")
                        goMain()
                    }
                },
                SPLASH_TIMEOUT_MS,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "loadSplash crashed", t)
            toastFail("开屏异常:${t.javaClass.simpleName}")
            goMain()
        }
    }

    private fun toastFail(msg: String) {
        // Temporary: helps diagnose fill issues on device without adb.
        runCatching {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun pxToDp(px: Int, metrics: DisplayMetrics): Float =
        px / metrics.density

    private fun goMain() {
        if (finished) return
        if (paused) {
            needGoMainOnResume = true
            return
        }
        finished = true
        runCatching { splashContainer.removeAllViews() }
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
        if (needGoMainOnResume) {
            needGoMainOnResume = false
            goMain()
        }
    }

    companion object {
        private const val TAG = "SplashActivity"
        // Cold start + mediation config pull often needs >3.5s on first launch.
        private const val SPLASH_TIMEOUT_MS = 8000
    }
}
