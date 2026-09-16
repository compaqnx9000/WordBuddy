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
    val nickname: String? = null,
    val shippingName: String? = null,
    val shippingPhone: String? = null,
    val shippingDetail: String? = null,
    val gender: String? = null,
    val region: String? = null,
    val buddyId: String? = null,
    val signature: String? = null,
    val email: String? = null,
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

    fun load(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE, null)?.trim().orEmpty()
        val userId = prefs.getLong(KEY_USER_ID, 0L)
        val vocabId = prefs.getLong(KEY_VOCAB_ID, 0L)
        val avatarUrl = prefs.getString(KEY_AVATAR, null)?.trim()?.takeIf { it.isNotEmpty() }
        val level = prefs.getInt(KEY_LEVEL, 0).let(LauncherIcons::clamp)
        if (token.isEmpty() || userId <= 0L) return null
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
            networkRegion = prefs.getString(KEY_NET_REGION, null)?.trim()?.takeIf { it.isNotEmpty() },
            networkRegionDetail = prefs.getString(KEY_NET_DETAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun save(session: UserSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putLong(KEY_VOCAB_ID, session.vocabNotebookId)
            .putString(KEY_AVATAR, session.avatarUrl.orEmpty())
            .putInt(KEY_LEVEL, LauncherIcons.clamp(session.level))
            .putString(KEY_NICKNAME, session.nickname.orEmpty())
            .putString(KEY_SHIP_NAME, session.shippingName.orEmpty())
            .putString(KEY_SHIP_PHONE, session.shippingPhone.orEmpty())
            .putString(KEY_SHIP_DETAIL, session.shippingDetail.orEmpty())
            .putString(KEY_GENDER, session.gender.orEmpty())
            .putString(KEY_REGION, session.region.orEmpty())
            .putString(KEY_BUDDY_ID, session.buddyId.orEmpty())
            .putString(KEY_SIGNATURE, session.signature.orEmpty())
            .putString(KEY_EMAIL, session.email.orEmpty())
            .putString(KEY_NET_REGION, session.networkRegion.orEmpty())
            .putString(KEY_NET_DETAIL, session.networkRegionDetail.orEmpty())
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
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_SHIP_NAME = "shipping_name"
        private const val KEY_SHIP_PHONE = "shipping_phone"
        private const val KEY_SHIP_DETAIL = "shipping_detail"
        private const val KEY_GENDER = "gender"
        private const val KEY_REGION = "region"
        private const val KEY_BUDDY_ID = "buddy_id"
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_EMAIL = "email"
        private const val KEY_NET_REGION = "network_region"
        private const val KEY_NET_DETAIL = "network_region_detail"
    }
}
