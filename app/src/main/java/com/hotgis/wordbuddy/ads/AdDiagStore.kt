package com.hotgis.wordbuddy.ads

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last-known ad slot status for on-device diagnosis (Settings → 广告诊断).
 * Persisted so a cold start still shows the previous session's error codes.
 */
object AdDiagStore {
    const val SPLASH = "splash"
    const val DRAW = "draw"
    const val REWARD = "reward"

    private const val PREFS = "wordbuddy_ads_diag"

    private val _splash = MutableStateFlow("暂无记录")
    val splash: StateFlow<String> = _splash.asStateFlow()

    private val _draw = MutableStateFlow("暂无记录")
    val draw: StateFlow<String> = _draw.asStateFlow()

    private val _reward = MutableStateFlow("暂无记录")
    val reward: StateFlow<String> = _reward.asStateFlow()

    fun report(context: Context, slot: String, text: String) {
        val stamped = "$text · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        when (slot) {
            SPLASH -> _splash.value = stamped
            DRAW -> _draw.value = stamped
            REWARD -> _reward.value = stamped
            else -> return
        }
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(slot, stamped)
                .apply()
        }
    }

    fun load(context: Context) {
        runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(SPLASH, null)?.let { _splash.value = it }
            prefs.getString(DRAW, null)?.let { _draw.value = it }
            prefs.getString(REWARD, null)?.let { _reward.value = it }
        }
    }
}
