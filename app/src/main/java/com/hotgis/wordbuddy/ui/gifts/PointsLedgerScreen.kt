package com.hotgis.wordbuddy.ui.gifts

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.hotgis.wordbuddy.data.PointsLedgerEntry
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PointsLedgerScreen(
    token: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { WordBuddyApi() }
    val scope = rememberCoroutineScope()
    var balance by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<PointsLedgerEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    fun load(nextPage: Int) {
        val auth = token
        if (auth.isNullOrBlank()) {
            loading = false
            return
        }
        if (nextPage == 1) loading = true else loadingMore = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.fetchPointsLedger(auth, nextPage) }
            }.onSuccess { result ->
                balance = result.balance
                total = result.total
                page = result.page
                items = if (nextPage == 1) result.items else items + result.items
            }.onFailure {
                Toast.makeText(context, it.message ?: "加载搭币明细失败", Toast.LENGTH_SHORT).show()
            }
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) onLogin() else load(1)
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
                text = "搭币明细",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.sdp()),
            verticalArrangement = Arrangement.spacedBy(10.sdp()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text("当前搭币", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                Text(
                    text = "$balance",
                    color = Stellar.Cyan,
                    fontSize = 32.ssp(),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.sdp()))
                Text(
                    text = "收入和支出都会记在这里。",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 12.ssp(),
                )
            }
            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                items.isEmpty() -> item {
                    Text("还没有搭币记录", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                }
                else -> {
                    items(items, key = { it.id }) { entry ->
                        LedgerRow(entry)
                    }
                    if (items.size < total) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.sdp()), contentAlignment = Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(color = Stellar.Cyan, modifier = Modifier.size(22.sdp()))
                                } else {
                                    Text(
                                        text = "加载更多",
                                        color = Stellar.Cyan,
                                        fontSize = 14.ssp(),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.sdp()))
                                            .clickable { load(page + 1) }
                                            .padding(horizontal = 16.sdp(), vertical = 8.sdp()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: PointsLedgerEntry) {
    val positive = entry.delta >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .stellarGlass()
            .padding(14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Stellar.OnSurface, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
            if (!entry.detail.isNullOrBlank()) {
                Spacer(Modifier.height(2.sdp()))
                Text(entry.detail, color = Stellar.OnSurfaceVariant, fontSize = 12.ssp())
            }
            Spacer(Modifier.height(4.sdp()))
            Text(formatLedgerTime(entry.createdAt), color = Stellar.OnSurfaceVariant, fontSize = 12.ssp())
        }
        Spacer(Modifier.width(12.sdp()))
        Text(
            text = if (positive) "+${entry.delta}" else "${entry.delta}",
            color = if (positive) Stellar.Cyan else Stellar.Pink,
            fontSize = 18.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatLedgerTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val zoned = Instant.parse(iso).atZone(ZoneId.of("Asia/Shanghai"))
        "%02d-%02d %02d:%02d".format(zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute)
    }.getOrDefault("")
}
