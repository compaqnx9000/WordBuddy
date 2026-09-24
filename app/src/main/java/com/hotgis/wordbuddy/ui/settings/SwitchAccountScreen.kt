package com.hotgis.wordbuddy.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.data.AccountStore
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.data.RememberedAccount
import com.hotgis.wordbuddy.ui.components.WordBuddyAvatarIcon
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SwitchAccountScreen(
    accounts: List<RememberedAccount>,
    currentUserId: Long?,
    switching: Boolean,
    onBack: () -> Unit,
    onSelectAccount: (RememberedAccount) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        SwitchAccountTopBar(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.sdp())
                .padding(bottom = 28.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.sdp()))
            Image(
                painter = painterResource(R.drawable.ic_wordbuddy_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(56.sdp())
                    .clip(RoundedCornerShape(14.sdp())),
            )
            Spacer(Modifier.height(18.sdp()))
            Text(
                text = "轻触头像以切换账号",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.sdp()))

            accounts.forEach { account ->
                val isCurrent = account.userId == currentUserId
                AccountRow(
                    account = account,
                    isCurrent = isCurrent,
                    enabled = !switching,
                    onClick = {
                        if (isCurrent) onBack() else onSelectAccount(account)
                    },
                )
                Spacer(Modifier.height(12.sdp()))
            }

            AddAccountRow(
                enabled = !switching,
                onClick = onAddAccount,
            )

            if (switching) {
                Spacer(Modifier.height(24.sdp()))
                CircularProgressIndicator(
                    color = Stellar.Cyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.sdp()),
                )
                Spacer(Modifier.height(8.sdp()))
                Text(
                    text = "正在切换…",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                )
            }
        }
    }
}

@Composable
private fun SwitchAccountTopBar(onBack: () -> Unit) {
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Box(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .drawBehind {
                drawLine(
                    color = line,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 12.sdp()),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.sdp())
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.CyanSoft,
                modifier = Modifier.size(18.sdp()),
            )
        }
        Text(
            text = "切换账号",
            modifier = Modifier.align(Alignment.Center),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AccountRow(
    account: RememberedAccount,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .clip(RoundedCornerShape(14.sdp()))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(url = account.avatarUrl)
        Spacer(Modifier.width(12.sdp()))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                color = Stellar.OnSurface,
                fontSize = 17.ssp(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = AccountStore.maskPhone(account.phone),
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                maxLines = 1,
            )
        }
        if (isCurrent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.sdp()),
            ) {
                Box(
                    Modifier
                        .size(8.sdp())
                        .clip(CircleShape)
                        .background(Stellar.Cyan),
                )
                Text(
                    text = "当前使用",
                    color = Stellar.Cyan,
                    fontSize = 13.ssp(),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AddAccountRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    val dashColor = Stellar.OnSurfaceVariant.copy(alpha = 0.45f)
    Row(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .clip(RoundedCornerShape(14.sdp()))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.sdp())
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = dash,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = Stellar.OnSurfaceVariant,
                modifier = Modifier.size(24.sdp()),
            )
        }
        Spacer(Modifier.width(12.sdp()))
        Text(
            text = "添加账号",
            color = Stellar.OnSurface,
            fontSize = 17.ssp(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AccountAvatar(url: String?) {
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
        Modifier
            .size(48.sdp())
            .clip(RoundedCornerShape(10.sdp()))
            .background(Stellar.SurfaceHigh)
            .border(1.dp, Stellar.Outline.copy(alpha = 0.35f), RoundedCornerShape(10.sdp())),
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
            WordBuddyAvatarIcon(
                modifier = Modifier.size(28.sdp()),
                detailTint = Stellar.Cyan,
            )
        }
    }
}
