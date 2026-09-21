package com.hotgis.wordbuddy.ui.gifts

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.data.HotWordsApi
import com.hotgis.wordbuddy.data.WithdrawConfig
import com.hotgis.wordbuddy.data.WithdrawalItem
import com.hotgis.wordbuddy.pay.isAlipayAuthIdentity
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WechatGreen = Color(0xFF07C160)

@Composable
fun PointsWithdrawScreen(
    totalPoints: Int,
    token: String?,
    alipayAccount: String?,
    alipayName: String?,
    wechatAccount: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { HotWordsApi() }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<WithdrawConfig?>(null) }
    var history by remember { mutableStateOf<List<WithdrawalItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var channel by remember { mutableStateOf("wechat") }
    var confirmOpen by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    fun reload() {
        val t = token
        if (t.isNullOrBlank()) {
            loading = false
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching {
                val cfg = api.fetchWithdrawConfig(t)
                val items = api.listWithdrawals(t)
                cfg to items
            }.onSuccess { (cfg, items) ->
                config = cfg
                history = items
                if (cfg.channels.none { it.id == channel }) {
                    channel = cfg.channels.firstOrNull()?.id ?: "alipay"
                }
            }.onFailure {
                error = it.message ?: "加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(token) { reload() }

    val cfg = config
    val selectedAccount = when (channel) {
        "wechat" -> wechatAccount?.trim().orEmpty()
        else -> alipayAccount?.trim().orEmpty()
    }
    val alipayReady = selectedAccount.isNotBlank() &&
        (isAlipayAuthIdentity(selectedAccount) || !alipayName.isNullOrBlank())
    val payoutReady = if (channel == "alipay") alipayReady else selectedAccount.isNotBlank()
    val selected = cfg?.channels?.firstOrNull { it.id == channel }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(stellarPanelBackgroundColor())
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.sdp())
                .padding(horizontal = 4.sdp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBackIos, contentDescription = "返回", tint = Stellar.OnSurface)
            }
            Text(
                text = "积分提现",
                color = Stellar.CyanSoft,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!token.isNullOrBlank()) {
                Text(
                    text = "提现记录",
                    color = Stellar.CyanSoft,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showHistory = true }
                        .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                )
            }
        }

        if (token.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("登录后可提现", color = Stellar.OnSurfaceVariant, fontSize = 15.ssp())
                    Spacer(Modifier.height(12.sdp()))
                    Text(
                        text = "去登录",
                        color = Stellar.Cyan,
                        fontSize = 15.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onLogin),
                    )
                }
            }
            return
        }

        if (loading && cfg == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Stellar.Cyan)
            }
            return
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
                        .stellarGlass()
                        .clip(RoundedCornerShape(16.sdp()))
                        .padding(16.sdp()),
                ) {
                    Text("可用积分", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                    Spacer(Modifier.height(6.sdp()))
                    Text(
                        text = "$totalPoints",
                        color = Stellar.Cyan,
                        fontSize = 28.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                    if (cfg != null) {
                        Spacer(Modifier.height(8.sdp()))
                        Text(
                            text = if (cfg.sandbox) {
                                "沙箱单笔 ¥${cfg.amountYuan}，消耗 ${cfg.pointsCost} 积分"
                            } else {
                                "单笔 ¥${cfg.amountYuan}，消耗 ${cfg.pointsCost} 积分"
                            },
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 13.ssp(),
                        )
                        if (cfg.note.isNotBlank()) {
                            Spacer(Modifier.height(6.sdp()))
                            Text(cfg.note, color = Stellar.Gold, fontSize = 12.ssp())
                        }
                    }
                }
            }

            item {
                Text("提现方式", color = Stellar.OnSurface, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
            }

            items(cfg?.channels.orEmpty(), key = { it.id }) { item ->
                val selectedNow = item.id == channel
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
                        .clickable { channel = item.id }
                        .padding(14.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WithdrawChannelBadge(channelId = item.id, size = 36.sdp())
                    Spacer(Modifier.width(12.sdp()))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (item.id == "wechat") "微信" else "支付宝",
                            color = Stellar.OnSurface,
                            fontSize = 16.ssp(),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = maskPayoutAccount(
                                if (item.id == "wechat") wechatAccount else alipayAccount,
                            ),
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 12.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedNow) {
                        Text("已选", color = Stellar.Cyan, fontSize = 13.ssp())
                    }
                }
            }

            if (!payoutReady) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .stellarGlass()
                            .clip(RoundedCornerShape(14.sdp()))
                            .padding(14.sdp()),
                    ) {
                        Text(
                            text = "收款账号",
                            color = Stellar.OnSurface,
                            fontSize = 14.ssp(),
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.sdp()))
                        Text(
                            text = if (selectedAccount.isBlank()) {
                                "尚未${if (channel == "wechat") "设置微信" else "绑定支付宝"}收款账号"
                            } else {
                                "尚未填写支付宝实名，打款会被拒绝"
                            },
                            color = Stellar.Pink,
                            fontSize = 13.ssp(),
                        )
                        Spacer(Modifier.height(8.sdp()))
                        Text(
                            text = "去个人资料设置",
                            color = Stellar.Cyan,
                            fontSize = 14.ssp(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onOpenProfile),
                        )
                    }
                }
            }

            if (error != null) {
                item {
                    Text(error ?: "", color = Stellar.Pink, fontSize = 13.ssp())
                }
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.sdp()))
                        .background(
                            if (submitting || cfg == null) Stellar.SurfaceHigh else Stellar.Cyan,
                        )
                        .clickable(enabled = !submitting && cfg != null) {
                            when {
                                selectedAccount.length < 3 -> {
                                    Toast.makeText(
                                        context,
                                        if (channel == "wechat") "请先在个人资料中设置收款账号" else "请先绑定支付宝账号",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                channel == "alipay" &&
                                    cfg?.sandbox != true &&
                                    !isAlipayAuthIdentity(selectedAccount) &&
                                    alipayName.isNullOrBlank() -> {
                                    Toast.makeText(
                                        context,
                                        "请先在个人资料中填写支付宝实名",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                channel == "wechat" &&
                                    cfg?.sandbox != true &&
                                    (selectedAccount.length < 18 || !selectedAccount.startsWith("o")) -> {
                                    Toast.makeText(
                                        context,
                                        "请填写微信 OpenID（以 o 开头，不是微信号）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                (cfg?.pointsCost ?: 1) > totalPoints -> {
                                    Toast.makeText(context, "积分不足", Toast.LENGTH_SHORT).show()
                                }
                                else -> confirmOpen = true
                            }
                        }
                        .padding(vertical = 14.sdp()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            color = Stellar.OnSurface,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.sdp()),
                        )
                    } else {
                        Text(
                            text = "确认提现 ¥${cfg?.amountYuan ?: "0.01"}",
                            color = if (cfg == null) Stellar.OnSurfaceVariant else Stellar.OnPrimary,
                            fontSize = 16.ssp(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        WithdrawHistoryDialog(
            items = history,
            onDismiss = { showHistory = false },
        )
    }

    if (confirmOpen && cfg != null) {
        val channelName = if (channel == "wechat") "微信" else "支付宝"
        StellarConfirmDialog(
            title = "确认提现",
            message = "将消耗 ${cfg.pointsCost} 积分，向${channelName}账号「${maskPayoutAccount(selectedAccount)}」发放 ¥${cfg.amountYuan}。\n\n${cfg.note}",
            confirmText = if (submitting) "提交中…" else "确认",
            dismissText = "取消",
            onDismiss = { if (!submitting) confirmOpen = false },
            onConfirm = {
                if (submitting) return@StellarConfirmDialog
                val t = token ?: return@StellarConfirmDialog
                submitting = true
                scope.launch {
                    runCatching {
                        api.createWithdrawal(t, channel, selectedAccount, alipayName)
                    }.onSuccess { result ->
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        confirmOpen = false
                        onSuccess()
                        reload()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "提现失败", Toast.LENGTH_LONG).show()
                    }
                    submitting = false
                }
            },
        )
    }
}

@Composable
private fun WithdrawHistoryDialog(
    items: List<WithdrawalItem>,
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
                    text = "提现记录",
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
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无提现记录", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.sdp()),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        WithdrawHistoryCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawHistoryCard(item: WithdrawalItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .background(Stellar.SurfaceHigh.copy(alpha = 0.9f))
            .padding(12.sdp()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WithdrawChannelBadge(channelId = item.channel, size = 28.sdp())
            Spacer(Modifier.width(10.sdp()))
            Text(
                text = if (item.channel == "wechat") "微信" else "支付宝",
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.statusLabel.ifBlank { item.status },
                color = when (item.status) {
                    "success" -> Stellar.Cyan
                    "failed" -> Stellar.Pink
                    else -> Stellar.Gold
                },
                fontSize = 13.ssp(),
            )
        }
        Spacer(Modifier.height(10.sdp()))
        HistoryMetaRow("提现金额", "¥${item.amountYuan}")
        HistoryMetaRow("提现方式", if (item.channel == "wechat") "微信" else "支付宝")
        HistoryMetaRow("提现时间", formatWithdrawTime(item.createdAt))
        HistoryMetaRow(
            "到账时间",
            when {
                item.status == "success" -> formatWithdrawTime(item.paidAt ?: item.updatedAt)
                item.status == "failed" -> "—"
                else -> "处理中"
            },
        )
    }
}

@Composable
private fun HistoryMetaRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.sdp()),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Stellar.OnSurfaceVariant, fontSize = 12.ssp())
        Text(
            value,
            color = Stellar.OnSurface,
            fontSize = 12.ssp(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.sdp()),
        )
    }
}

@Composable
fun WithdrawChannelBadge(channelId: String, size: androidx.compose.ui.unit.Dp) {
    val wechat = channelId.equals("wechat", ignoreCase = true)
    if (wechat) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(WechatGreen),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.62f)) {
                val w = this.size.width
                val h = this.size.height
                val left = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            Offset(w * 0.05f, h * 0.12f),
                            Size(w * 0.58f, h * 0.52f),
                        ),
                    )
                }
                val right = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            Offset(w * 0.35f, h * 0.28f),
                            Size(w * 0.58f, h * 0.52f),
                        ),
                    )
                }
                drawPath(left, Color.White.copy(alpha = 0.95f))
                drawPath(right, Color.White)
                drawCircle(WechatGreen, radius = w * 0.045f, center = Offset(w * 0.28f, h * 0.36f))
                drawCircle(WechatGreen, radius = w * 0.045f, center = Offset(w * 0.42f, h * 0.36f))
                drawCircle(WechatGreen, radius = w * 0.045f, center = Offset(w * 0.58f, h * 0.52f))
                drawCircle(WechatGreen, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.52f))
            }
        }
    } else {
        Image(
            painter = painterResource(R.drawable.ic_alipay),
            contentDescription = "支付宝",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f)),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun maskPayoutAccount(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return "未设置"
    if (isAlipayAuthIdentity(value)) return "已绑定支付宝"
    if (value.length <= 4) return value
    if (value.contains("@")) {
        val at = value.indexOf('@')
        val name = value.take(at)
        val domain = value.drop(at)
        val head = name.take(2)
        return head + "***" + domain
    }
    if (value.length >= 7 && value.all { it.isDigit() || it == '+' }) {
        val digits = value.filter { it.isDigit() }
        if (digits.length >= 7) return digits.take(3) + "****" + digits.takeLast(4)
    }
    return value.take(2) + "***" + value.takeLast(2)
}

private fun formatWithdrawTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val instant = Instant.parse(iso)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { iso.take(16).replace('T', ' ') }
}
