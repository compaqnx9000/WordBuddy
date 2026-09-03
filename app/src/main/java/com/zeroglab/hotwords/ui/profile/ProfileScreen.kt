package com.zeroglab.hotwords.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroglab.hotwords.BuildConfig
import com.zeroglab.hotwords.data.NotebookImportResult
import com.zeroglab.hotwords.ui.components.StellarConfirmDialog
import com.zeroglab.hotwords.ui.components.WordBuddyAvatarIcon
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.stellarGlass
import com.zeroglab.hotwords.ui.lookup.stellarPanelBackgroundColor
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground
import com.zeroglab.hotwords.ui.settings.AboutWordBuddyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    wordCount: Int,
    userName: String,
    exportFileName: String,
    onOpenSettings: () -> Unit,
    onExportContent: () -> String,
    onImportContent: suspend (String) -> NotebookImportResult,
    phone: String? = null,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val loggedIn = !phone.isNullOrBlank()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = onExportContent()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取文件")
                }
                val result = onImportContent(json)
                Toast.makeText(
                    context,
                    "导入完成：新增 ${result.added} 个，更新 ${result.updated} 个",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showHelp) {
        StellarConfirmDialog(
            title = "帮助与反馈",
            message = "使用中遇到问题，可通过应用商店评论反馈，或联系 support@zeroglab.com。\n\n我们会持续改进查词、收藏与背诵体验。",
            confirmText = "知道了",
            dismissText = "",
            onDismiss = { showHelp = false },
            onConfirm = { showHelp = false },
        )
    }
    if (showAbout) {
        AboutWordBuddyDialog(onDismiss = { showAbout = false })
    }
    if (showLogoutConfirm) {
        StellarConfirmDialog(
            title = "退出登录",
            message = "退出后将清除本机登录状态与词库缓存，需要重新登录。",
            confirmText = "退出",
            destructive = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.sdp()),
    ) {
        ProfileTopBar()

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.sdp()),
            verticalArrangement = Arrangement.spacedBy(14.sdp()),
        ) {
            ProfileHeroCard(
                userName = userName,
                phone = phone,
                wordCount = wordCount,
                onAccountClick = {
                    if (loggedIn) {
                        Toast.makeText(context, "账号：$phone", Toast.LENGTH_SHORT).show()
                    } else {
                        onLogin()
                    }
                },
                onOpenSettings = onOpenSettings,
            )

            // 原有内容保留：导出 / 导入 / 帮助
            ProfileMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.FileUpload,
                    iconTint = Stellar.Cyan,
                    title = "导出词库",
                    trailing = "不含图片和发音",
                    onClick = { exportLauncher.launch(exportFileName) },
                )
                ProfileMenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.FileDownload,
                    iconTint = Stellar.Pink,
                    title = "导入词库",
                    trailing = "合并导入",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                )
                ProfileMenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.HelpOutline,
                    iconTint = Stellar.Gold,
                    title = "帮助与反馈",
                    onClick = { showHelp = true },
                )
            }

            ProfileActionPill(
                icon = if (loggedIn) Icons.AutoMirrored.Outlined.Logout else Icons.Outlined.Person,
                title = if (loggedIn) "退出" else "登录 / 注册",
                tint = if (loggedIn) Stellar.Pink else Stellar.Cyan,
                onClick = {
                    if (loggedIn) showLogoutConfirm = true else onLogin()
                },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.sdp(), bottom = 8.sdp()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.sdp()),
            ) {
                Text(
                    text = "关于词搭子",
                    color = Stellar.Cyan,
                    fontSize = 13.ssp(),
                    modifier = Modifier.clickable { showAbout = true },
                )
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 12.ssp(),
                )
                Text(
                    text = "查词、收藏、卡片背诵。词库在服务器，本机只做分页缓存。",
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 11.ssp(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 4.sdp()),
                )
            }
        }
    }
}

@Composable
private fun ProfileTopBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "我的",
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProfileHeroCard(
    userName: String,
    phone: String?,
    wordCount: Int,
    onAccountClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val cardShape = RoundedCornerShape(20.sdp())
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                ambientColor = Stellar.Cyan.copy(alpha = 0.2f),
                spotColor = Stellar.Cyan.copy(alpha = 0.15f),
            )
            .clip(cardShape)
            .stellarGlass(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.sdp())
                .background(
                    Brush.linearGradient(
                        listOf(
                            Stellar.Cyan.copy(alpha = 0.55f),
                            Stellar.Pink.copy(alpha = 0.35f),
                            Stellar.CyanBright.copy(alpha = 0.4f),
                        ),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
                val color = Color.White.copy(alpha = 0.22f)
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                    style = stroke,
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.85f,
                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                    style = stroke,
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width * 0.92f, size.height * 1.1f),
                    style = stroke,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.sdp())
                .padding(bottom = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .offset(y = (-36).sdp())
                    .size(84.sdp()),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(3.dp, Stellar.SurfaceContainer, CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Stellar.Cyan.copy(alpha = 0.9f),
                                    Stellar.CyanBright.copy(alpha = 0.7f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    WordBuddyAvatarIcon(
                        modifier = Modifier.size(48.sdp()),
                        detailTint = Stellar.Cyan,
                    )
                }
                Box(
                    Modifier
                        .size(26.sdp())
                        .clip(CircleShape)
                        .background(Stellar.SurfaceHigh)
                        .border(1.dp, Stellar.Outline.copy(alpha = 0.4f), CircleShape)
                        .clickable(onClick = onAccountClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "账号",
                        tint = Stellar.OnSurfaceVariant,
                        modifier = Modifier.size(14.sdp()),
                    )
                }
            }

            Text(
                text = userName,
                color = Stellar.OnSurface,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = (-22).sdp()),
            )
            Text(
                text = phone?.let { "@$it" } ?: "@未登录",
                color = Stellar.Cyan,
                fontSize = 14.ssp(),
                modifier = Modifier.offset(y = (-16).sdp()),
            )
            Text(
                text = "已收藏 $wordCount 词 · 学习中",
                color = Stellar.OnSurfaceVariant,
                fontSize = 12.ssp(),
                modifier = Modifier.offset(y = (-10).sdp()),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.sdp()),
                horizontalArrangement = Arrangement.spacedBy(10.sdp()),
            ) {
                ProfilePillButton(
                    icon = Icons.Outlined.Person,
                    label = if (phone.isNullOrBlank()) "登录账号" else "账号信息",
                    onClick = onAccountClick,
                    modifier = Modifier.weight(1f),
                )
                ProfilePillButton(
                    icon = Icons.Outlined.Settings,
                    label = "设置",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProfilePillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Stellar.Outline.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .background(Stellar.SurfaceHigh.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Stellar.OnSurface, modifier = Modifier.size(16.sdp()))
        Spacer(Modifier.width(6.sdp()))
        Text(label, color = Stellar.OnSurface, fontSize = 13.ssp(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileMenuCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(vertical = 2.sdp()),
        content = { content() },
    )
}

@Composable
private fun ProfileMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.sdp()),
        thickness = 0.5.dp,
        color = Stellar.Outline.copy(alpha = 0.45f),
    )
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.sdp())
                .clip(RoundedCornerShape(8.sdp()))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.sdp()))
        }
        Spacer(Modifier.width(12.sdp()))
        Text(
            text = title,
            color = Stellar.OnSurface,
            fontSize = 16.ssp(),
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 13.ssp(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.sdp()),
            )
        }
    }
}

@Composable
private fun ProfileActionPill(
    icon: ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp()))
            .stellarGlass()
            .clickable(onClick = onClick)
            .padding(vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.sdp()))
        Spacer(Modifier.width(8.sdp()))
        Text(title, color = tint, fontSize = 16.ssp(), fontWeight = FontWeight.Medium)
    }
}
