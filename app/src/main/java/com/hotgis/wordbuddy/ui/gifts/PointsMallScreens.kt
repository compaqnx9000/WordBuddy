package com.hotgis.wordbuddy.ui.gifts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.data.GiftCategory
import com.hotgis.wordbuddy.data.GiftItem
import com.hotgis.wordbuddy.data.GiftOrder
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.launch

private fun parseHexColor(hex: String, fallback: Color = Color(0xFF1B6CA8)): Color {
    val raw = hex.trim().removePrefix("#")
    return runCatching {
        when (raw.length) {
            6 -> Color(android.graphics.Color.parseColor("#$raw"))
            8 -> Color(android.graphics.Color.parseColor("#$raw"))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

@Composable
fun PointsMallScreen(
    totalPoints: Int,
    userName: String,
    loggedIn: Boolean,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenGift: (Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenBuyPoints: () -> Unit = {},
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = remember { WordBuddyApi() }
    var categories by remember { mutableStateOf(listOf(GiftCategory("recommend", "推荐"))) }
    var category by remember { mutableStateOf("recommend") }
    var gifts by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(cat: String) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                if (categories.size <= 1) {
                    categories = api.listGiftCategories().ifEmpty { categories }
                }
                api.listGifts(cat)
            }.onSuccess {
                gifts = it
            }.onFailure {
                error = it.message ?: "加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload(category) }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        MallTopBar(title = "积分兑礼", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 10.sdp()),
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
            verticalArrangement = Arrangement.spacedBy(10.sdp()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }) {
                MallHeader(
                    totalPoints = totalPoints,
                    userName = userName,
                    loggedIn = loggedIn,
                    onOpenOrders = {
                        if (loggedIn) onOpenOrders() else onLogin()
                    },
                    onOpenCheckIn = onOpenCheckIn,
                    onOpenWithdraw = {
                        if (loggedIn) onOpenWithdraw() else onLogin()
                    },
                    onOpenBuyPoints = {
                        if (loggedIn) onOpenBuyPoints() else onLogin()
                    },
                    onPointsOnly = {
                        category = "points_only"
                        reload("points_only")
                    },
                )
            }
            item(span = { GridItemSpan(2) }) {
                CategoryTabs(
                    categories = categories,
                    selected = category,
                    onSelect = {
                        category = it
                        reload(it)
                    },
                )
            }
            when {
                loading -> item(span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth().padding(40.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                error != null -> item(span = { GridItemSpan(2) }) {
                    Text(
                        text = error ?: "",
                        color = Stellar.Pink,
                        fontSize = 14.ssp(),
                        modifier = Modifier.padding(16.sdp()),
                    )
                }
                gifts.isEmpty() -> item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "暂无礼品",
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 14.ssp(),
                        modifier = Modifier.padding(24.sdp()),
                    )
                }
                else -> items(gifts, key = { it.id }) { gift ->
                    GiftCard(gift = gift, onClick = { onOpenGift(gift.id) })
                }
            }
        }
    }
}

@Composable
private fun MallTopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            color = Stellar.CyanSoft,
            fontSize = 18.ssp(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MallHeader(
    totalPoints: Int,
    userName: String,
    loggedIn: Boolean,
    onOpenOrders: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenBuyPoints: () -> Unit,
    onPointsOnly: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.sdp()))
            .background(
                Brush.linearGradient(
                    listOf(
                        Stellar.Cyan.copy(alpha = 0.35f),
                        Stellar.Pink.copy(alpha = 0.18f),
                        Stellar.SurfaceContainer,
                    ),
                ),
            )
            .border(1.dp, Stellar.Cyan.copy(alpha = 0.25f), RoundedCornerShape(18.sdp()))
            .padding(16.sdp()),
    ) {
        Text(
            text = if (loggedIn) userName else "未登录",
            color = Stellar.OnSurface,
            fontSize = 16.ssp(),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.sdp()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的积分", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
            Spacer(Modifier.width(8.sdp()))
            Text(
                text = "$totalPoints",
                color = Stellar.Cyan,
                fontSize = 28.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "我的订单 >",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                modifier = Modifier.clickable(onClick = onOpenOrders),
            )
        }
        Spacer(Modifier.height(14.sdp()))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuickAction(Icons.Outlined.ShoppingCart, "购买积分", onClick = onOpenBuyPoints)
            QuickAction(Icons.Outlined.LocalMall, "0元起兑", onClick = onPointsOnly)
            QuickAction(Icons.Outlined.CardGiftcard, "积分提现", onClick = onOpenWithdraw)
            QuickAction(Icons.Outlined.EventAvailable, "每日签到", onClick = onOpenCheckIn)
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.sdp()))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.sdp(), vertical = 4.sdp()),
    ) {
        Box(
            Modifier
                .size(40.sdp())
                .clip(CircleShape)
                .background(Stellar.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Stellar.Gold, modifier = Modifier.size(20.sdp()))
        }
        Spacer(Modifier.height(4.sdp()))
        Text(label, color = Stellar.OnSurfaceVariant, fontSize = 11.ssp())
    }
}

@Composable
private fun CategoryTabs(
    categories: List<GiftCategory>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.sdp()),
    ) {
        categories.forEach { cat ->
            val active = cat.id == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(cat.id) },
            ) {
                Text(
                    text = cat.name,
                    color = if (active) Stellar.OnSurface else Stellar.OnSurfaceVariant,
                    fontSize = 14.ssp(),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.sdp()))
                Box(
                    Modifier
                        .width(18.sdp())
                        .height(3.sdp())
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) Stellar.Cyan else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun GiftCard(gift: GiftItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .stellarGlass()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(parseHexColor(gift.coverColor)),
            contentAlignment = Alignment.Center,
        ) {
            Text(gift.coverEmoji, fontSize = 44.ssp())
        }
        Column(Modifier.padding(10.sdp())) {
            Text(
                text = gift.title,
                color = Stellar.OnSurface,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(36.sdp()),
            )
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = gift.priceLabel,
                color = Stellar.Pink,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = gift.redeemedLabel,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 11.ssp(),
            )
        }
    }
}

@Composable
fun GiftDetailScreen(
    giftId: Long,
    totalPoints: Int,
    token: String?,
    onBack: () -> Unit,
    onRedeemed: () -> Unit,
    onLogin: () -> Unit,
    initialShippingName: String = "",
    initialShippingPhone: String = "",
    initialShippingDetail: String = "",
    onSaveShipping: (name: String, phone: String, detail: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val api = remember { WordBuddyApi() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gift by remember { mutableStateOf<GiftItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var confirmRedeem by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialShippingName) }
    var phone by remember { mutableStateOf(initialShippingPhone) }
    var detail by remember { mutableStateOf(initialShippingDetail) }

    LaunchedEffect(giftId, initialShippingName, initialShippingPhone, initialShippingDetail) {
        if (name.isBlank() && initialShippingName.isNotBlank()) name = initialShippingName
        if (phone.isBlank() && initialShippingPhone.isNotBlank()) phone = initialShippingPhone
        if (detail.isBlank() && initialShippingDetail.isNotBlank()) detail = initialShippingDetail
    }

    LaunchedEffect(giftId) {
        loading = true
        runCatching { api.fetchGift(giftId) }
            .onSuccess { gift = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "加载失败", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    fun doRedeem() {
        val g = gift ?: return
        val t = token
        if (t.isNullOrBlank()) {
            onLogin()
            return
        }
        if (g.needAddress && (name.isBlank() || phone.isBlank() || detail.isBlank())) {
            showAddress = true
            return
        }
        busy = true
        scope.launch {
            runCatching {
                api.redeemGift(t, g.id, name.ifBlank { null }, phone.ifBlank { null }, detail.ifBlank { null })
            }.onSuccess { result ->
                if (g.needAddress && name.isNotBlank() && phone.isNotBlank() && detail.isNotBlank()) {
                    onSaveShipping(name, phone, detail)
                }
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                onRedeemed()
            }.onFailure {
                Toast.makeText(context, it.message ?: "兑换失败", Toast.LENGTH_LONG).show()
            }
            busy = false
            confirmRedeem = false
            showAddress = false
        }
    }

    val g = gift
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        MallTopBar(title = "礼品详情", onBack = onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Stellar.Cyan)
            }
            g == null -> Text("礼品不存在", color = Stellar.OnSurfaceVariant, modifier = Modifier.padding(24.sdp()))
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f)
                            .background(parseHexColor(g.coverColor)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g.coverEmoji, fontSize = 72.ssp())
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFE85D4C), Color(0xFF3D7EA6)),
                                ),
                            )
                            .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
                    ) {
                        Column {
                            Text(
                                text = g.priceLabel,
                                color = Color.White,
                                fontSize = 22.ssp(),
                                fontWeight = FontWeight.Bold,
                            )
                            if (g.originalPriceYuan != null) {
                                Text(
                                    text = "优惠前 ¥${g.originalPriceYuan}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.ssp(),
                                )
                            }
                            if (g.pointsOffsetYuan != null) {
                                Text(
                                    text = "积分已抵 ${g.pointsOffsetYuan} 元",
                                    color = Color(0xFFFFE08A),
                                    fontSize = 12.ssp(),
                                    modifier = Modifier.padding(top = 4.sdp()),
                                )
                            }
                        }
                    }
                    Column(Modifier.padding(16.sdp())) {
                        Text(
                            text = g.title,
                            color = Stellar.OnSurface,
                            fontSize = 18.ssp(),
                            fontWeight = FontWeight.Bold,
                        )
                        if (g.subtitle.isNotBlank()) {
                            Text(
                                text = g.subtitle,
                                color = Stellar.OnSurfaceVariant,
                                fontSize = 13.ssp(),
                                modifier = Modifier.padding(top = 4.sdp()),
                            )
                        }
                        Spacer(Modifier.height(10.sdp()))
                        Text(
                            text = g.description.ifBlank { "兑换后由词搭子客服处理发货或发放虚拟权益。" },
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 14.ssp(),
                            lineHeight = 20.ssp(),
                        )
                        Spacer(Modifier.height(12.sdp()))
                        Text(
                            text = "当前积分 $totalPoints · ${g.redeemedLabel}",
                            color = Stellar.Cyan,
                            fontSize = 13.ssp(),
                        )
                        if (g.cashFen > 0) {
                            Text(
                                text = "含现金部分：积分先扣，现金需客服确认（暂未开通在线支付）",
                                color = Stellar.Gold,
                                fontSize = 12.ssp(),
                                modifier = Modifier.padding(top = 6.sdp()),
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Stellar.SurfaceContainer)
                        .padding(12.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = g.priceLabel,
                        color = Stellar.Pink,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (busy) "兑换中…" else "立即兑换",
                        color = Stellar.OnPrimary,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (busy) Stellar.SurfaceHigh else Stellar.Pink)
                            .clickable(enabled = !busy) {
                                if (token.isNullOrBlank()) onLogin()
                                else if (g.needAddress) showAddress = true
                                else confirmRedeem = true
                            }
                            .padding(horizontal = 18.sdp(), vertical = 12.sdp()),
                    )
                }
            }
        }
    }

    if (confirmRedeem && g != null) {
        StellarConfirmDialog(
            title = "确认兑换",
            message = "将花费 ${g.priceLabel}\n当前积分 $totalPoints",
            confirmText = "确认",
            dismissText = "取消",
            onDismiss = { confirmRedeem = false },
            onConfirm = { doRedeem() },
        )
    }
    if (showAddress && g != null) {
        AddressDialog(
            name = name,
            phone = phone,
            detail = detail,
            onName = { name = it },
            onPhone = { phone = it },
            onDetail = { detail = it },
            onDismiss = { showAddress = false },
            onConfirm = {
                showAddress = false
                confirmRedeem = true
            },
        )
    }
}

@Composable
private fun AddressDialog(
    name: String,
    phone: String,
    detail: String,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .clip(shape)
                .background(Stellar.SurfaceContainer)
                .border(1.dp, Stellar.Cyan.copy(alpha = 0.4f), shape)
                .padding(20.sdp()),
        ) {
            Text("收货地址", color = Stellar.CyanSoft, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.sdp()))
            Field(label = "收件人", placeholder = "请填写收件人姓名", value = name, onChange = onName)
            Spacer(Modifier.height(8.sdp()))
            Field(label = "电话", placeholder = "请填写联系电话", value = phone, onChange = onPhone)
            Spacer(Modifier.height(8.sdp()))
            Field(label = "地址", placeholder = "省市区 + 详细地址", value = detail, onChange = onDetail)
            Spacer(Modifier.height(16.sdp()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "取消",
                    color = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(10.sdp()),
                )
                Text(
                    "下一步",
                    color = Stellar.OnPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Stellar.Cyan)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 16.sdp(), vertical = 10.sdp()),
                )
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Stellar.OnSurfaceVariant,
            fontSize = 13.ssp(),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.sdp()))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp()))
                .background(Stellar.SurfaceHigh)
                .padding(12.sdp()),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 14.ssp()),
                cursorBrush = SolidColor(Stellar.Cyan),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun GiftOrdersScreen(
    token: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = remember { WordBuddyApi() }
    var orders by remember { mutableStateOf<List<GiftOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { api.listGiftOrders(token) }
            .onSuccess { orders = it }
        loading = false
    }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        MallTopBar(title = "我的订单", onBack = onBack)
        when {
            token.isNullOrBlank() -> {
                Text(
                    "登录后查看兑换订单",
                    color = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .padding(24.sdp())
                        .clickable(onClick = onLogin),
                )
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Stellar.Cyan)
            }
            orders.isEmpty() -> Text(
                "暂无兑换记录",
                color = Stellar.OnSurfaceVariant,
                modifier = Modifier.padding(24.sdp()),
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.sdp()),
                verticalArrangement = Arrangement.spacedBy(10.sdp()),
            ) {
                orders.forEach { order ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.sdp()))
                            .stellarGlass()
                            .padding(12.sdp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(52.sdp())
                                .clip(RoundedCornerShape(10.sdp()))
                                .background(parseHexColor(order.coverColor)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(order.coverEmoji, fontSize = 24.ssp())
                        }
                        Spacer(Modifier.width(10.sdp()))
                        Column(Modifier.weight(1f)) {
                            Text(order.giftTitle, color = Stellar.OnSurface, fontSize = 14.ssp(), maxLines = 2)
                            Text(order.priceLabel, color = Stellar.Pink, fontSize = 12.ssp())
                            Text(order.statusLabel, color = Stellar.Cyan, fontSize = 12.ssp())
                        }
                    }
                }
            }
        }
    }
}
