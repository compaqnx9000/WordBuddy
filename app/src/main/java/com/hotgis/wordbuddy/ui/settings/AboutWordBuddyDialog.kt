package com.hotgis.wordbuddy.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.BuildConfig
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.data.AppUpdateInfo
import com.hotgis.wordbuddy.data.AppUpdater
import com.hotgis.wordbuddy.data.HotWordsApi
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AboutWordBuddyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val api = remember { HotWordsApi() }
    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailMessage by remember { mutableStateOf("") }
    var detailConfirm by remember { mutableStateOf("知道了") }
    var detailDismiss by remember { mutableStateOf("") }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }

    fun installPendingApk(file: File) {
        if (!AppUpdater.canRequestInstall(context)) {
            activity?.let { AppUpdater.openInstallPermissionSettings(it) }
                ?: Toast.makeText(context, "请允许安装未知应用后再试", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { AppUpdater.installApk(context, file) }
            .onFailure {
                Toast.makeText(context, it.message ?: "无法打开安装程序", Toast.LENGTH_LONG).show()
            }
    }

    fun startInAppDownload(info: AppUpdateInfo) {
        if (updateDownloading) return
        updateDownloading = true
        updateProgress = 0f
        Toast.makeText(context, "开始下载 ${info.versionName}…", Toast.LENGTH_SHORT).show()
        scope.launch {
            runCatching {
                AppUpdater.downloadApk(context, info) { progress ->
                    scope.launch(Dispatchers.Main.immediate) { updateProgress = progress }
                }
            }.onSuccess { file ->
                updateDownloading = false
                Toast.makeText(context, "下载完成，正在打开安装…", Toast.LENGTH_SHORT).show()
                installPendingApk(file)
            }.onFailure { error ->
                updateDownloading = false
                Toast.makeText(context, error.message ?: "下载失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    detailTitle?.let { title ->
        StellarConfirmDialog(
            title = title,
            message = detailMessage,
            confirmText = detailConfirm,
            dismissText = detailDismiss,
            onDismiss = {
                detailTitle = null
                pendingUpdate = null
            },
            onConfirm = {
                val info = pendingUpdate
                detailTitle = null
                pendingUpdate = null
                if (info != null) startInAppDownload(info)
            },
        )
    }

    fun checkForUpdate() {
        if (checkingUpdate || updateDownloading) return
        checkingUpdate = true
        Toast.makeText(context, "正在检测更新…", Toast.LENGTH_SHORT).show()
        scope.launch {
            runCatching { AppUpdater.fetchLatest(api) }
                .onSuccess { info ->
                    if (info.hasUpdate) {
                        detailTitle = "发现新版本"
                        detailConfirm = "立即更新"
                        detailDismiss = "稍后"
                        pendingUpdate = info
                        detailMessage = buildString {
                            append("当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n")
                            append("最新版本 ${info.versionName}（${info.versionCode}）\n\n")
                            if (info.notes.isNotBlank()) {
                                append(info.notes)
                                append("\n\n")
                            }
                            append("将在应用内下载并安装，无需打开浏览器。")
                        }
                    } else {
                        detailTitle = "检测更新"
                        detailConfirm = "知道了"
                        detailDismiss = ""
                        pendingUpdate = null
                        detailMessage =
                            "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n\n已是最新版本。"
                    }
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message?.ifBlank { "检测更新失败" } ?: "检测更新失败",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            checkingUpdate = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .stellarScreenBackground(),
        ) {
            AboutTopBar(onBack = onDismiss)
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
                AboutBrandHeader()
                Spacer(Modifier.height(28.sdp()))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .stellarGlass()
                        .padding(vertical = 2.sdp()),
                ) {
                    AboutMenuRow(
                        title = "功能介绍",
                        onClick = {
                            detailTitle = "功能介绍"
                            detailConfirm = "知道了"
                            detailDismiss = ""
                            pendingUpdate = null
                            detailMessage =
                                "词搭子帮你查词、收藏、卡片背诵。\n\n" +
                                    "· 首页快速查词，查看音标、释义与例句\n" +
                                    "· 收藏到生词本，支持导入导出\n" +
                                    "· 卡片模式复习，可朗读与切换英音美音\n" +
                                    "· 内置四级 / 六级词书，随时开背"
                        },
                    )
                    AboutMenuDivider()
                    AboutMenuRow(
                        title = "投诉",
                        onClick = {
                            detailTitle = "投诉"
                            detailConfirm = "知道了"
                            detailDismiss = ""
                            pendingUpdate = null
                            detailMessage =
                                "如遇内容错误、体验问题或违规信息，请通过「帮助与反馈」联系我们，或发送邮件至 hi@wordbuddy.cc。\n\n我们会尽快核实处理。"
                        },
                    )
                    AboutMenuDivider()
                    AboutMenuRow(
                        title = when {
                            updateDownloading -> "正在下载 ${(updateProgress * 100).toInt()}%"
                            checkingUpdate -> "检测更新中…"
                            else -> "检测更新"
                        },
                        onClick = ::checkForUpdate,
                    )
                }

                if (updateDownloading) {
                    Spacer(Modifier.height(12.sdp()))
                    LinearProgressIndicator(
                        progress = { updateProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Stellar.Cyan,
                        trackColor = Stellar.SurfaceHigh,
                    )
                }

                Spacer(Modifier.height(36.sdp()))
                AboutLegalFooter(
                    onOpenAgreement = {
                        detailTitle = "软件许可及服务协议"
                        detailConfirm = "知道了"
                        detailDismiss = ""
                        pendingUpdate = null
                        detailMessage =
                            "使用词搭子即表示你同意遵守本软件的使用规范。请勿将本应用用于违法用途。词库与查词服务可能依赖第三方数据源，结果仅供学习参考。"
                    },
                    onOpenPrivacySummary = {
                        detailTitle = "隐私保护指引摘要"
                        detailConfirm = "知道了"
                        detailDismiss = ""
                        pendingUpdate = null
                        detailMessage =
                            "我们仅收集账号登录与学习所需的最少信息（如手机号、词库数据）。不会出售你的个人信息。详细说明见《隐私保护指引》。"
                    },
                    onOpenPrivacy = {
                        detailTitle = "隐私保护指引"
                        detailConfirm = "知道了"
                        detailDismiss = ""
                        pendingUpdate = null
                        detailMessage =
                            "词搭子会本地缓存部分词条以便离线浏览，并在登录后将你的生词本同步至服务器。你可以随时退出登录清除本机缓存。\n\n如需删除账号数据，请联系 hi@wordbuddy.cc。"
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
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
            text = "关于词搭子",
            modifier = Modifier.align(Alignment.Center),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AboutBrandHeader() {
    val logoShape = RoundedCornerShape(22.sdp())
    Image(
        painter = painterResource(R.drawable.ic_wordbuddy_logo),
        contentDescription = "词搭子",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .shadow(
                elevation = 16.sdp(),
                shape = logoShape,
                ambientColor = Stellar.Cyan.copy(alpha = 0.4f),
                spotColor = Stellar.Cyan.copy(alpha = 0.3f),
            )
            .size(88.sdp())
            .clip(logoShape),
    )
    Spacer(Modifier.height(16.sdp()))
    Text(
        text = "词搭子",
        color = Stellar.CyanSoft,
        fontSize = 24.ssp(),
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.sdp()))
    Text(
        text = "Version ${BuildConfig.VERSION_NAME}",
        color = Stellar.OnSurfaceVariant,
        fontSize = 14.ssp(),
    )
}

@Composable
private fun AboutMenuRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp(), vertical = 16.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Stellar.OnSurface,
            fontSize = 16.ssp(),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(20.sdp()),
        )
    }
}

@Composable
private fun AboutMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Stellar.Outline.copy(alpha = 0.45f),
    )
}

@Composable
private fun AboutLegalFooter(
    onOpenAgreement: () -> Unit,
    onOpenPrivacySummary: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.sdp()),
    ) {
        AboutLegalLink("《软件许可及服务协议》", onOpenAgreement)
        AboutLegalLink("《隐私保护指引摘要》", onOpenPrivacySummary)
        AboutLegalLink("《隐私保护指引》", onOpenPrivacy)
        Spacer(Modifier.height(6.sdp()))
        Text(
            text = "客服邮箱：hi@wordbuddy.cc",
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.75f),
            fontSize = 12.ssp(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "HotGIS 版权所有\nCopyright © 2024-$year HotGIS. All Rights Reserved.",
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 11.ssp(),
            textAlign = TextAlign.Center,
            lineHeight = 16.ssp(),
        )
    }
}

@Composable
private fun AboutLegalLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Stellar.Cyan,
        fontSize = 13.ssp(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
