package com.hotgis.wordbuddy.ui.gifts

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.data.HotWordsApi
import com.hotgis.wordbuddy.data.WithdrawConfig
import com.hotgis.wordbuddy.data.WithdrawalItem
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.launch

@Composable
fun PointsWithdrawScreen(
    totalPoints: Int,
    token: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
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
    var channel by remember { mutableStateOf("alipay") }
    var account by remember { mutableStateOf("") }
    var confirmOpen by remember { mutableStateOf(false) }

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
                            text = "沙箱单笔 ¥${cfg.amountYuan}，消耗 ${cfg.pointsCost} 积分",
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
                    Text(
                        text = item.name,
                        color = Stellar.OnSurface,
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedNow) {
                        Text("已选", color = Stellar.Cyan, fontSize = 13.ssp())
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .stellarGlass()
                        .clip(RoundedCornerShape(14.sdp()))
                        .padding(14.sdp()),
                ) {
                    Text(
                        text = selected?.accountLabel ?: "收款账号",
                        color = Stellar.OnSurface,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.sdp()))
                    BasicTextField(
                        value = account,
                        onValueChange = { if (it.length <= 64) account = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 15.ssp()),
                        cursorBrush = SolidColor(Stellar.Cyan),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.sdp()))
                                    .background(Stellar.SurfaceHigh)
                                    .padding(horizontal = 12.sdp(), vertical = 12.sdp()),
                            ) {
                                if (account.isEmpty()) {
                                    Text(
                                        text = selected?.accountHint ?: "请输入收款账号",
                                        color = Stellar.OnSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 14.ssp(),
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                            if (account.trim().length < 3) {
                                Toast.makeText(context, "请填写收款账号", Toast.LENGTH_SHORT).show()
                            } else if ((cfg?.pointsCost ?: 1) > totalPoints) {
                                Toast.makeText(context, "积分不足", Toast.LENGTH_SHORT).show()
                            } else {
                                confirmOpen = true
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

            item {
                Text(
                    text = "提现记录",
                    color = Stellar.OnSurface,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (history.isEmpty()) {
                item {
                    Text("暂无记录", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                }
            } else {
                items(history, key = { it.id }) { item ->
                    WithdrawHistoryRow(item)
                }
            }
        }
    }

    if (confirmOpen && cfg != null) {
        val channelName = selected?.name ?: "提现"
        StellarConfirmDialog(
            title = "确认提现",
            message = "将消耗 ${cfg.pointsCost} 积分，向${channelName}账号「${account.trim()}」发放 ¥${cfg.amountYuan}。\n\n${cfg.note}",
            confirmText = if (submitting) "提交中…" else "确认",
            dismissText = "取消",
            onDismiss = { if (!submitting) confirmOpen = false },
            onConfirm = {
                if (submitting) return@StellarConfirmDialog
                val t = token ?: return@StellarConfirmDialog
                submitting = true
                scope.launch {
                    runCatching {
                        api.createWithdrawal(t, channel, account.trim())
                    }.onSuccess { result ->
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        confirmOpen = false
                        account = ""
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
private fun WithdrawHistoryRow(item: WithdrawalItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .clip(RoundedCornerShape(12.sdp()))
            .padding(12.sdp()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.channelLabel.ifBlank { item.channel },
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.Medium,
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
        Spacer(Modifier.height(4.sdp()))
        Text(
            text = "¥${item.amountYuan} · ${item.pointsSpent}积分 · ${item.account}",
            color = Stellar.OnSurfaceVariant,
            fontSize = 12.ssp(),
        )
        if (!item.providerTradeNo.isNullOrBlank()) {
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = item.providerTradeNo,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 11.ssp(),
            )
        }
    }
}
