package com.hotgis.wordbuddy.data

import android.content.Context

data class UserSession(
    val token: String,
    val userId: Long,
    val phone: String,
    val vocabNotebookId: Long,
    val avatarUrl: String? = null,
    val level: Int = 1,
    val nickname: String? = null,
    val shippingName: String? = null,
    val shippingPhone: String? = null,
    val shippingDetail: String? = null,
    val gender: String? = null,
    val region: String? = null,
    val buddyId: String? = null,
    val signature: String? = null,
    val email: String? = null,
    val alipayAccount: String? = null,
    val alipayName: String? = null,
    val wechatAccount: String? = null,
    /** Short display label, e.g. 北京 */
    val networkRegion: String? = null,
    /** Full operator location string */
    val networkRegionDetail: String? = null,
) {
    val displayNickname: String
        get() = nickname?.trim()?.takeIf { it.isNotEmpty() } ?: "词搭子"

    val hasShippingAddress: Boolean
        get() = !shippingName.isNullOrBlank() &&
            !shippingPhone.isNullOrBlank() &&
            !shippingDetail.isNullOrBlank()

    val shippingSummary: String?
        get() = if (!hasShippingAddress) null
        else listOfNotNull(shippingName, shippingPhone, shippingDetail).joinToString(" · ")
}

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        migrateLevelOneBasedIfNeeded()
    }

    /** Old builds stored Lv0–7. Bump once so stored levels match the 1–8 scale. */
    private fun migrateLevelOneBasedIfNeeded() {
        if (prefs.getBoolean(KEY_LEVEL_ONE_BASED, false)) return
        val editor = prefs.edit()
        if (prefs.contains(KEY_LEVEL)) {
            val raw = prefs.getInt(KEY_LEVEL, 0)
            editor.putInt(KEY_LEVEL, (raw + 1).coerceIn(MIN_LEVEL, MAX_LEVEL))
        }
        editor.putBoolean(KEY_LEVEL_ONE_BASED, true).apply()
    }

    fun load(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE, null)?.trim().orEmpty()
        val userId = prefs.getLong(KEY_USER_ID, 0L)
        val vocabId = prefs.getLong(KEY_VOCAB_ID, 0L)
        val avatarUrl = prefs.getString(KEY_AVATAR, null)?.trim()?.takeIf { it.isNotEmpty() }
        val level = prefs.getInt(KEY_LEVEL, 1).coerceIn(MIN_LEVEL, MAX_LEVEL)
        if (token.isEmpty() || userId <= 0L) return null
        // Migrate: keep last-login phone for the next logout prefill.
        if (phone.filter { it.isDigit() }.length == 11 &&
            prefs.getString(KEY_LAST_LOGIN_PHONE, null).isNullOrBlank()
        ) {
            prefs.edit().putString(KEY_LAST_LOGIN_PHONE, phone.filter { it.isDigit() }).apply()
        }
        return UserSession(
            token = token,
            userId = userId,
            phone = phone,
            vocabNotebookId = vocabId,
            avatarUrl = avatarUrl,
            level = level,
            nickname = prefs.getString(KEY_NICKNAME, null)?.trim()?.takeIf { it.isNotEmpty() },
            shippingName = prefs.getString(KEY_SHIP_NAME, null)?.trim()?.takeIf { it.isNotEmpty() },
            shippingPhone = prefs.getString(KEY_SHIP_PHONE, null)?.trim()?.takeIf { it.isNotEmpty() },
            shippingDetail = prefs.getString(KEY_SHIP_DETAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
            gender = prefs.getString(KEY_GENDER, null)?.trim()?.takeIf { it.isNotEmpty() },
            region = prefs.getString(KEY_REGION, null)?.trim()?.takeIf { it.isNotEmpty() },
            buddyId = prefs.getString(KEY_BUDDY_ID, null)?.trim()?.takeIf { it.isNotEmpty() },
            signature = prefs.getString(KEY_SIGNATURE, null)?.trim()?.takeIf { it.isNotEmpty() },
            email = prefs.getString(KEY_EMAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
            alipayAccount = prefs.getString(KEY_ALIPAY, null)?.trim()?.takeIf { it.isNotEmpty() },
            alipayName = prefs.getString(KEY_ALIPAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() },
            wechatAccount = prefs.getString(KEY_WECHAT, null)?.trim()?.takeIf { it.isNotEmpty() },
            networkRegion = prefs.getString(KEY_NET_REGION, null)?.trim()?.takeIf { it.isNotEmpty() },
            networkRegionDetail = prefs.getString(KEY_NET_DETAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun save(session: UserSession) {
        val editor = prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putLong(KEY_VOCAB_ID, session.vocabNotebookId)
            .putString(KEY_AVATAR, session.avatarUrl.orEmpty())
            .putInt(KEY_LEVEL, session.level.coerceIn(MIN_LEVEL, MAX_LEVEL))
            .putString(KEY_NICKNAME, session.nickname.orEmpty())
            .putString(KEY_SHIP_NAME, session.shippingName.orEmpty())
            .putString(KEY_SHIP_PHONE, session.shippingPhone.orEmpty())
            .putString(KEY_SHIP_DETAIL, session.shippingDetail.orEmpty())
            .putString(KEY_GENDER, session.gender.orEmpty())
            .putString(KEY_REGION, session.region.orEmpty())
            .putString(KEY_BUDDY_ID, session.buddyId.orEmpty())
            .putString(KEY_SIGNATURE, session.signature.orEmpty())
            .putString(KEY_EMAIL, session.email.orEmpty())
            .putString(KEY_ALIPAY, session.alipayAccount.orEmpty())
            .putString(KEY_ALIPAY_NAME, session.alipayName.orEmpty())
            .putString(KEY_WECHAT, session.wechatAccount.orEmpty())
            .putString(KEY_NET_REGION, session.networkRegion.orEmpty())
            .putString(KEY_NET_DETAIL, session.networkRegionDetail.orEmpty())
        val loginPhone = session.phone.filter { it.isDigit() }
        if (loginPhone.length == 11) {
            editor.putString(KEY_LAST_LOGIN_PHONE, loginPhone)
        }
        editor.apply()
    }

    /** Survives logout — used to prefill the login phone field. */
    fun lastLoginPhone(): String =
        prefs.getString(KEY_LAST_LOGIN_PHONE, null)?.trim().orEmpty()

    fun clear() {
        val lastPhone = prefs.getString(KEY_LAST_LOGIN_PHONE, null)
            ?: prefs.getString(KEY_PHONE, null)
        prefs.edit().clear().apply()
        val keep = lastPhone?.filter { it.isDigit() }.orEmpty()
        if (keep.length == 11) {
            prefs.edit().putString(KEY_LAST_LOGIN_PHONE, keep).apply()
        }
    }

    companion object {
        private const val PREFS = "hotwords_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_LAST_LOGIN_PHONE = "last_login_phone"
        private const val KEY_VOCAB_ID = "vocab_notebook_id"
        private const val KEY_AVATAR = "avatar_url"
        private const val KEY_LEVEL = "user_level"
        private const val KEY_LEVEL_ONE_BASED = "user_level_one_based"
        private const val MIN_LEVEL = 1
        private const val MAX_LEVEL = 8
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_SHIP_NAME = "shipping_name"
        private const val KEY_SHIP_PHONE = "shipping_phone"
        private const val KEY_SHIP_DETAIL = "shipping_detail"
        private const val KEY_GENDER = "gender"
        private const val KEY_REGION = "region"
        private const val KEY_BUDDY_ID = "buddy_id"
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_EMAIL = "email"
        private const val KEY_ALIPAY = "alipay_account"
        private const val KEY_ALIPAY_NAME = "alipay_name"
        private const val KEY_WECHAT = "wechat_account"
        private const val KEY_NET_REGION = "network_region"
        private const val KEY_NET_DETAIL = "network_region_detail"
    }
}
