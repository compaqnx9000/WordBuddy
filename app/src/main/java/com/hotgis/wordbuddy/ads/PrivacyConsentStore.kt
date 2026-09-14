package com.hotgis.wordbuddy.ads

import android.content.Context

/**
 * Persists whether the user has agreed to the privacy policy required before
 * starting the Pangle (穿山甲) SDK.
 */
object PrivacyConsentStore {
    private const val PREFS = "wordbuddy_privacy"
    private const val KEY_ACCEPTED = "privacy_accepted_v1"

    fun hasAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(context: Context, accepted: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, accepted)
            .apply()
    }
}
