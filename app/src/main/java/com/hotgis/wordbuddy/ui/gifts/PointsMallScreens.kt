package com.hotgis.wordbuddy.ui.gifts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onOpenWithdraw: () -> Unit = {},
    onLogin: () -> Unit,
    token: String? = null,
    streakDays: Int = 0,
    checkedInToday: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val api = remember { WordBuddyApi() }
    var categories by remember { mutableStateOf(listOf(GiftCategory("recommend", "推荐"))) }
    var category by remember { mutableStateOf("recommend") }
    var gifts by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(cat: String = category) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val tabs = api.listGiftCategories().ifEmpty {
                    listOf(GiftCategory("recommend", "推荐"))
                }
                categories = tabs
                val selected = if (tabs.any { it.id == cat }) cat else tabs.first().id
                category = selected
                api.listGifts(selected, token = token)
            }.onSuccess {
                gifts = it
            }.onFailure {
                error = it.message ?: "加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(token) { reload() }

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
                    streakDays = streakDays,
                    checkedInToday = checkedInToday,
                    onOpenOrders = {
                        if (loggedIn) onOpenOrders() else onLogin()
                    },
                    onOpenWithdraw = {
                        if (loggedIn) onOpenWithdraw() else onLogin()
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
    streakDays: Int,
    checkedInToday: Boolean,
    onOpenOrders: () -> Unit,
    onOpenWithdraw: () -> Unit,
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
        Spacer(Modifier.height(12.sdp()))
        Text(
            text = "支付宝提现",
            color = Stellar.OnPrimary,
            fontSize = 13.ssp(),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Stellar.Cyan)
                .clickable(onClick = onOpenWithdraw)
                .padding(horizontal = 14.sdp(), vertical = 8.sdp()),
        )
        Spacer(Modifier.height(14.sdp()))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MallStat(value = if (loggedIn) "${streakDays}天" else "—", caption = "连续签到")
            MallStat(
                value = if (!loggedIn) "—" else if (checkedInToday) "已签到" else "未签到",
                caption = "今日",
            )
        }
    }
}

@Composable
private fun MallStat(value: String, caption: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.sdp(), vertical = 4.sdp()),
    ) {
        Box(
            Modifier.height(40.sdp()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                color = Stellar.CyanSoft,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.sdp()))
        Text(caption, color = Stellar.OnSurfaceVariant, fontSize = 11.ssp())
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
private fun RemoteGiftImage(
    url: String?,
    fallbackEmoji: String,
    fallbackColor: Color,
    emojiSizeSp: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = WordBuddyApi().fetchAvatarBytes(url)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(fallbackColor),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(fallbackEmoji, fontSize = emojiSizeSp)
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
        RemoteGiftImage(
            url = gift.coverImage ?: gift.bannerImages.firstOrNull(),
            fallbackEmoji = gift.coverEmoji,
            fallbackColor = parseHexColor(gift.coverColor),
            emojiSizeSp = 44.ssp(),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
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
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val api = remember { WordBuddyApi() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gift by remember { mutableStateOf<GiftItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var missingAddress by remember { mutableStateOf(false) }
    var confirmRedeem by remember { mutableStateOf(false) }
    val savedName = initialShippingName.trim()
    val savedPhone = initialShippingPhone.trim()
    val savedDetail = initialShippingDetail.trim()
    val hasShipping = savedName.isNotEmpty() && savedPhone.isNotEmpty() && savedDetail.isNotEmpty()

    LaunchedEffect(giftId, token) {
        loading = true
        runCatching { api.fetchGift(giftId, token) }
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
        busy = true
        scope.launch {
            val shipName = if (g.needAddress) savedName else null
            val shipPhone = if (g.needAddress) savedPhone else null
            val shipDetail = if (g.needAddress) savedDetail else null
            runCatching {
                api.redeemGift(t, g.id, shipName, shipPhone, shipDetail)
            }.onSuccess { result ->
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                onRedeemed()
            }.onFailure {
                Toast.makeText(context, it.message ?: "兑换失败", Toast.LENGTH_LONG).show()
            }
            busy = false
            confirmRedeem = false
        }
    }

    fun beginRedeem() {
        val g = gift ?: return
        if (token.isNullOrBlank()) {
            onLogin()
            return
        }
        if (g.needAddress && !hasShipping) {
            missingAddress = true
            return
        }
        confirmRedeem = true
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
                    val bannerImages = g.bannerImages
                    if (bannerImages.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { bannerImages.size })
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                RemoteGiftImage(
                                    url = bannerImages[page],
                                    fallbackEmoji = g.coverEmoji,
                                    fallbackColor = parseHexColor(g.coverColor),
                                    emojiSizeSp = 72.ssp(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (bannerImages.size > 1) {
                                Row(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 10.sdp()),
                                    horizontalArrangement = Arrangement.spacedBy(6.sdp()),
                                ) {
                                    repeat(bannerImages.size) { index ->
                                        Box(
                                            Modifier
                                                .size(if (pagerState.currentPage == index) 8.sdp() else 6.sdp())
                                                .clip(CircleShape)
                                                .background(
                                                    if (pagerState.currentPage == index) {
                                                        Color.White
                                                    } else {
                                                        Color.White.copy(alpha = 0.45f)
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        RemoteGiftImage(
                            url = null,
                            fallbackEmoji = g.coverEmoji,
                            fallbackColor = parseHexColor(g.coverColor),
                            emojiSizeSp = 72.ssp(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f),
                        )
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
                            .clickable(enabled = !busy) { beginRedeem() }
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
    if (missingAddress) {
        StellarConfirmDialog(
            title = "请先填写收货地址",
            message = "兑换需要收货地址。请先到个人资料中填写收件人、电话和地址，再回来兑换。",
            confirmText = "去填写",
            dismissText = "取消",
            onDismiss = { missingAddress = false },
            onConfirm = {
                missingAddress = false
                onOpenProfile()
            },
        )
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
