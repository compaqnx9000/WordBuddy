package com.hotgis.wordbuddy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RememberedAccount(
    val session: UserSession,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    val userId: Long get() = session.userId
    val phone: String get() = session.phone
    val displayName: String get() = session.displayNickname
    val avatarUrl: String? get() = session.avatarUrl
}

/**
 * WeChat-style multi-account memory on this device.
 * Different phones keep their own tokens; same phone re-login still rotates server session.
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<RememberedAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    parse(obj)?.let { add(it) }
                }
            }.sortedByDescending { it.lastUsedAt }
        }.getOrDefault(emptyList())
    }

    fun upsert(session: UserSession) {
        if (session.userId <= 0L || session.token.isBlank()) return
        val now = System.currentTimeMillis()
        val next = list()
            .filterNot { it.userId == session.userId }
            .toMutableList()
        next.add(0, RememberedAccount(session = session, lastUsedAt = now))
        save(next.take(MAX_ACCOUNTS))
    }

    fun remove(userId: Long) {
        save(list().filterNot { it.userId == userId })
    }

    fun clearToken(userId: Long) {
        val next = list().map { acc ->
            if (acc.userId == userId) {
                acc.copy(session = acc.session.copy(token = ""))
            } else {
                acc
            }
        }.filter { it.session.token.isNotBlank() || it.phone.isNotBlank() }
        // Drop entries with empty token — they can't quick-switch; user adds via login.
        save(next.filter { it.session.token.isNotBlank() })
    }

    fun find(userId: Long): RememberedAccount? = list().firstOrNull { it.userId == userId }

    private fun save(accounts: List<RememberedAccount>) {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject()
                    .put("lastUsedAt", acc.lastUsedAt)
                    .put("token", acc.session.token)
                    .put("userId", acc.session.userId)
                    .put("phone", acc.session.phone)
                    .put("vocabNotebookId", acc.session.vocabNotebookId)
                    .put("avatarUrl", acc.session.avatarUrl.orEmpty())
                    .put("level", acc.session.level)
                    .put("nickname", acc.session.nickname.orEmpty())
                    .put("shippingName", acc.session.shippingName.orEmpty())
                    .put("shippingPhone", acc.session.shippingPhone.orEmpty())
                    .put("shippingDetail", acc.session.shippingDetail.orEmpty())
                    .put("gender", acc.session.gender.orEmpty())
                    .put("region", acc.session.region.orEmpty())
                    .put("buddyId", acc.session.buddyId.orEmpty())
                    .put("signature", acc.session.signature.orEmpty())
                    .put("email", acc.session.email.orEmpty())
                    .put("alipayAccount", acc.session.alipayAccount.orEmpty())
                    .put("alipayName", acc.session.alipayName.orEmpty())
                    .put("wechatAccount", acc.session.wechatAccount.orEmpty())
                    .put("networkRegion", acc.session.networkRegion.orEmpty())
                    .put("networkRegionDetail", acc.session.networkRegionDetail.orEmpty()),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    private fun parse(obj: JSONObject): RememberedAccount? {
        val token = obj.optString("token").trim()
        val userId = obj.optLong("userId", 0L)
        val phone = obj.optString("phone").trim()
        if (token.isEmpty() || userId <= 0L) return null
        return RememberedAccount(
            lastUsedAt = obj.optLong("lastUsedAt", 0L),
            session = UserSession(
                token = token,
                userId = userId,
                phone = phone,
                vocabNotebookId = obj.optLong("vocabNotebookId", 0L),
                avatarUrl = obj.optString("avatarUrl").trim().takeIf { it.isNotEmpty() },
                level = obj.optInt("level", 0),
                nickname = obj.optString("nickname").trim().takeIf { it.isNotEmpty() },
                shippingName = obj.optString("shippingName").trim().takeIf { it.isNotEmpty() },
                shippingPhone = obj.optString("shippingPhone").trim().takeIf { it.isNotEmpty() },
                shippingDetail = obj.optString("shippingDetail").trim().takeIf { it.isNotEmpty() },
                gender = obj.optString("gender").trim().takeIf { it.isNotEmpty() },
                region = obj.optString("region").trim().takeIf { it.isNotEmpty() },
                buddyId = obj.optString("buddyId").trim().takeIf { it.isNotEmpty() },
                signature = obj.optString("signature").trim().takeIf { it.isNotEmpty() },
                email = obj.optString("email").trim().takeIf { it.isNotEmpty() },
                alipayAccount = obj.optString("alipayAccount").trim().takeIf { it.isNotEmpty() },
                alipayName = obj.optString("alipayName").trim().takeIf { it.isNotEmpty() },
                wechatAccount = obj.optString("wechatAccount").trim().takeIf { it.isNotEmpty() },
                networkRegion = obj.optString("networkRegion").trim().takeIf { it.isNotEmpty() },
                networkRegionDetail = obj.optString("networkRegionDetail").trim().takeIf { it.isNotEmpty() },
            ),
        )
    }

    companion object {
        private const val PREFS = "hotwords_accounts"
        private const val KEY_ACCOUNTS = "accounts_json"
        private const val MAX_ACCOUNTS = 8

        fun maskPhone(phone: String): String {
            val p = phone.filter { it.isDigit() }
            if (p.length < 7) return phone.ifBlank { "—" }
            return p.take(3) + "****" + p.takeLast(4)
        }
    }
}
