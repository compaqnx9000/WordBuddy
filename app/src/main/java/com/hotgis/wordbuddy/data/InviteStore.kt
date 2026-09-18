package com.hotgis.wordbuddy.data

import android.content.Context
import com.hotgis.wordbuddy.BuildConfig

/** Pending invite code for new-user registration (from share link / clipboard / manual). */
class InviteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun peek(): String? =
        prefs.getString(KEY_CODE, null)?.trim()?.lowercase()?.takeIf { it.length in 4..16 }

    fun save(code: String?) {
        val normalized = code?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty()
        if (normalized.length in 4..16) {
            prefs.edit().putString(KEY_CODE, normalized).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_CODE).apply()
    }

    companion object {
        private const val PREFS = "hotwords_invite"
        private const val KEY_CODE = "pending_invite"

        fun inviteUrl(buddyId: String): String {
            val base = BuildConfig.API_BASE_URL.trimEnd('/')
            return "$base/i/${buddyId.trim()}"
        }

        fun shareText(buddyId: String): String {
            val id = buddyId.trim()
            return "我在用「词搭子」背单词，邀请你一起来！\n邀请码：$id\n打开链接注册：${inviteUrl(id)}"
        }
    }
}
