package com.hotgis.wordbuddy.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import java.lang.ref.WeakReference
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdLoadType
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTDrawFeedAd
import com.bytedance.sdk.openadsdk.TTNativeAd
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
import com.bytedance.sdk.openadsdk.mediation.ad.MediationExpressRenderListener
import com.hotgis.wordbuddy.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Loads GroMore Draw feed ads and binds them into short-video pager pages.
 * Lives for the process so ads can preload before the shorts tab is opened.
 */
object DrawFeedController {
    private const val TAG = "DrawFeedAd"
    private const val REQUEST_COUNT = 3
    private const val MAX_CACHED = 12
    private const val FAIL_COOLDOWN_MS = 8_000L

    private val ads = LinkedHashMap<String, BoundDrawAd>()
    private val _readyKeys = MutableStateFlow<List<String>>(emptyList())
    val readyKeys: StateFlow<List<String>> = _readyKeys.asStateFlow()

    @Volatile
    private var loading = false

    @Volatile
    private var released = false

    @Volatile
    private var lastFailAtMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _lastStatus = MutableStateFlow("未请求")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    @Volatile
    private var retryAttempt = 0
    private var retryRunnable: Runnable? = null

    private fun setStatus(activity: Activity, text: String) {
        _lastStatus.value = text
        AdDiagStore.report(activity, AdDiagStore.DRAW, text)
    }

    fun start(activity: Activity) {
        released = false
        retryAttempt = 0
        cancelRetry()
        if (BuildConfig.CSJ_DRAW_CODE_ID.isBlank()) {
            Log.w(TAG, "CSJ_DRAW_CODE_ID empty — skip draw ads")
            setStatus(activity, "未配置广告位")
            return
        }
        if (!PrivacyConsentStore.hasAccepted(activity)) {
            Log.w(TAG, "privacy not accepted — skip draw ads")
            setStatus(activity, "未同意隐私")
            return
        }
        CsjSdkHolder.initAndStart(activity) { ok ->
            activity.runOnUiThread {
                if (!ok || released) {
                    Log.w(TAG, "SDK not ready — skip draw ads")
                    return@runOnUiThread
                }
                loadBatch(activity)
            }
        }
    }

    fun loadMore(activity: Activity) {
        if (released || loading) return
        if (ads.size >= MAX_CACHED) return
        loadBatch(activity)
    }

    fun attachTo(activity: Activity, key: String, container: FrameLayout, onReady: (Boolean) -> Unit) {
        val bound = ads[key]
        if (bound == null) {
            Log.w(TAG, "attach missing key=$key")
            onReady(false)
            return
        }
        val readyNow = runCatching { bound.ad.mediationManager?.isReady }.getOrNull()
        Log.i(TAG, "attach key=$key isReady=$readyNow")
        bound.ensureView(activity) { view ->
            if (released || view == null) {
                onReady(false)
                return@ensureView
            }
            if (view.parent !== container) {
                (view.parent as? ViewGroup)?.removeView(view)
                container.removeAllViews()
                container.clipChildren = true
                container.clipToPadding = true
                container.addView(view)
            }
            bound.bindCoverLayout(container, view)
            // Resume after layout/scale settles so TextureView can start playback.
            container.post {
                runCatching { bound.ad.mediationManager?.onResume() }
            }
            onReady(true)
        }
    }

    fun setPageActive(key: String, active: Boolean) {
        val bound = ads[key] ?: return
        val manager = bound.ad.mediationManager ?: return
        runCatching {
            if (active) {
                manager.onResume()
            } else {
                manager.onPause()
            }
        }
    }

    fun release() {
        released = true
        cancelRetry()
        ads.values.forEach { bound ->
            runCatching { bound.ad.destroy() }
        }
        ads.clear()
        _readyKeys.value = emptyList()
    }

    private fun cancelRetry() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    /** Transient no-fill is common; retry a few times with backoff while the host lives. */
    private fun scheduleRetry(activity: Activity) {
        if (released) return
        if (retryAttempt >= 4) return
        cancelRetry()
        val delayMs = longArrayOf(15_000L, 30_000L, 60_000L, 60_000L)[retryAttempt.coerceIn(0, 3)]
        retryAttempt++
        val weak = WeakReference(activity)
        val task = Runnable {
            val act = weak.get()
            if (act == null || act.isFinishing || act.isDestroyed || released) return@Runnable
            loadBatch(act)
        }
        retryRunnable = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun loadBatch(activity: Activity) {
        if (released || loading) return
        if (System.currentTimeMillis() - lastFailAtMs < FAIL_COOLDOWN_MS) return
        if (!CsjSdkHolder.ready) return
        if (BuildConfig.CSJ_DRAW_CODE_ID.isBlank()) return
        loading = true
        try {
            val metrics = activity.resources.displayMetrics
            // Draw templates are 9:16. On foldables request a phone-like frame, then scale-to-cover.
            val heightPx = metrics.heightPixels
            val widthPx = minOf(metrics.widthPixels, ((heightPx * 9f) / 16f).toInt().coerceAtLeast(1))
            val widthDp = widthPx / metrics.density
            val heightDp = heightPx / metrics.density
            val adSlot = AdSlot.Builder()
                .setCodeId(BuildConfig.CSJ_DRAW_CODE_ID)
                .setImageAcceptedSize(widthPx, heightPx)
                .setExpressViewAcceptedSize(widthDp, heightDp)
                .setAdCount(REQUEST_COUNT)
                .setOrientation(TTAdConstant.ORIENTATION_VERTICAL)
                // LOAD = realtime fill for in-feed insertion; PRELOAD often returns empty early.
                .setAdLoadType(TTAdLoadType.LOAD)
                .setMediationAdSlot(
                    MediationAdSlot.Builder()
                        .setMuted(false)
                        .setBidNotify(true)
                        // TextureView respects view scale; SurfaceView does not (breaks foldable cover).
                        .setUseSurfaceView(false)
                        .build(),
                )
                .build()
            Log.i(
                TAG,
                "load draw slot=${BuildConfig.CSJ_DRAW_CODE_ID} ${widthPx}x$heightPx " +
                    "(window ${metrics.widthPixels}x${metrics.heightPixels})",
            )
            val adNative: TTAdNative = TTAdSdk.getAdManager().createAdNative(activity)
            adNative.loadDrawFeedAd(
                adSlot,
                object : TTAdNative.DrawFeedAdListener {
                    override fun onError(code: Int, message: String?) {
                        loading = false
                        lastFailAtMs = System.currentTimeMillis()
                        Log.w(TAG, "draw load fail: $code $message")
                        setStatus(activity, "失败 $code ${message?.take(40).orEmpty()}")
                        scheduleRetry(activity)
                    }

                    override fun onDrawFeedAdLoad(adsResult: MutableList<TTDrawFeedAd>?) {
                        activity.runOnUiThread {
                            loading = false
                            if (released) {
                                adsResult?.forEach { runCatching { it.destroy() } }
                                return@runOnUiThread
                            }
                            val loaded = adsResult.orEmpty()
                            Log.i(TAG, "draw load success count=${loaded.size}")
                            if (loaded.isEmpty()) {
                                lastFailAtMs = System.currentTimeMillis()
                                setStatus(activity, "空填充")
                                scheduleRetry(activity)
                                return@runOnUiThread
                            }
                            retryAttempt = 0
                            cancelRetry()
                            setStatus(activity, "已加载 ${loaded.size} 条")
                            val keys = ArrayList<String>(loaded.size)
                            loaded.forEach { ad ->
                                // Do NOT discard on !isReady here — template Draw ads often
                                // become ready only after render(). Destroying early empties the pool.
                                val readyHint = runCatching { ad.mediationManager?.isReady }.getOrNull()
                                val express = runCatching { ad.mediationManager?.isExpress }.getOrNull()
                                Log.i(TAG, "draw item ready=$readyHint express=$express title=${ad.title}")
                                runCatching { ad.setCanInterruptVideoPlay(true) }
                                runCatching { ad.setActivityForDownloadApp(activity) }
                                val key = "draw-${System.nanoTime()}-${ad.hashCode()}"
                                ads[key] = BoundDrawAd(key, ad)
                                keys += key
                            }
                            while (ads.size > MAX_CACHED) {
                                val oldest = ads.keys.firstOrNull() ?: break
                                ads.remove(oldest)?.let { runCatching { it.ad.destroy() } }
                            }
                            if (keys.isNotEmpty()) {
                                _readyKeys.value = ads.keys.toList()
                                Log.i(TAG, "draw cache size=${ads.size} readyKeys=${_readyKeys.value.size}")
                            } else {
                                lastFailAtMs = System.currentTimeMillis()
                            }
                        }
                    }
                },
            )
        } catch (t: Throwable) {
            loading = false
            lastFailAtMs = System.currentTimeMillis()
            Log.e(TAG, "loadBatch crashed", t)
        }
    }

    private class BoundDrawAd(
        val key: String,
        val ad: TTDrawFeedAd,
    ) {
        private var view: View? = null
        private var started = false
        private var failed = false
        /** Express render size in px; 0 until known. */
        private var naturalWidthPx = 0
        private var naturalHeightPx = 0
        private var coverListener: View.OnLayoutChangeListener? = null
        private val waiters = ArrayList<(View?) -> Unit>()

        fun ensureView(activity: Activity, onReady: (View?) -> Unit) {
            view?.let {
                onReady(it)
                return
            }
            if (failed) {
                onReady(null)
                return
            }
            waiters += onReady
            if (started) return
            started = true
            try {
                val density = activity.resources.displayMetrics.density
                val express = ad.mediationManager?.isExpress == true || ad.adView == null
                if (express) {
                    ad.setExpressRenderListener(
                        object : MediationExpressRenderListener {
                            override fun onRenderSuccess(
                                view: View?,
                                width: Float,
                                height: Float,
                                isExpress: Boolean,
                            ) {
                                // Mediation reports express size in dp.
                                if (width > 0f && height > 0f) {
                                    naturalWidthPx = (width * density).toInt().coerceAtLeast(1)
                                    naturalHeightPx = (height * density).toInt().coerceAtLeast(1)
                                }
                                Log.i(
                                    TAG,
                                    "draw render ok $key expressSize=${width}x${height}dp " +
                                        "-> ${naturalWidthPx}x${naturalHeightPx}px",
                                )
                                complete(ad.adView ?: view)
                            }

                            override fun onRenderFail(view: View?, msg: String?, code: Int) {
                                Log.w(TAG, "draw render fail: $code $msg")
                                val fallback = ad.adView
                                if (fallback != null) complete(buildNativeView(activity, ad))
                                else complete(null)
                            }

                            override fun onAdClick() = Unit

                            override fun onAdShow() {
                                Log.i(TAG, "draw express shown $key")
                            }
                        },
                    )
                    ad.render()
                } else {
                    complete(buildNativeView(activity, ad))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "ensureView crashed", t)
                complete(null)
            }
        }

        fun bindCoverLayout(container: FrameLayout, adView: View) {
            coverListener?.let { container.removeOnLayoutChangeListener(it) }
            val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val oldW = oldRight - oldLeft
                val oldH = oldBottom - oldTop
                val newW = right - left
                val newH = bottom - top
                if (newW == oldW && newH == oldH) return@OnLayoutChangeListener
                applyCoverScale(container, adView)
            }
            coverListener = listener
            container.addOnLayoutChangeListener(listener)
            applyCoverScale(container, adView)
            // One deferred pass after first measure; avoid post loops that restart the player.
            if (container.width <= 0 || container.height <= 0) {
                container.post { applyCoverScale(container, adView) }
            }
        }

        private fun applyCoverScale(container: FrameLayout, adView: View) {
            val cw = container.width
            val ch = container.height
            if (cw <= 0 || ch <= 0) return

            // Phone / tall windows: let the express view fill the pager page normally.
            val wideWindow = cw.toFloat() / ch > 0.72f
            if (!wideWindow) {
                val lp = (adView.layoutParams as? FrameLayout.LayoutParams)
                    ?: FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                    lp.height != ViewGroup.LayoutParams.MATCH_PARENT ||
                    adView.scaleX != 1f ||
                    adView.scaleY != 1f
                ) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.gravity = Gravity.CENTER
                    adView.layoutParams = lp
                    adView.scaleX = 1f
                    adView.scaleY = 1f
                    adView.pivotX = 0f
                    adView.pivotY = 0f
                    adView.translationX = 0f
                    adView.translationY = 0f
                }
                return
            }

            var nw = naturalWidthPx
            var nh = naturalHeightPx
            // Foldable: Draw creatives stay portrait. Prefer a 9:16 frame and scale-to-cover.
            if (nw <= 0 || nh <= 0 || nw.toFloat() / nh.coerceAtLeast(1) > 0.72f) {
                nh = ch
                nw = ((ch * 9f) / 16f).toInt().coerceAtLeast(1)
            }

            val scale = maxOf(cw.toFloat() / nw, ch.toFloat() / nh)
            val lp = (adView.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(nw, nh)
            val already =
                lp.width == nw &&
                    lp.height == nh &&
                    kotlin.math.abs(adView.scaleX - scale) < 0.01f &&
                    kotlin.math.abs(adView.scaleY - scale) < 0.01f
            if (already) return

            lp.width = nw
            lp.height = nh
            lp.gravity = Gravity.CENTER
            adView.layoutParams = lp
            adView.translationX = 0f
            adView.translationY = 0f
            adView.pivotX = nw / 2f
            adView.pivotY = nh / 2f
            adView.scaleX = scale
            adView.scaleY = scale
            Log.i(TAG, "coverScale $key container=${cw}x$ch natural=${nw}x$nh scale=$scale")
        }

        private fun complete(result: View?) {
            if (result == null) failed = true
            view = result
            val pending = ArrayList(waiters)
            waiters.clear()
            pending.forEach { it(result) }
        }
    }
}

private fun buildNativeView(activity: Activity, ad: TTDrawFeedAd): View {
    val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val root = FrameLayout(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.BLACK)
    }
    val videoContainer = FrameLayout(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    ad.adView?.let { video ->
        (video.parent as? ViewGroup)?.removeView(video)
        videoContainer.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
    root.addView(videoContainer)

    val bottom = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(72), dp(24))
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
    }
    val title = TextView(activity).apply {
        text = ad.title?.takeIf { it.isNotBlank() } ?: "精选内容"
        setTextColor(Color.WHITE)
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
    }
    val desc = TextView(activity).apply {
        text = ad.description.orEmpty()
        setTextColor(Color.argb(220, 255, 255, 255))
        textSize = 13f
        setPadding(0, dp(4), 0, 0)
    }
    val button = TextView(activity).apply {
        text = ad.buttonText?.takeIf { it.isNotBlank() } ?: "查看详情"
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundColor(Color.parseColor("#19C6D4"))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        params.topMargin = dp(10)
        layoutParams = params
    }
    bottom.addView(title)
    if (!ad.description.isNullOrBlank()) bottom.addView(desc)
    bottom.addView(button)
    root.addView(bottom)

    val clickViews = listOf<View>(root, title, desc, button)
    val creativeViews = listOf<View>(button)
    ad.registerViewForInteraction(
        root,
        clickViews,
        creativeViews,
        object : TTNativeAd.AdInteractionListener {
            override fun onAdClicked(view: View?, ad: TTNativeAd?) = Unit
            override fun onAdCreativeClick(view: View?, ad: TTNativeAd?) = Unit
            override fun onAdShow(ad: TTNativeAd?) {
                Log.i("DrawFeedAd", "draw native shown")
            }
        },
    )
    return root
}
