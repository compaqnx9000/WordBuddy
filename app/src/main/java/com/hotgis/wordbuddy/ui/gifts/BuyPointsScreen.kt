package com.hotgis.wordbuddy.ui.gifts

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.auth.WeChatAuth
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.data.PointPackage
import com.hotgis.wordbuddy.data.PointPurchaseResult
import com.hotgis.wordbuddy.pay.AlipayPayHelper
import com.hotgis.wordbuddy.pay.WeChatPayHelper
import com.hotgis.wordbuddy.pay.WeChatPayParams
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WechatGreen = Color(0xFF07C160)
private val AlipayBlue = Color(0xFF1677FF)

@Composable
fun BuyPointsScreen(
    totalPoints: Int,
    authToken: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onPointsUpdated: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val api = remember { WordBuddyApi() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<PointPackage>>(emptyList()) }
    var aiCost by remember { mutableStateOf(5) }
    var sandbox by remember { mutableStateOf(true) }
    var alipayReady by remember { mutableStateOf(true) }
    var wechatReady by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf("wechat") }
    var loading by remember { mutableStateOf(true) }
    var buyingId by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf(totalPoints) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { api.fetchPointPackages() }
            .onSuccess {
                packages = it.items
                aiCost = it.aiImagePointsCost
                sandbox = it.sandbox
                alipayReady = it.alipayReady || it.sandbox
                wechatReady = it.wechatReady
                channel = when {
                    it.wechatReady -> "wechat"
                    alipayReady -> "alipay"
                    else -> "alipay"
                }
            }
            .onFailure {
                Toast.makeText(context, it.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        loading = false
    }

    fun buy(pkg: PointPackage) {
        val token = authToken
        if (token.isNullOrBlank()) {
            onLogin()
            return
        }
        if (activity == null) {
            Toast.makeText(context, "无法调起支付", Toast.LENGTH_SHORT).show()
            return
        }
        if (channel == "wechat" && !wechatReady) {
            Toast.makeText(context, "微信支付尚未开通", Toast.LENGTH_SHORT).show()
            return
        }
        if (channel == "alipay" && !alipayReady) {
            Toast.makeText(context, "支付宝支付尚未开通", Toast.LENGTH_SHORT).show()
            return
        }
        buyingId = pkg.id
        scope.launch {
            val created = runCatching {
                withContext(Dispatchers.IO) { api.createPointOrder(token, pkg.id, channel) }
            }.getOrElse {
                Toast.makeText(context, it.message ?: "下单失败", Toast.LENGTH_SHORT).show()
                buyingId = null
                return@launch
            }
            val paid = payOrder(activity, api, token, created)
            buyingId = null
            if (paid == null) return@launch
            if (paid.balance != null) {
                balance = paid.balance
                onPointsUpdated(paid.balance)
                Toast.makeText(context, "已到账 ${pkg.points} 积分", Toast.LENGTH_SHORT).show()
            } else {
                repeat(8) {
                    delay(900)
                    val order = runCatching {
                        withContext(Dispatchers.IO) {
                            api.getPointOrder(token, created.orderId)
                        }
                    }.getOrNull()
                    if (order?.status == "paid") {
                        Toast.makeText(context, "支付成功，积分已到账", Toast.LENGTH_SHORT).show()
                        onPointsUpdated(-1)
                        return@launch
                    }
                }
                Toast.makeText(context, "支付结果确认中，稍后下拉刷新积分", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.sdp(), vertical = 10.sdp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.OnSurface,
                modifier = Modifier
                    .size(40.sdp())
                    .clip(RoundedCornerShape(12.sdp()))
                    .clickable(onClick = onBack)
                    .padding(10.sdp()),
            )
            Text(
                text = "购买积分",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp()))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Stellar.Cyan.copy(alpha = 0.28f),
                                    Stellar.Pink.copy(alpha = 0.14f),
                                ),
                            ),
                        )
                        .padding(16.sdp()),
                ) {
                    Text("当前积分", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                    Text(
                        text = "$balance",
                        color = Stellar.Cyan,
                        fontSize = 32.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.sdp()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Stellar.Gold,
                            modifier = Modifier.size(18.sdp()),
                        )
                        Spacer(Modifier.width(6.sdp()))
                        Text(
                            text = "AI 生图每次消耗 $aiCost 积分（缓存图不扣）",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 12.ssp(),
                        )
                    }
                    if (sandbox && channel == "alipay") {
                        Spacer(Modifier.height(6.sdp()))
                        Text(
                            text = "支付宝当前为沙箱：点购买会直接到账，不调起真实支付。",
                            color = Stellar.Gold,
                            fontSize = 12.ssp(),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "支付方式",
                    color = Stellar.OnSurface,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.sdp()))
                Row(horizontalArrangement = Arrangement.spacedBy(10.sdp())) {
                    PayChannelChip(
                        title = "微信",
                        iconRes = R.drawable.ic_wechat,
                        accent = WechatGreen,
                        selected = channel == "wechat",
                        enabled = wechatReady,
                        onClick = { channel = "wechat" },
                        modifier = Modifier.weight(1f),
                    )
                    PayChannelChip(
                        title = "支付宝",
                        iconRes = R.drawable.ic_alipay,
                        accent = AlipayBlue,
                        selected = channel == "alipay",
                        enabled = alipayReady,
                        onClick = { channel = "alipay" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!wechatReady) {
                    Spacer(Modifier.height(6.sdp()))
                    Text(
                        text = "微信支付待商户证书配置完成后可用",
                        color = Stellar.OnSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.ssp(),
                    )
                }
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                packages.isEmpty() -> item {
                    Text("暂无积分包", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                }
                else -> items(packages, key = { it.id }) { pkg ->
                    PackageCard(
                        pkg = pkg,
                        busy = buyingId == pkg.id,
                        enabled = buyingId == null,
                        onBuy = { buy(pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PayChannelChip(
    title: String,
    iconRes: Int,
    accent: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.sdp()))
            .border(
                1.dp,
                when {
                    !enabled -> Stellar.Outline.copy(alpha = 0.25f)
                    selected -> accent
                    else -> Stellar.Outline.copy(alpha = 0.35f)
                },
                RoundedCornerShape(12.sdp()),
            )
            .background(
                when {
                    !enabled -> Stellar.SurfaceHigh.copy(alpha = 0.5f)
                    selected -> accent.copy(alpha = 0.12f)
                    else -> Stellar.SurfaceContainer
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.sdp()),
            contentScale = ContentScale.Fit,
            alpha = if (enabled) 1f else 0.4f,
        )
        Spacer(Modifier.width(8.sdp()))
        Text(
            text = title,
            color = if (enabled) Stellar.OnSurface else Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 14.ssp(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PackageCard(
    pkg: PointPackage,
    busy: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .stellarGlass()
            .border(1.dp, Stellar.Outline.copy(alpha = 0.35f), RoundedCornerShape(14.sdp()))
            .padding(14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pkg.title,
                    color = Stellar.OnSurface,
                    fontSize = 16.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
                if (!pkg.badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.sdp()))
                    Text(
                        text = pkg.badge,
                        color = Stellar.Gold,
                        fontSize = 11.ssp(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.sdp()))
                            .background(Stellar.Gold.copy(alpha = 0.15f))
                            .padding(horizontal = 6.sdp(), vertical = 2.sdp()),
                    )
                }
            }
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = "${pkg.points} 积分${if (pkg.subtitle.isNotBlank()) " · ${pkg.subtitle}" else ""}",
                color = Stellar.OnSurfaceVariant,
                fontSize = 12.ssp(),
            )
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = "¥${pkg.amountYuan}",
                color = Stellar.Pink,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(20.sdp()))
                .background(if (enabled) Stellar.Cyan else Stellar.Cyan.copy(alpha = 0.4f))
                .clickable(enabled = enabled && !busy, onClick = onBuy)
                .padding(horizontal = 16.sdp(), vertical = 10.sdp()),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Stellar.OnSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.sdp()),
                )
            } else {
                Text("购买", color = Stellar.OnSurface, fontSize = 14.ssp(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private data class PaidOutcome(val balance: Int?)

private suspend fun payOrder(
    activity: Activity,
    api: WordBuddyApi,
    token: String,
    created: PointPurchaseResult,
): PaidOutcome? {
    if (created.channel == "wechat") {
        val wx = created.wechatPay
        if (wx == null || wx.prepayId.isBlank()) {
            Toast.makeText(activity, "微信下单失败", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!WeChatAuth.isConfigured()) {
            Toast.makeText(activity, "微信尚未配置", Toast.LENGTH_SHORT).show()
            return null
        }
        val result = runCatching {
            WeChatPayHelper.pay(
                activity,
                WeChatPayParams(
                    appId = wx.appId,
                    partnerId = wx.partnerId,
                    prepayId = wx.prepayId,
                    packageValue = wx.packageValue,
                    nonceStr = wx.nonceStr,
                    timeStamp = wx.timeStamp,
                    sign = wx.sign,
                ),
            ).await()
        }.getOrElse {
            Toast.makeText(activity, it.message ?: "调起微信支付失败", Toast.LENGTH_SHORT).show()
            return null
        }
        return when {
            result.success -> PaidOutcome(null)
            result.cancelled -> {
                Toast.makeText(activity, "已取消支付", Toast.LENGTH_SHORT).show()
                null
            }
            else -> {
                Toast.makeText(
                    activity,
                    result.errStr?.takeIf { it.isNotBlank() } ?: "支付未完成",
                    Toast.LENGTH_SHORT,
                ).show()
                null
            }
        }
    }

    if (created.sandbox || created.orderInfo.isNullOrBlank()) {
        val sim = runCatching {
            withContext(Dispatchers.IO) { api.simulatePointOrderPay(token, created.orderId) }
        }.getOrElse {
            Toast.makeText(activity, it.message ?: "支付失败", Toast.LENGTH_SHORT).show()
            return null
        }
        return PaidOutcome(sim.balance)
    }
    val result = runCatching {
        AlipayPayHelper.pay(activity, created.orderInfo)
    }.getOrElse {
        Toast.makeText(activity, it.message ?: "调起支付宝失败", Toast.LENGTH_SHORT).show()
        return null
    }
    return when {
        result.success || result.pending -> PaidOutcome(null)
        result.cancelled -> {
            Toast.makeText(activity, "已取消支付", Toast.LENGTH_SHORT).show()
            null
        }
        else -> {
            Toast.makeText(activity, result.memo.ifBlank { "支付未完成(${result.resultStatus})" }, Toast.LENGTH_SHORT).show()
            null
        }
    }
}
