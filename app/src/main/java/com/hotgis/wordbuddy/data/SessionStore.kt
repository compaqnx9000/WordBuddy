package com.hotgis.wordbuddy.data

import android.content.Context
import com.hotgis.wordbuddy.LauncherIcons

data class UserSession(
    val token: String,
    val userId: Long,
    val phone: String,
    val vocabNotebookId: Long,
    val avatarUrl: String? = null,
    val level: Int = 0,
)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE, null)?.trim().orEmpty()
        val userId = prefs.getLong(KEY_USER_ID, 0L)
        val vocabId = prefs.getLong(KEY_VOCAB_ID, 0L)
        val avatarUrl = prefs.getString(KEY_AVATAR, null)?.trim()?.takeIf { it.isNotEmpty() }
        val level = prefs.getInt(KEY_LEVEL, 0).let(LauncherIcons::clamp)
        if (token.isEmpty() || userId <= 0L) return null
        return UserSession(token, userId, phone, vocabId, avatarUrl, level)
    }

    fun save(session: UserSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putLong(KEY_VOCAB_ID, session.vocabNotebookId)
            .putString(KEY_AVATAR, session.avatarUrl.orEmpty())
            .putInt(KEY_LEVEL, LauncherIcons.clamp(session.level))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "hotwords_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_VOCAB_ID = "vocab_notebook_id"
        private const val KEY_AVATAR = "avatar_url"
        private const val KEY_LEVEL = "user_level"
    }
}
