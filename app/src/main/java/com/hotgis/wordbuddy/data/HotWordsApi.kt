package com.hotgis.wordbuddy.data

import android.os.Build
import com.hotgis.wordbuddy.BuildConfig
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuthResult(
    val session: UserSession?,
    val isNewUser: Boolean = false,
)

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkPath: String,
    val force: Boolean = false,
    val notes: String = "",
) {
    val hasUpdate: Boolean get() = versionCode > BuildConfig.VERSION_CODE
}

data class WordPage(
    val items: List<VocabEntry>,
    val total: Int,
    val nextCursor: String?,
    val fromIndex: Int = 0,
)

data class WordHead(
    val id: Long,
    val text: String,
    val isPhrase: Boolean = false,
    val ipaUk: String? = null,
    val ipaUs: String? = null,
    val sortOrder: Int = 0,
) {
    fun toStub(notebookId: Long): VocabEntry = VocabEntry(
        id = id,
        notebookId = notebookId,
        text = text,
        isPhrase = isPhrase,
        ipaUk = ipaUk,
        ipaUs = ipaUs,
        definitions = emptyList(),
        sortOrder = sortOrder,
    )
}

data class WordHeads(
    val items: List<WordHead>,
    val total: Int,
    val letterIndex: Map<Char, Int>,
)

class ApiException(
    message: String,
    val code: String? = null,
    val httpCode: Int = 0,
) : Exception(message) {
    val isSessionReplaced: Boolean
        get() = code == SESSION_REPLACED_CODE

    companion object {
        const val SESSION_REPLACED_CODE = "SESSION_REPLACED"
    }
}

/** Emits when the server invalidates this device because the account logged in elsewhere. */
object AuthSessionEvents {
    private val _replaced = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val replaced: SharedFlow<String> = _replaced.asSharedFlow()

    fun notifyReplaced(message: String?) {
        _replaced.tryEmit(
            message?.trim()?.takeIf { it.isNotEmpty() } ?: "账号已在其他设备登录",
        )
    }
}

class HotWordsApi {
    private val bases = linkedSetOf(
        BuildConfig.API_BASE_URL.trimEnd('/'),
        BuildConfig.API_FALLBACK_URL.trimEnd('/'),
    ).filter { it.isNotBlank() }

    @Volatile
    private var baseUrl = bases.first()

    suspend fun sendCode(phone: String): String? = withContext(Dispatchers.IO) {
        val root = request("POST", "/auth/send-code", auth = null, body = JSONObject().put("phone", phone))
        if (root.has("debugCode") && !root.isNull("debugCode")) root.optString("debugCode") else null
    }

    suspend fun checkAppUpdate(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val root = request("GET", "/app/version.json", auth = null)
        val path = root.optString("apkPath").ifBlank { "/app/HotWords-release.apk" }
        AppUpdateInfo(
            versionCode = root.optInt("versionCode"),
            versionName = root.optString("versionName").ifBlank { "未知" },
            apkPath = if (path.startsWith("http")) path else "$baseUrl$path",
            force = root.optBoolean("force"),
            notes = root.optString("notes").trim(),
        )
    }

    suspend fun login(phone: String, code: String): AuthResult = withContext(Dispatchers.IO) {
        parseAuth(
            request(
                "POST",
                "/auth/login",
                auth = null,
                body = JSONObject().put("phone", phone).put("code", code),
            ),
        )
    }

    suspend fun loginWithPassword(phone: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            parseAuth(
                request(
                    "POST",
                    "/auth/login",
                    auth = null,
                    body = JSONObject().put("phone", phone).put("password", password),
                ),
            )
        }

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String) =
        withContext(Dispatchers.IO) {
            request(
                "POST",
                "/auth/change-password",
                auth = token,
                body = JSONObject()
                    .put("oldPassword", oldPassword)
                    .put("newPassword", newPassword),
            )
        }

    suspend fun verifyPassword(token: String, password: String) =
        withContext(Dispatchers.IO) {
            request(
                "POST",
                "/auth/verify-password",
                auth = token,
                body = JSONObject().put("password", password),
            )
        }

    suspend fun changePhone(
        token: String,
        password: String,
        newPhone: String,
        code: String,
    ): UserSession = withContext(Dispatchers.IO) {
        val root = request(
            "POST",
            "/auth/change-phone",
            auth = token,
            body = JSONObject()
                .put("password", password)
                .put("newPhone", newPhone)
                .put("code", code),
        )
        val nextToken = root.optString("token").ifBlank { token }
        val user = root.getJSONObject("user")
        parseUserSession(token = nextToken, root = root, user = user)
    }

    suspend fun register(phone: String, code: String, password: String, inviteCode: String? = null): AuthResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("phone", phone)
                .put("code", code)
                .put("password", password)
            val invite = inviteCode?.trim()?.lowercase().orEmpty()
            if (invite.isNotEmpty()) body.put("inviteCode", invite)
            parseAuth(
                request(
                    "POST",
                    "/auth/register",
                    auth = null,
                    body = body,
                ),
            )
        }

    private fun parseAuth(root: JSONObject): AuthResult {
        val isNewUser = root.optBoolean("isNewUser")
        if (!root.has("token") || root.isNull("token")) {
            return AuthResult(session = null, isNewUser = isNewUser)
        }
        val user = root.getJSONObject("user")
        return AuthResult(
            session = parseUserSession(token = root.getString("token"), root = root, user = user),
            isNewUser = isNewUser,
        )
    }

    suspend fun uploadAvatar(token: String, jpegBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val encoded = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
            val root = request(
                "POST",
                "/me/avatar",
                auth = token,
                body = JSONObject().put("imageBase64", encoded),
            )
            root.optString("avatarUrl").ifBlank { error("上传失败") }
        }

    suspend fun fetchMe(token: String): UserSession? = withContext(Dispatchers.IO) {
        val root = request("GET", "/me", auth = token)
        val user = root.optJSONObject("user") ?: return@withContext null
        parseUserSession(token = token, root = root, user = user)
    }

    data class InviteInfo(
        val canBindInvite: Boolean,
        val invitedByBuddyId: String?,
        val inviteeReward: Int,
        val inviterReward: Int,
        val invitedCount: Int,
    )

    data class BindInviteResult(
        val message: String,
        val inviteeReward: Int,
        val totalPoints: Int,
        val invitedByBuddyId: String?,
    )

    suspend fun fetchInviteInfo(token: String): InviteInfo = withContext(Dispatchers.IO) {
        val root = request("GET", "/me/invite", auth = token)
        val rewards = root.optJSONObject("rewards")
        InviteInfo(
            canBindInvite = root.optBoolean("canBindInvite", true),
            invitedByBuddyId = root.optString("invitedByBuddyId").trim().takeIf { it.isNotEmpty() },
            inviteeReward = rewards?.optInt("inviteePoints", 10) ?: 10,
            inviterReward = rewards?.optInt("inviterPoints", 20) ?: 20,
            invitedCount = root.optInt("invitedCount", 0),
        )
    }

    suspend fun bindInviteCode(token: String, inviteCode: String): BindInviteResult =
        withContext(Dispatchers.IO) {
            val root = request(
                "POST",
                "/me/invite/bind",
                auth = token,
                body = JSONObject().put("inviteCode", inviteCode.trim()),
            )
            BindInviteResult(
                message = root.optString("message").ifBlank { "邀请码已填写" },
                inviteeReward = root.optInt("inviteeReward", 10),
                totalPoints = root.optInt("totalPoints", 0),
                invitedByBuddyId = root.optString("invitedByBuddyId").trim().takeIf { it.isNotEmpty() },
            )
        }

    suspend fun updateProfile(
        token: String,
        nickname: String? = null,
        shippingName: String? = null,
        shippingPhone: String? = null,
        shippingDetail: String? = null,
        gender: String? = null,
        region: String? = null,
        signature: String? = null,
        email: String? = null,
        alipayAccount: String? = null,
        wechatAccount: String? = null,
    ): UserSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (nickname != null) body.put("nickname", nickname)
        if (gender != null) body.put("gender", gender)
        if (region != null) body.put("region", region)
        if (signature != null) body.put("signature", signature)
        if (email != null) body.put("email", email)
        if (alipayAccount != null) body.put("alipayAccount", alipayAccount)
        if (wechatAccount != null) body.put("wechatAccount", wechatAccount)
        if (shippingName != null || shippingPhone != null || shippingDetail != null) {
            body.put(
                "shipping",
                JSONObject()
                    .put("name", shippingName ?: "")
                    .put("phone", shippingPhone ?: "")
                    .put("detail", shippingDetail ?: ""),
            )
        }
        val root = request("PATCH", "/me", auth = token, body = body)
        val user = root.getJSONObject("user")
        parseUserSession(token = token, root = root, user = user)
    }

    suspend fun refreshNetworkRegion(token: String): Pair<UserSession, String> =
        withContext(Dispatchers.IO) {
            val root = request("POST", "/me/network-region/refresh", auth = token, body = JSONObject())
            val user = root.getJSONObject("user")
            val session = parseUserSession(token = token, root = root, user = user)
            val message = root.optString("message").ifBlank { "网络属地已刷新" }
            session to message
        }

    private fun parseUserSession(token: String, root: JSONObject, user: JSONObject): UserSession {
        val avatarUrl = optNullableString(root, "avatarUrl")
            ?: optNullableString(user, "avatarUrl")
        val level = when {
            user.has("level") && !user.isNull("level") -> user.optInt("level", 0)
            root.has("level") && !root.isNull("level") -> root.optInt("level", 0)
            else -> 0
        }.coerceIn(0, 7)
        return UserSession(
            token = token,
            userId = user.optLong("id"),
            phone = user.optString("phone"),
            vocabNotebookId = root.optLong("vocabNotebookId"),
            avatarUrl = avatarUrl,
            level = level,
            nickname = optNullableString(user, "nickname"),
            shippingName = optNullableString(user, "shippingName"),
            shippingPhone = optNullableString(user, "shippingPhone"),
            shippingDetail = optNullableString(user, "shippingDetail"),
            gender = optNullableString(user, "gender"),
            region = optNullableString(user, "region"),
            buddyId = optNullableString(user, "buddyId"),
            signature = optNullableString(user, "signature"),
            email = optNullableString(user, "email"),
            alipayAccount = optNullableString(user, "alipayAccount"),
            wechatAccount = optNullableString(user, "wechatAccount"),
            networkRegion = optNullableString(user, "networkRegion"),
            networkRegionDetail = optNullableString(user, "networkRegionDetail"),
        )
    }

    suspend fun fetchCheckIn(token: String): CheckInState = withContext(Dispatchers.IO) {
        val root = request("GET", "/me/checkin", auth = token)
        parseCheckIn(root.optJSONObject("checkIn"))
    }

    suspend fun performCheckIn(token: String): CheckInResult = withContext(Dispatchers.IO) {
        val root = request("POST", "/me/checkin", auth = token, body = JSONObject())
        val checkIn = parseCheckIn(root.optJSONObject("checkIn"))
        if (root.optBoolean("already", false) || !root.optBoolean("ok", false)) {
            CheckInResult.AlreadyCheckedIn
        } else {
            CheckInResult.Success(
                pointsEarned = root.optInt("pointsEarned", checkIn.todayReward),
                streakDays = root.optInt("streakDays", checkIn.streakDays),
                totalPoints = root.optInt("totalPoints", checkIn.totalPoints),
            )
        }
    }

    suspend fun makeupCheckIn(token: String, date: String): CheckInResult = withContext(Dispatchers.IO) {
        val root = request(
            "POST",
            "/me/checkin/makeup",
            auth = token,
            body = JSONObject().put("date", date),
        )
        if (!root.optBoolean("ok", false)) {
            if (root.optBoolean("already", false)) return@withContext CheckInResult.AlreadyCheckedIn
            throw IllegalStateException(root.optString("error").ifBlank { "补签失败" })
        }
        CheckInResult.Success(
            pointsEarned = root.optInt("pointsEarned", 1),
            streakDays = root.optInt("streakDays", 0),
            totalPoints = root.optInt("totalPoints", 0),
        )
    }

    suspend fun listGiftCategories(): List<GiftCategory> = withContext(Dispatchers.IO) {
        val root = request("GET", "/gifts/categories", auth = null)
        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                add(GiftCategory(id = obj.optString("id"), name = obj.optString("name")))
            }
        }
    }

    suspend fun listGifts(category: String = "recommend", page: Int = 1): List<GiftItem> =
        withContext(Dispatchers.IO) {
            val path = "/gifts?page=$page&pageSize=40&category=${enc(category)}"
            val root = request("GET", path, auth = null)
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    add(parseGift(obj))
                }
            }
        }

    suspend fun fetchGift(id: Long): GiftItem = withContext(Dispatchers.IO) {
        val root = request("GET", "/gifts/$id", auth = null)
        parseGift(root.getJSONObject("item"))
    }

    suspend fun redeemGift(
        token: String,
        giftId: Long,
        name: String? = null,
        phone: String? = null,
        detail: String? = null,
    ): GiftRedeemResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (!name.isNullOrBlank()) body.put("name", name)
        if (!phone.isNullOrBlank()) body.put("phone", phone)
        if (!detail.isNullOrBlank()) body.put("detail", detail)
        val root = request("POST", "/gifts/$giftId/redeem", auth = token, body = body)
        GiftRedeemResult(
            message = root.optString("message").ifBlank { "兑换成功" },
            order = parseGiftOrder(root.getJSONObject("order")),
            totalPoints = root.optInt("totalPoints"),
            checkIn = parseCheckIn(root.optJSONObject("checkIn")),
        )
    }

    suspend fun listGiftOrders(token: String, page: Int = 1): List<GiftOrder> =
        withContext(Dispatchers.IO) {
            val root = request("GET", "/me/gift-orders?page=$page&pageSize=50", auth = token)
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    add(parseGiftOrder(obj))
                }
            }
        }

    suspend fun fetchWithdrawConfig(token: String): WithdrawConfig = withContext(Dispatchers.IO) {
        val root = request("GET", "/me/withdrawals/config", auth = token)
        parseWithdrawConfig(root.getJSONObject("config"))
    }

    suspend fun listWithdrawals(token: String, page: Int = 1): List<WithdrawalItem> =
        withContext(Dispatchers.IO) {
            val root = request("GET", "/me/withdrawals?page=$page&pageSize=50", auth = token)
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    add(parseWithdrawal(obj))
                }
            }
        }

    suspend fun createWithdrawal(
        token: String,
        channel: String,
        account: String,
    ): WithdrawResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("channel", channel)
            .put("account", account)
        val root = request("POST", "/me/withdrawals", auth = token, body = body)
        WithdrawResult(
            message = root.optString("message").ifBlank { "提现成功" },
            item = parseWithdrawal(root.getJSONObject("item")),
            totalPoints = root.optInt("totalPoints"),
            checkIn = parseCheckIn(root.optJSONObject("checkIn")),
        )
    }

    private fun parseWithdrawConfig(obj: JSONObject): WithdrawConfig {
        val channelsArr = obj.optJSONArray("channels") ?: JSONArray()
        val channels = buildList {
            for (i in 0 until channelsArr.length()) {
                val c = channelsArr.optJSONObject(i) ?: continue
                add(
                    WithdrawChannel(
                        id = c.optString("id"),
                        name = c.optString("name"),
                        accountLabel = c.optString("accountLabel"),
                        accountHint = c.optString("accountHint"),
                    ),
                )
            }
        }
        return WithdrawConfig(
            sandbox = obj.optBoolean("sandbox", true),
            amountFen = obj.optInt("amountFen", 1),
            amountYuan = obj.optString("amountYuan").ifBlank { "0.01" },
            pointsCost = obj.optInt("pointsCost", 1),
            channels = channels,
            note = obj.optString("note"),
        )
    }

    private fun parseWithdrawal(obj: JSONObject): WithdrawalItem {
        return WithdrawalItem(
            id = obj.optLong("id"),
            channel = obj.optString("channel"),
            channelLabel = obj.optString("channelLabel"),
            account = obj.optString("account"),
            amountFen = obj.optInt("amountFen"),
            amountYuan = obj.optString("amountYuan").ifBlank { "0.00" },
            pointsSpent = obj.optInt("pointsSpent"),
            status = obj.optString("status"),
            statusLabel = obj.optString("statusLabel"),
            providerTradeNo = optNullableString(obj, "providerTradeNo"),
            errorMessage = optNullableString(obj, "errorMessage"),
            remark = optNullableString(obj, "remark"),
            sandbox = obj.optBoolean("sandbox", true),
            createdAt = optNullableString(obj, "createdAt"),
            updatedAt = optNullableString(obj, "updatedAt"),
            paidAt = optNullableString(obj, "paidAt"),
        )
    }

    private fun parseGift(obj: JSONObject): GiftItem {
        return GiftItem(
            id = obj.optLong("id"),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            coverEmoji = obj.optString("coverEmoji").ifBlank { "🎁" },
            coverColor = obj.optString("coverColor").ifBlank { "#1B6CA8" },
            category = obj.optString("category").ifBlank { "recommend" },
            pointsCost = obj.optInt("pointsCost"),
            cashFen = obj.optInt("cashFen"),
            cashYuan = obj.optString("cashYuan").ifBlank { "0.00" },
            originalPriceYuan = optNullableString(obj, "originalPriceYuan"),
            pointsOffsetYuan = optNullableString(obj, "pointsOffsetYuan"),
            stock = obj.optInt("stock", -1),
            redeemedCount = obj.optInt("redeemedCount"),
            needAddress = obj.optBoolean("needAddress"),
            description = obj.optString("description"),
        )
    }

    private fun parseGiftOrder(obj: JSONObject): GiftOrder {
        return GiftOrder(
            id = obj.optLong("id"),
            giftId = if (obj.has("giftId") && !obj.isNull("giftId")) obj.optLong("giftId") else null,
            giftTitle = obj.optString("giftTitle"),
            coverEmoji = obj.optString("coverEmoji").ifBlank { "🎁" },
            coverColor = obj.optString("coverColor").ifBlank { "#1B6CA8" },
            pointsSpent = obj.optInt("pointsSpent"),
            cashFen = obj.optInt("cashFen"),
            cashYuan = obj.optString("cashYuan").ifBlank { "0.00" },
            status = obj.optString("status"),
            addressName = optNullableString(obj, "addressName"),
            addressPhone = optNullableString(obj, "addressPhone"),
            addressDetail = optNullableString(obj, "addressDetail"),
            remark = optNullableString(obj, "remark"),
            createdAt = optNullableString(obj, "createdAt"),
        )
    }

    private fun parseCheckIn(obj: JSONObject?): CheckInState {
        if (obj == null) return CheckInState()
        val datesArr = obj.optJSONArray("recentDates")
        val recentDates = buildList {
            if (datesArr != null) {
                for (i in 0 until datesArr.length()) {
                    val d = datesArr.optString(i).trim()
                    if (d.isNotEmpty()) add(d)
                }
            }
        }
        val last = optNullableString(obj, "lastCheckInDate")
        val checkedInToday = obj.optBoolean("checkedInToday", false) ||
            recentDates.contains(CheckInStore.todayShanghai().toString())
        return CheckInState(
            totalPoints = obj.optInt("totalPoints", 0).coerceAtLeast(0),
            streakDays = obj.optInt("streakDays", 0).coerceAtLeast(0),
            lastCheckInDate = last,
            checkedInToday = checkedInToday,
            todayReward = obj.optInt("todayReward", 1).coerceIn(1, 7),
            recentDates = recentDates,
        )
    }

    suspend fun fetchAvatarUrl(token: String): String? = withContext(Dispatchers.IO) {
        fetchMe(token)?.avatarUrl
    }

    suspend fun fetchAvatarBytes(avatarUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val path = avatarUrl.substringBefore('?')
        val stamp = System.currentTimeMillis()
        val url = if (path.startsWith("http")) "$path?t=$stamp" else "${baseUrl.trimEnd('/')}$path?t=$stamp"
        val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299 || body.isEmpty()) error("头像加载失败")
            body
        } finally {
            conn.disconnect()
        }
    }

    suspend fun listNotebooks(token: String): List<Notebook> = withContext(Dispatchers.IO) {
        val root = request("GET", "/notebooks", token)
        val items = root.getJSONArray("items")
        buildList {
            for (i in 0 until items.length()) add(parseNotebook(items.getJSONObject(i)))
        }
    }

    /** Public catalogs (中考 / 高考 / CET) — no login required. */
    suspend fun listCatalogs(): List<Notebook> = withContext(Dispatchers.IO) {
        val root = request("GET", "/catalogs", auth = null)
        val items = root.getJSONArray("items")
        buildList {
            for (i in 0 until items.length()) add(parseNotebook(items.getJSONObject(i)))
        }
    }

    suspend fun createNotebook(token: String, name: String): Notebook = withContext(Dispatchers.IO) {
        val root = request(
            "POST",
            "/notebooks",
            token,
            body = JSONObject().put("name", name.trim()),
        )
        parseNotebook(root.getJSONObject("item"))
    }

    suspend fun deleteNotebook(token: String, id: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "/notebooks/$id", token)
    }

    suspend fun listWords(
        token: String?,
        notebookId: Long,
        cursor: String?,
        limit: Int = 100,
        fromIndex: Int = 0,
    ): WordPage =
        withContext(Dispatchers.IO) {
            val path = buildString {
                append("/notebooks/").append(notebookId).append("/words?limit=").append(limit)
                if (!cursor.isNullOrBlank()) {
                    append("&cursor=").append(enc(cursor))
                } else if (fromIndex > 0) {
                    append("&fromIndex=").append(fromIndex)
                }
            }
            val root = request("GET", path, token)
            val items = root.getJSONArray("items")
            WordPage(
                items = buildList {
                    for (i in 0 until items.length()) add(parseWord(items.getJSONObject(i)))
                },
                total = root.optInt("total"),
                nextCursor = optNullableString(root, "nextCursor"),
                fromIndex = root.optInt("fromIndex", fromIndex),
            )
        }

    /**
     * Absolute 0-based index of the first word for each initial letter in the notebook.
     * Keys are A–Z and optionally '#'.
     */
    suspend fun letterIndex(token: String?, notebookId: Long): Map<Char, Int> =
        withContext(Dispatchers.IO) {
            val root = request("GET", "/notebooks/$notebookId/letter-index", token)
            parseLetterIndex(root.optJSONObject("index"))
        }

    /** Full ordered word heads (id + text) for in-memory seeks. */
    suspend fun listHeads(token: String?, notebookId: Long): WordHeads =
        withContext(Dispatchers.IO) {
            val root = request("GET", "/notebooks/$notebookId/heads", token)
            val items = root.optJSONArray("items") ?: JSONArray()
            WordHeads(
                items = buildList {
                    for (i in 0 until items.length()) {
                        val obj = items.getJSONObject(i)
                        add(
                            WordHead(
                                id = obj.getLong("id"),
                                text = obj.optString("text"),
                                isPhrase = obj.optBoolean("isPhrase"),
                                ipaUk = optNullableString(obj, "ipaUk"),
                                ipaUs = optNullableString(obj, "ipaUs"),
                                sortOrder = obj.optInt("sortOrder"),
                            ),
                        )
                    }
                },
                total = root.optInt("total"),
                letterIndex = parseLetterIndex(root.optJSONObject("index")),
            )
        }

    private fun parseLetterIndex(obj: JSONObject?): Map<Char, Int> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.isEmpty()) continue
                put(key[0].uppercaseChar(), obj.getInt(key))
            }
        }
    }

    suspend fun createWord(token: String, notebookId: Long, entry: VocabEntry): VocabEntry =
        withContext(Dispatchers.IO) {
            val root = request(
                "POST",
                "/notebooks/$notebookId/words",
                token,
                body = wordBody(entry),
            )
            parseWord(root.getJSONObject("item"))
        }

    suspend fun updateWord(
        token: String,
        id: Long,
        definitions: List<Definition>? = null,
        examples: List<ExampleSentence>? = null,
        nearWords: List<String>? = null,
        synonyms: List<String>? = null,
        antonyms: List<String>? = null,
    ): VocabEntry = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (definitions != null) body.put("definitions", JSONArray(encodeDefinitions(definitions)))
        if (examples != null) {
            val array = JSONArray()
            examples.forEach { item ->
                array.put(JSONObject().put("english", item.english).put("chinese", item.chinese))
            }
            body.put("examples", array)
        }
        if (nearWords != null) body.put("nearWords", JSONArray(nearWords))
        if (synonyms != null) body.put("synonyms", JSONArray(synonyms))
        if (antonyms != null) body.put("antonyms", JSONArray(antonyms))
        val root = request("PATCH", "/words/$id", token, body = body)
        parseWord(root.getJSONObject("item"))
    }

    suspend fun deleteWord(token: String, id: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "/words/$id", token)
    }

    suspend fun fetchHomophones(token: String?, word: String, limit: Int = 3): List<WordHomophone> =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(word.trim(), StandardCharsets.UTF_8.name())
            val root = request("GET", "/homophones?word=$encoded&limit=$limit", token)
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    add(parseHomophone(obj))
                }
            }
        }

    suspend fun submitHomophone(token: String, word: String, body: String): WordHomophone =
        withContext(Dispatchers.IO) {
            val root = request(
                "POST",
                "/homophones",
                token,
                body = JSONObject().put("word", word).put("body", body),
            )
            parseHomophone(root.getJSONObject("item"))
        }

    suspend fun toggleHomophoneLike(token: String, id: Long): WordHomophone =
        withContext(Dispatchers.IO) {
            val root = request("POST", "/homophones/$id/like", token)
            parseHomophone(root.getJSONObject("item"))
        }

    suspend fun fetchHomophoneLikers(
        token: String,
        id: Long,
        limit: Int = 20,
        offset: Int = 0,
    ): HomophoneLikersPage = withContext(Dispatchers.IO) {
        val root = request("GET", "/homophones/$id/likes?limit=$limit&offset=$offset", token)
        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                add(
                    WordHomophoneLiker(
                        userId = item.optLong("userId"),
                        label = item.optString("label").ifBlank { "用户" },
                        avatarUrl = optNullableString(item, "avatarUrl"),
                    ),
                )
            }
        }
        HomophoneLikersPage(
            total = root.optInt("total"),
            items = items,
            nextOffset = if (root.isNull("nextOffset")) null else root.optInt("nextOffset"),
        )
    }

    private fun parseHomophone(obj: JSONObject): WordHomophone {
        val likersArr = obj.optJSONArray("likers")
        val likers = buildList {
            if (likersArr != null) {
                for (i in 0 until likersArr.length()) {
                    val item = likersArr.optJSONObject(i) ?: continue
                    add(
                        WordHomophoneLiker(
                            userId = item.optLong("userId"),
                            label = item.optString("label").ifBlank { "用户" },
                            avatarUrl = optNullableString(item, "avatarUrl"),
                        ),
                    )
                }
            }
        }
        return WordHomophone(
            id = obj.getLong("id"),
            word = obj.optString("word"),
            body = obj.optString("body"),
            likeCount = obj.optInt("likeCount"),
            likedByMe = obj.optBoolean("likedByMe"),
            authorUserId = obj.optLong("authorUserId"),
            isMine = obj.optBoolean("isMine"),
            likers = likers,
        )
    }

    private fun parseWord(obj: JSONObject): VocabEntry {
        return VocabEntry(
            id = obj.getLong("id"),
            notebookId = obj.getLong("notebookId"),
            text = obj.getString("text"),
            isPhrase = obj.optBoolean("isPhrase"),
            ipaUk = optNullableString(obj, "ipaUk"),
            ipaUs = optNullableString(obj, "ipaUs"),
            definitions = decodeDefinitions(obj.optJSONArray("definitions")?.toString() ?: "[]"),
            examples = decodeExamples(
                JSONArray().also { out ->
                    val raw = obj.optJSONArray("examples") ?: return@also
                    for (i in 0 until raw.length()) {
                        val ex = raw.optJSONObject(i) ?: continue
                        out.put(
                            JSONObject()
                                .put("en", ex.optString("english").ifBlank { ex.optString("en") })
                                .put("zh", ex.optString("chinese").ifBlank { ex.optString("zh") }),
                        )
                    }
                }.toString(),
            ),
            nearWords = decodeStringList(obj.optJSONArray("nearWords")?.toString()),
            synonyms = decodeStringList(obj.optJSONArray("synonyms")?.toString()),
            antonyms = decodeStringList(obj.optJSONArray("antonyms")?.toString()),
            sortOrder = obj.optInt("sortOrder"),
            addedAtMillis = obj.optLong("addedAtMillis", System.currentTimeMillis()),
        )
    }

    private fun wordBody(entry: VocabEntry): JSONObject {
        val examples = JSONArray()
        entry.examples.forEach { item ->
            examples.put(JSONObject().put("english", item.english).put("chinese", item.chinese))
        }
        return JSONObject()
            .put("text", entry.text)
            .put("isPhrase", entry.isPhrase)
            .put("ipaUk", entry.ipaUk)
            .put("ipaUs", entry.ipaUs)
            .put("definitions", JSONArray(encodeDefinitions(entry.definitions)))
            .put("examples", examples)
            .put("nearWords", JSONArray(entry.nearWords))
            .put("synonyms", JSONArray(entry.synonyms))
            .put("antonyms", JSONArray(entry.antonyms))
    }

    private fun request(method: String, path: String, auth: String?, body: JSONObject? = null): JSONObject {
        var lastError: Exception? = null
        val order = listOf(baseUrl) + bases.filter { it != baseUrl }
        for (base in order) {
            try {
                val json = requestOnce(base, method, path, auth, body)
                baseUrl = base
                return json
            } catch (error: ApiException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError?.let { friendlyNetworkError(it) } ?: ApiException("无法连接服务器")
    }

    private fun requestOnce(
        base: String,
        method: String,
        path: String,
        auth: String?,
        body: JSONObject?,
    ): JSONObject {
        val conn = java.net.URI("$base$path").toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/json")
            applyDeviceHeaders(conn)
            if (!auth.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $auth")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = parseObject(text)
            if (code !in 200..299) {
                val errCode = json.optString("code").trim().ifBlank { null }
                val message = json.optString("error").ifBlank { "http $code" }
                if (errCode == ApiException.SESSION_REPLACED_CODE) {
                    AuthSessionEvents.notifyReplaced(message)
                }
                throw ApiException(message, code = errCode, httpCode = code)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun applyDeviceHeaders(conn: HttpURLConnection) {
        val brand = Build.BRAND.orEmpty().ifBlank { Build.MANUFACTURER.orEmpty() }
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val osVersion = Build.VERSION.RELEASE.orEmpty()
        val labelParts = listOfNotNull(
            brand.takeIf { it.isNotBlank() },
            model.takeIf { it.isNotBlank() && !it.equals(brand, ignoreCase = true) },
        )
        val deviceName = labelParts.joinToString(" ").ifBlank { model.ifBlank { "Android" } }
        conn.setRequestProperty(
            "User-Agent",
            "HotWords/${BuildConfig.VERSION_NAME} (Android $osVersion; $deviceName; brand/$brand; model/$model; manufacturer/$manufacturer)",
        )
        conn.setRequestProperty("X-Device-Platform", "Android")
        conn.setRequestProperty("X-Device-Brand", brand)
        conn.setRequestProperty("X-Device-Manufacturer", manufacturer)
        conn.setRequestProperty("X-Device-Model", model)
        if (product.isNotBlank()) conn.setRequestProperty("X-Device-Product", product)
        if (osVersion.isNotBlank()) conn.setRequestProperty("X-Device-Os-Version", osVersion)
        conn.setRequestProperty("X-Device-Sdk", Build.VERSION.SDK_INT.toString())
        conn.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
    }

    private fun parseNotebook(obj: JSONObject): Notebook {
        return Notebook(
            id = obj.getLong("id"),
            name = obj.getString("name"),
            sortOrder = obj.optInt("sortOrder"),
            createdAtMillis = obj.optLong("createdAtMillis"),
            kind = obj.optString("kind", "user"),
            slug = optNullableString(obj, "slug"),
            wordCount = obj.optInt("wordCount"),
        )
    }

    private fun parseObject(text: String): JSONObject {
        if (text.isBlank()) return JSONObject()
        return runCatching { JSONObject(text) }.getOrElse { JSONObject() }
    }

    private fun optNullableString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key).trim()
        return value.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun friendlyNetworkError(error: Exception): ApiException {
        val message = error.message.orEmpty()
        val hint = when {
            message.contains("Connection reset", ignoreCase = true) ||
                message.contains("failed to connect", ignoreCase = true) ||
                message.contains("ECONNREFUSED", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ->
                if (BuildConfig.DEBUG) {
                    "无法连接服务器。模拟器请确认本机后端已启动，并执行 adb reverse tcp:8787 tcp:8787；真机 Debug 会自动尝试云端。"
                } else {
                    "无法连接服务器，请检查手机网络后重试"
                }
            message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                "连接服务器超时，请检查网络后重试"
            message.contains("SSL", ignoreCase = true) ||
                message.contains("CertPath", ignoreCase = true) ->
                "安全连接失败，请稍后重试或检查系统时间"
            else -> message.ifBlank { "无法连接服务器" }
        }
        return ApiException(hint)
    }
}
