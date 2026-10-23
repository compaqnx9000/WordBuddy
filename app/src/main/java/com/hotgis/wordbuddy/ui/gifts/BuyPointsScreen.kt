package com.hotgis.wordbuddy.ui.gifts

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.data.PointOrder
import com.hotgis.wordbuddy.data.PointPackage
import com.hotgis.wordbuddy.data.PointPurchaseResult
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.pay.AlipayPayHelper
import com.hotgis.wordbuddy.pay.WeChatPayHelper
import com.hotgis.wordbuddy.pay.WeChatPayParams
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BuyPointsScreen(
    imageCredits: Int,
    authToken: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onCreditsUpdated: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val api = remember { WordBuddyApi() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<PointPackage>>(emptyList()) }
    var sandbox by remember { mutableStateOf(true) }
    var alipayReady by remember { mutableStateOf(true) }
    var wechatReady by remember { mutableStateOf(false) }
    var payChannel by remember { mutableStateOf("alipay") }
    var loading by remember { mutableStateOf(true) }
    var buyingId by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf(imageCredits) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<PointOrder>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resumingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(imageCredits) {
        balance = imageCredits
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { api.fetchPointPackages() }
            .onSuccess {
                packages = it.items
                sandbox = it.sandbox
                alipayReady = it.alipayReady || it.sandbox
                wechatReady = it.wechatReady
            }
            .onFailure {
                Toast.makeText(context, it.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        loading = false
    }

    fun loadHistory() {
        val token = authToken
        if (token.isNullOrBlank()) {
            onLogin()
            return
        }
        historyLoading = true
        scope.launch {
            runCatching { api.listPointOrders(token) }
                .onSuccess { history = it }
                .onFailure {
                    Toast.makeText(context, it.message ?: "加载购买记录失败", Toast.LENGTH_SHORT).show()
                }
            historyLoading = false
        }
    }

    LaunchedEffect(showHistory) {
        if (!showHistory) return@LaunchedEffect
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val expiredPendingIds = history
        .filter { it.status == "pending" && it.payLeftMs(nowMillis) <= 0L }
        .map { it.id }
    LaunchedEffect(showHistory, expiredPendingIds) {
        if (showHistory && expiredPendingIds.isNotEmpty()) loadHistory()
    }

    fun settlePaid(token: String, created: PointPurchaseResult, credits: Int) {
        scope.launch {
            val paid = payOrder(activity ?: return@launch, api, token, created)
            if (paid == null) {
                buyingId = null
                resumingId = null
                if (showHistory) loadHistory()
                return@launch
            }
            fun applyCredits(next: Int) {
                balance = next
                onCreditsUpdated(next)
            }
            if (paid.imageCredits != null) {
                applyCredits(paid.imageCredits)
                buyingId = null
                resumingId = null
                Toast.makeText(context, "已到账 $credits 张配图", Toast.LENGTH_SHORT).show()
            } else {
                var credited = false
                for (attempt in 0 until 12) {
                    if (attempt > 0) delay(500)
                    val lookup = runCatching {
                        withContext(Dispatchers.IO) { api.getPointOrder(token, created.orderId) }
                    }.getOrNull()
                    if (lookup?.order?.status == "paid") {
                        val next = lookup.imageCredits ?: (balance + credits).coerceAtLeast(0)
                        applyCredits(next)
                        credited = true
                        break
                    }
                }
                buyingId = null
                resumingId = null
                Toast.makeText(
                    context,
                    if (credited) "已到账 $credits 张配图" else "支付结果确认中，请稍后查看剩余配图",
                    if (credited) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
            }
            if (showHistory) loadHistory()
        }
    }

    fun resume(order: PointOrder) {
        val token = authToken
        if (token.isNullOrBlank()) {
            onLogin()
            return
        }
        if (activity == null) {
            Toast.makeText(context, "无法调起支付", Toast.LENGTH_SHORT).show()
            return
        }
        if (buyingId != null || resumingId != null) return
        if (order.payLeftMs(System.currentTimeMillis()) <= 0L) {
            Toast.makeText(context, "超过 10 分钟未支付，订单已取消", Toast.LENGTH_SHORT).show()
            loadHistory()
            return
        }
        resumingId = order.id
        scope.launch {
            val created = runCatching {
                withContext(Dispatchers.IO) { api.continuePointOrder(token, order.id) }
            }.getOrElse {
                Toast.makeText(context, it.message ?: "无法继续支付", Toast.LENGTH_SHORT).show()
                resumingId = null
                loadHistory()
                return@launch
            }
            settlePaid(token, created, order.points)
        }
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
        val channel = payChannel
        if (channel == "alipay" && !alipayReady) {
            Toast.makeText(context, "支付宝支付尚未开通", Toast.LENGTH_SHORT).show()
            return
        }
        if (channel == "wechat" && !wechatReady) {
            Toast.makeText(context, "微信支付尚未开通", Toast.LENGTH_SHORT).show()
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
            if (paid == null) {
                buyingId = null
                return@launch
            }
            fun applyCredits(next: Int) {
                balance = next
                onCreditsUpdated(next)
            }
            if (paid.imageCredits != null) {
                applyCredits(paid.imageCredits)
                buyingId = null
                Toast.makeText(context, "已到账 ${pkg.credits} 张配图", Toast.LENGTH_SHORT).show()
            } else {
                var credited = false
                for (attempt in 0 until 12) {
                    if (attempt > 0) delay(500)
                    val lookup = runCatching {
                        withContext(Dispatchers.IO) {
                            api.getPointOrder(token, created.orderId)
                        }
                    }.getOrNull()
                    if (lookup?.order?.status == "paid") {
                        val next = lookup.imageCredits ?: (balance + pkg.credits).coerceAtLeast(0)
                        applyCredits(next)
                        credited = true
                        break
                    }
                }
                buyingId = null
                Toast.makeText(
                    context,
                    if (credited) "已到账 ${pkg.credits} 张配图" else "支付结果确认中，请稍后查看剩余配图",
                    if (credited) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
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
                text = "购买配图",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!authToken.isNullOrBlank()) {
                Text(
                    text = "购买记录",
                    color = Stellar.CyanSoft,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            showHistory = true
                            loadHistory()
                        }
                        .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                )
            }
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
                    Text("剩余配图", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                    Text(
                        text = "$balance 张",
                        color = Stellar.Cyan,
                        fontSize = 32.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.sdp()))
                    Text(
                        text = "只能用来给单词生成助记配图，不能提现、兑礼或转给别人。生成成功扣 1 张，看过的缓存图不扣。",
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 12.ssp(),
                    )
                    if (sandbox) {
                        Spacer(Modifier.height(6.sdp()))
                        Text(
                            text = "支付宝当前为沙箱：选支付宝购买会直接到账，不调起真实支付。",
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
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                listOf("alipay" to "支付宝", "wechat" to "微信"),
                key = { it.first },
            ) { (id, label) ->
                val selectedNow = payChannel == id
                val ready = if (id == "wechat") wechatReady else alipayReady
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.sdp()))
                        .border(
                            1.dp,
                            if (selectedNow) Stellar.Cyan else Stellar.Outline.copy(alpha = 0.35f),
                            RoundedCornerShape(14.sdp()),
                        )
                        .background(
                            if (selectedNow) Stellar.Cyan.copy(alpha = 0.12f) else Stellar.SurfaceContainer,
                        )
                        .clickable { payChannel = id }
                        .padding(14.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WithdrawChannelBadge(channelId = id, size = 36.sdp())
                    Spacer(Modifier.width(12.sdp()))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = Stellar.OnSurface,
                            fontSize = 16.ssp(),
                            fontWeight = FontWeight.Medium,
                        )
                        if (!ready) {
                            Text(
                                text = "尚未开通",
                                color = Stellar.OnSurfaceVariant,
                                fontSize = 12.ssp(),
                            )
                        }
                    }
                    if (selectedNow) {
                        Text("已选", color = Stellar.Cyan, fontSize = 13.ssp())
                    }
                }
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                packages.isEmpty() -> item {
                    Text("暂无配图档位", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
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

    if (showHistory) {
        val visibleHistory = history.filter { order ->
            order.status != "pending" || order.payLeftMs(nowMillis) > 0L
        }
        PurchaseHistoryDialog(
            items = visibleHistory,
            loading = historyLoading,
            nowMillis = nowMillis,
            resumingId = resumingId,
            onContinue = { resume(it) },
            onDismiss = { showHistory = false },
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
                text = pkg.subtitle.ifBlank { "${pkg.credits} 张助记配图" },
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
                    color = Stellar.OnPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.sdp()),
                )
            } else {
                Text("购买", color = Stellar.OnPrimary, fontSize = 14.ssp(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PurchaseHistoryDialog(
    items: List<PointOrder>,
    loading: Boolean,
    nowMillis: Long,
    resumingId: Long?,
    onContinue: (PointOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 18.sdp())
                .clip(RoundedCornerShape(20.sdp()))
                .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                .border(1.dp, Stellar.Cyan.copy(alpha = 0.35f), RoundedCornerShape(20.sdp()))
                .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "购买记录",
                    color = Stellar.CyanSoft,
                    fontSize = 18.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .size(24.sdp())
                        .clickable(onClick = onDismiss),
                )
            }
            Spacer(Modifier.height(12.sdp()))
            when {
                loading && items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无购买记录", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.sdp()),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item ->
                            PurchaseHistoryCard(
                                item = item,
                                nowMillis = nowMillis,
                                busy = resumingId == item.id,
                                onContinue = { onContinue(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseHistoryCard(
    item: PointOrder,
    nowMillis: Long,
    busy: Boolean,
    onContinue: () -> Unit,
) {
    val channel = item.payChannel ?: "alipay"
    val channelName = if (channel == "wechat") "微信" else "支付宝"
    val amount = item.amountYuan.ifBlank {
        val fen = item.amountFen.coerceAtLeast(0)
        if (fen % 100 == 0) (fen / 100).toString() else "%.2f".format(fen / 100.0)
    }
    val pendingLeft = if (item.status == "pending") item.payLeftMs(nowMillis) else 0L
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .background(Stellar.SurfaceHigh.copy(alpha = 0.9f))
            .padding(12.sdp()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WithdrawChannelBadge(channelId = channel, size = 28.sdp())
            Spacer(Modifier.width(10.sdp()))
            Text(
                text = channelName,
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pendingLeft > 0L) {
                    "待支付 ${formatPayLeft(pendingLeft)}"
                } else {
                    item.statusLabel.ifBlank { item.status }
                },
                color = when (item.status) {
                    "paid" -> Stellar.Cyan
                    "refunded" -> Stellar.Pink
                    "closed" -> Stellar.OnSurfaceVariant
                    else -> Stellar.Gold
                },
                fontSize = 13.ssp(),
            )
        }
        Spacer(Modifier.height(10.sdp()))
        HistoryMetaRow("购买内容", "${item.points} 张配图")
        HistoryMetaRow("支付金额", "¥$amount")
        HistoryMetaRow("支付方式", channelName)
        HistoryMetaRow("购买时间", formatWithdrawTime(item.createdAt))
        HistoryMetaRow(
            "到账时间",
            when (item.status) {
                "paid", "refunded" -> formatWithdrawTime(item.paidAt)
                "closed" -> "—"
                else -> "剩余 ${formatPayLeft(pendingLeft)}"
            },
        )
        if (pendingLeft > 0L) {
            Spacer(Modifier.height(10.sdp()))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(if (busy) Stellar.Cyan.copy(alpha = 0.4f) else Stellar.Cyan)
                    .clickable(enabled = !busy, onClick = onContinue)
                    .padding(vertical = 10.sdp()),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Stellar.OnPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.sdp()),
                    )
                } else {
                    Text(
                        text = "继续支付",
                        color = Stellar.OnPrimary,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun PointOrder.payLeftMs(nowMillis: Long): Long {
    val raw = expiresAt ?: return 0L
    val expires = runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
    return (expires - nowMillis).coerceAtLeast(0L)
}

private fun formatPayLeft(ms: Long): String {
    val totalSec = (ms / 1000L).toInt().coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

private data class PaidOutcome(val balance: Int?, val imageCredits: Int? = null)

private suspend fun payOrder(
    activity: Activity,
    api: WordBuddyApi,
    token: String,
    created: PointPurchaseResult,
): PaidOutcome? {
    if (created.channel == "wechat") {
        val wx = created.wechatPay
        if (wx == null) {
            Toast.makeText(activity, "微信下单失败", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(activity, result.errStr?.ifBlank { null } ?: "支付未完成", Toast.LENGTH_SHORT).show()
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
        return PaidOutcome(sim.balance, sim.imageCredits)
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
