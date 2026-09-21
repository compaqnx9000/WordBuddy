package com.hotgis.wordbuddy.pay

import android.app.Activity
import android.content.Context
import com.alipay.sdk.app.AuthTask
import com.alipay.sdk.app.PayTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

data class AlipayPayResult(
    val resultStatus: String,
    val result: String,
    val memo: String,
) {
    val success: Boolean get() = resultStatus == "9000"
    val cancelled: Boolean get() = resultStatus == "6001"
    val pending: Boolean get() = resultStatus == "8000" || resultStatus == "6004"
}

data class AlipayAuthResult(
    val resultStatus: String,
    val result: String,
    val memo: String,
    val authCode: String?,
) {
    val cancelled: Boolean get() = resultStatus == "6001"
}

object AlipayPayHelper {
    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return ALIPAY_PACKAGES.any { name ->
            runCatching {
                pm.getPackageInfo(name, 0)
                true
            }.getOrDefault(false)
        }
    }

    suspend fun pay(activity: Activity, orderInfo: String): AlipayPayResult =
        withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            val raw = PayTask(activity).payV2(orderInfo, true) as Map<String, String>
            AlipayPayResult(
                resultStatus = raw["resultStatus"].orEmpty(),
                result = raw["result"].orEmpty(),
                memo = raw["memo"].orEmpty(),
            )
        }

    suspend fun auth(activity: Activity, authInfo: String): AlipayAuthResult =
        withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            val raw = AuthTask(activity).authV2(authInfo, true) as Map<String, String>
            val result = raw["result"].orEmpty()
            AlipayAuthResult(
                resultStatus = raw["resultStatus"].orEmpty(),
                result = result,
                memo = raw["memo"].orEmpty(),
                authCode = authCodeFrom(result),
            )
        }

    private fun authCodeFrom(result: String): String? {
        for (part in result.split('&')) {
            val idx = part.indexOf('=')
            if (idx <= 0 || part.substring(0, idx) != "auth_code") continue
            val value = runCatching {
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8.name())
            }.getOrDefault(part.substring(idx + 1))
            if (value.isNotBlank()) return value
        }
        return null
    }

    private val ALIPAY_PACKAGES = listOf(
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC",
    )
}

fun isAlipayAuthIdentity(value: String?): Boolean {
    val id = value?.trim().orEmpty()
    if (id.length < 16) return false
    if (Regex("^2088\\d{12,16}$").matches(id)) return true
    if (id.contains('@') || Regex("^1\\d{10}$").matches(id)) return false
    return Regex("^[A-Za-z0-9_-]{16,128}$").matches(id)
}
