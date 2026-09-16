package com.hotgis.wordbuddy.ads

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdLoadType
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTRewardVideoAd
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
import com.hotgis.wordbuddy.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GroMore rewarded video. Used for check-in makeup (补签); preload before showing.
 */
object RewardVideoController {
    private const val TAG = "RewardVideoAd"
    private const val FAIL_COOLDOWN_MS = 6_000L
    private const val SHOW_TIMEOUT_MS = 4_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    // Shown in the shorts test banner so real-device fill problems are visible.
    private val _lastStatus = MutableStateFlow("未请求")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    @Volatile
    private var loading = false

    @Volatile
    private var showing = false

    @Volatile
    private var lastFailAtMs = 0L

    private var cachedAd: TTRewardVideoAd? = null
    private var pendingCallbacks: Callbacks? = null
    private var shownAd: TTRewardVideoAd? = null
    @Volatile
    private var rewardGranted = false

    interface Callbacks {
        fun onShown()
        /** Fired when the SDK confirms the user earned the reward (watch complete). */
        fun onRewarded()
        fun onClosed()
        fun onFailed(reason: String)
    }

    fun preload(activity: Activity) {
        if (BuildConfig.CSJ_REWARD_CODE_ID.isBlank()) {
            Log.w(TAG, "CSJ_REWARD_CODE_ID empty — skip reward ads")
            _lastStatus.value = "未配置广告位"
            return
        }
        if (!PrivacyConsentStore.hasAccepted(activity)) {
            Log.w(TAG, "privacy not accepted — skip reward ads")
            _lastStatus.value = "未同意隐私"
            return
        }
        CsjSdkHolder.initAndStart(activity) { ok ->
            activity.runOnUiThread {
                if (!ok) {
                    Log.w(TAG, "SDK not ready — skip reward ads")
                    _lastStatus.value = "SDK未就绪"
                    return@runOnUiThread
                }
                load(activity)
            }
        }
    }

    fun hasReadyAd(): Boolean = cachedAd != null && _ready.value && !showing

    fun show(activity: Activity, callbacks: Callbacks) {
        if (activity.isFinishing || activity.isDestroyed) {
            callbacks.onFailed("activity gone")
            return
        }
        if (showing) {
            callbacks.onFailed("already showing")
            return
        }
        val ad = cachedAd
        if (ad == null) {
            Log.w(TAG, "show requested but no cached ad")
            preload(activity)
            callbacks.onFailed("not loaded")
            return
        }
        val readyNow = runCatching { ad.mediationManager?.isReady }.getOrNull()
        Log.i(TAG, "show reward slot=${BuildConfig.CSJ_REWARD_CODE_ID} isReady=$readyNow cached=${_ready.value}")
        cachedAd = null
        _ready.value = false
        showing = true
        rewardGranted = false
        shownAd = ad
        pendingCallbacks = callbacks
        try {
            ad.setRewardAdInteractionListener(interactionListener(activity, ad))
            ad.showRewardVideoAd(activity)
            mainHandler.postDelayed(showTimeout, SHOW_TIMEOUT_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "showRewardVideoAd failed", t)
            finishFailed(activity, ad, "show crashed: ${t.message}")
        }
    }

    private val showTimeout = Runnable {
        if (!showing) return@Runnable
        Log.w(TAG, "show timeout — SDK did not report onAdShow")
        finishFailed(null, shownAd, "show timeout")
    }

    private fun load(activity: Activity) {
        if (loading || showing) return
        if (cachedAd != null) return
        if (System.currentTimeMillis() - lastFailAtMs < FAIL_COOLDOWN_MS) return
        if (!CsjSdkHolder.ready) return
        if (BuildConfig.CSJ_REWARD_CODE_ID.isBlank()) return
        loading = true
        _lastStatus.value = "请求中"
        try {
            val metrics = activity.resources.displayMetrics
            val adSlot = AdSlot.Builder()
                .setCodeId(BuildConfig.CSJ_REWARD_CODE_ID)
                .setOrientation(TTAdConstant.ORIENTATION_VERTICAL)
                .setImageAcceptedSize(metrics.widthPixels, metrics.heightPixels)
                .setAdCount(1)
                .setUserID("wordbuddy")
                .setRewardName("continue")
                .setRewardAmount(1)
                .setAdLoadType(TTAdLoadType.LOAD)
                .setMediationAdSlot(
                    MediationAdSlot.Builder()
                        .setMuted(false)
                        .setBidNotify(true)
                        .setRewardName("continue")
                        .setRewardAmount(1)
                        .build(),
                )
                .build()
            Log.i(TAG, "load reward slot=${BuildConfig.CSJ_REWARD_CODE_ID}")
            val adNative: TTAdNative = TTAdSdk.getAdManager().createAdNative(activity)
            adNative.loadRewardVideoAd(
                adSlot,
                object : TTAdNative.RewardVideoAdListener {
                    override fun onError(code: Int, message: String?) {
                        loading = false
                        lastFailAtMs = System.currentTimeMillis()
                        cachedAd = null
                        _ready.value = false
                        _lastStatus.value = "失败 $code ${message?.take(40).orEmpty()}"
                        Log.w(TAG, "reward load fail: $code $message")
                    }

                    override fun onRewardVideoAdLoad(ad: TTRewardVideoAd?) {
                        Log.i(TAG, "reward loaded isReady=${runCatching { ad?.mediationManager?.isReady }.getOrNull()}")
                        if (ad != null) {
                            cachedAd = ad
                            // Some GroMore versions only fire load, not cached.
                            if (!_ready.value) {
                                _ready.value = true
                            }
                            _lastStatus.value = "已加载"
                        }
                        loading = false
                    }

                    override fun onRewardVideoCached() {
                        loading = false
                        _ready.value = cachedAd != null
                        if (_ready.value) _lastStatus.value = "已缓存"
                        Log.i(TAG, "reward cached ready=${_ready.value}")
                    }

                    override fun onRewardVideoCached(ad: TTRewardVideoAd?) {
                        loading = false
                        if (ad != null) cachedAd = ad
                        _ready.value = cachedAd != null
                        if (_ready.value) _lastStatus.value = "已缓存"
                        Log.i(TAG, "reward cached(ad) ready=${_ready.value}")
                    }
                },
            )
        } catch (t: Throwable) {
            loading = false
            lastFailAtMs = System.currentTimeMillis()
            _lastStatus.value = "异常 ${t.message?.take(30).orEmpty()}"
            Log.e(TAG, "load crashed", t)
        }
    }

    private fun interactionListener(
        activity: Activity,
        ad: TTRewardVideoAd,
    ): TTRewardVideoAd.RewardAdInteractionListener {
        return object : TTRewardVideoAd.RewardAdInteractionListener {
            override fun onAdShow() {
                Log.i(TAG, "reward shown")
                mainHandler.removeCallbacks(showTimeout)
                pendingCallbacks?.onShown()
            }

            override fun onAdVideoBarClick() {
                Log.i(TAG, "reward clicked")
            }

            override fun onAdClose() {
                Log.i(TAG, "reward closed")
                mainHandler.removeCallbacks(showTimeout)
                val cb = pendingCallbacks
                clearShowing(ad)
                cb?.onClosed()
                activity.runOnUiThread { preload(activity) }
            }

            override fun onVideoComplete() {
                Log.i(TAG, "reward video complete")
            }

            override fun onVideoError() {
                Log.w(TAG, "reward video error")
            }

            override fun onRewardVerify(
                rewardVerify: Boolean,
                rewardAmount: Int,
                rewardName: String?,
                errorCode: Int,
                errorMsg: String?,
            ) {
                Log.i(TAG, "reward verify=$rewardVerify amount=$rewardAmount name=$rewardName err=$errorCode $errorMsg")
                if (rewardVerify) notifyRewarded()
            }

            override fun onRewardArrived(isRewardValid: Boolean, rewardType: Int, extraInfo: Bundle?) {
                Log.i(TAG, "reward arrived valid=$isRewardValid type=$rewardType")
                if (isRewardValid) notifyRewarded()
            }

            override fun onSkippedVideo() {
                Log.i(TAG, "reward skipped")
            }
        }
    }

    private fun notifyRewarded() {
        if (rewardGranted) return
        rewardGranted = true
        pendingCallbacks?.onRewarded()
    }

    private fun finishFailed(activity: Activity?, ad: TTRewardVideoAd?, reason: String) {
        mainHandler.removeCallbacks(showTimeout)
        val cb = pendingCallbacks
        clearShowing(ad)
        cb?.onFailed(reason)
        activity?.let { preload(it) }
    }

    private fun clearShowing(ad: TTRewardVideoAd?) {
        showing = false
        rewardGranted = false
        pendingCallbacks = null
        shownAd = null
        destroyAd(ad)
    }

    private fun destroyAd(ad: TTRewardVideoAd?) {
        runCatching { ad?.mediationManager?.destroy() }
        if (cachedAd === ad) {
            cachedAd = null
            _ready.value = false
        }
    }
}
