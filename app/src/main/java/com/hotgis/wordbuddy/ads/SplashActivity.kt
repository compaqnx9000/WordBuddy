package com.hotgis.wordbuddy.ads

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
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
import com.hotgis.wordbuddy.ui.settings.LegalDocuments

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
        val dialog = AlertDialog.Builder(this)
            .setTitle("服务协议和隐私政策")
            .setMessage(PRIVACY_CONSENT_MESSAGE)
            .setCancelable(false)
            .setPositiveButton("同意") { _, _ ->
                PrivacyConsentStore.setAccepted(this, true)
                startAdsOrMain()
            }
            .setNegativeButton("不同意") { _, _ ->
                finish()
            }
            .setNeutralButton("查看全文", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                showPrivacyFullText()
            }
        }
        dialog.show()
    }

    private fun showPrivacyFullText() {
        val text = TextView(this).apply {
            this.text = LegalDocuments.privacy
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this)
            .setTitle(LegalDocuments.PRIVACY_TITLE)
            .setView(scroll)
            .setPositiveButton("返回", null)
            .show()
    }

    private fun startAdsOrMain() {
        if (!CsjSdkHolder.isConfigured) {
            Log.w(TAG, "CSJ IDs not configured — skip splash ad")
            goMain()
            return
        }
        CsjSdkHolder.initAndStart(this) { ok ->
            runOnUiThread {
                if (!ok) {
                    Log.w(TAG, "SDK not ready — skip splash")
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
                // 冷启动自定义兜底：直接打穿山甲 ADN 代码位，避免瀑布流配置未拉到时超时无广告
                mediationBuilder.setMediationSplashRequestInfo(
                    object : MediationSplashRequestInfo(
                        MediationConstant.ADN_PANGLE,
                        fallbackCode,
                        BuildConfig.CSJ_APP_ID,
                        "",
                    ) {},
                )
            }

            val slotBuilder = AdSlot.Builder()
                .setCodeId(BuildConfig.CSJ_SPLASH_CODE_ID)
                .setImageAcceptedSize(widthPx, acceptHeightPx)
                .setExpressViewAcceptedSize(widthDp, heightDp)
                .setAdLoadType(TTAdLoadType.LOAD)
                .setMediationAdSlot(mediationBuilder.build())
            // 便于 logcat 看清瀑布流各 ADN 失败原因（20005 = 全部无填充）
            runCatching {
                slotBuilder.javaClass.getMethod(
                    "setExtraObject",
                    String::class.java,
                    Any::class.java,
                ).invoke(slotBuilder, "show_adn_load_error_detail", true)
            }

            val adSlot = slotBuilder.build()

            Log.i(
                TAG,
                "load splash slot=${BuildConfig.CSJ_SPLASH_CODE_ID} fallback=$fallbackCode " +
                    "size=${widthPx}x$acceptHeightPx",
            )

            val adNative: TTAdNative = TTAdSdk.getAdManager().createAdNative(this)
            adNative.loadSplashAd(adSlot, splashListener(), SPLASH_TIMEOUT_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "loadSplash crashed", t)
            goMain()
        }
    }

    private fun splashListener() = object : TTAdNative.CSJSplashAdListener {
        override fun onSplashLoadSuccess(ad: CSJSplashAd?) {
            Log.i(TAG, "splash load success")
        }

        override fun onSplashLoadFail(error: CSJAdError?) {
            val msg = "${error?.code} ${error?.msg}"
            Log.w(TAG, "splash load fail: $msg")
            AdDiagStore.report(this@SplashActivity, AdDiagStore.SPLASH, "加载失败 $msg")
            goMain()
        }

        override fun onSplashRenderSuccess(ad: CSJSplashAd?) {
            if (ad == null || finished) {
                goMain()
                return
            }
            Log.i(TAG, "splash render success — showing")
            AdDiagStore.report(this@SplashActivity, AdDiagStore.SPLASH, "展示中")
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
            AdDiagStore.report(this@SplashActivity, AdDiagStore.SPLASH, "渲染失败 $msg")
            goMain()
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
        private const val PRIVACY_CONSENT_MESSAGE =
            "欢迎使用你的词搭子。\n\n" +
                "在你点击「同意」之前，我们不会初始化广告 SDK，也不会读取设备标识。\n\n" +
                "同意之后，为实现开屏、短视频和激励视频广告，本应用会接入穿山甲（GroMore）。" +
                "仅在加载或展示一条广告时读取一次 OAID，用于广告投放、频次控制和反作弊，" +
                "不会在后台按固定周期重复采集。\n\n" +
                "我们已关闭，且不会收集：IMEI、IMSI、设备 MAC 地址、软件安装列表、精确位置、" +
                "通讯录、通话记录、日历、短信、本机号码、录音。\n\n" +
                "相机和相册只在你主动更换头像，或把视频保存到相册时才会申请，不会在首次打开时弹出。\n\n" +
                "点击「不同意」将退出应用。可先点「查看全文」阅读《隐私保护指引》。"
        // Cold start + mediation config pull often needs >3.5s on first launch.
        private const val SPLASH_TIMEOUT_MS = 8000
    }
}
