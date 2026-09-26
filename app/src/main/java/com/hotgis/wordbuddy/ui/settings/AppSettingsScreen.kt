package com.hotgis.wordbuddy.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import android.widget.Toast
import com.hotgis.wordbuddy.data.AccentStyle
import com.hotgis.wordbuddy.data.AppTheme
import com.hotgis.wordbuddy.data.ImageGenProvider
import com.hotgis.wordbuddy.data.Notebook
import com.hotgis.wordbuddy.data.StudySettings
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.StellarPalettes
import com.hotgis.wordbuddy.ui.lookup.hasStellarWallpaperBackground
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hotgis.wordbuddy.BuildConfig
import com.hotgis.wordbuddy.ads.AdDiagStore
import com.hotgis.wordbuddy.ads.DrawFeedController
import com.hotgis.wordbuddy.ads.RewardVideoController
import com.hotgis.wordbuddy.ads.findActivity
import com.hotgis.wordbuddy.media.MediaDiskCaches
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.runtime.LaunchedEffect
import com.hotgis.wordbuddy.ui.profile.ChangePasswordDialog

@AndroidXOptIn(UnstableApi::class)
@Composable
fun AppSettingsScreen(
    settings: StudySettings,
    notebooks: List<Notebook>,
    onBack: () -> Unit,
    onChange: ((StudySettings) -> StudySettings) -> Unit,
    onLogout: () -> Unit = {},
    onSwitchAccount: () -> Unit = {},
    loggedIn: Boolean = false,
    onBiometricLoginChange: (Boolean) -> Unit = {},
    onChangePassword: (
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (Result<Unit>) -> Unit,
    ) -> Unit = { _, _, _, _ -> },
    deletionPending: Boolean = false,
    onOpenAccountDeletion: () -> Unit = {},
    modifier: Modifier = Modifier,
    title: String = "设置",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheBusy by remember { mutableStateOf(false) }
    var cacheLabel by remember { mutableStateOf("计算中…") }
    var showChangePassword by remember { mutableStateOf(false) }
    var changePasswordBusy by remember { mutableStateOf(false) }
    var changePasswordError by remember { mutableStateOf<String?>(null) }

    fun refreshCacheSize() {
        scope.launch {
            val label = withContext(Dispatchers.IO) {
                MediaDiskCaches.formatSize(MediaDiskCaches.usedBytes(context))
            }
            cacheLabel = label
        }
    }

    LaunchedEffect(Unit) { refreshCacheSize() }

    if (showLogoutConfirm) {
        StellarConfirmDialog(
            title = "退出登录",
            message = "退出后将清除本机登录状态与词库缓存，可继续以游客身份使用。再次使用需重新登录。",
            confirmText = "退出",
            destructive = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
        )
    }

    if (showClearCacheConfirm) {
        StellarConfirmDialog(
            title = "清除本地缓存",
            message = "将清除短视频与播客的本地媒体缓存（约 $cacheLabel）。下次播放会重新从网络加载。不影响账号与词库数据。",
            confirmText = if (cacheBusy) "清除中…" else "清除",
            destructive = true,
            onDismiss = { if (!cacheBusy) showClearCacheConfirm = false },
            onConfirm = {
                if (cacheBusy) return@StellarConfirmDialog
                cacheBusy = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        MediaDiskCaches.clearAll(context)
                    }
                    cacheBusy = false
                    showClearCacheConfirm = false
                    refreshCacheSize()
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            busy = changePasswordBusy,
            error = changePasswordError,
            onDismiss = {
                if (!changePasswordBusy) {
                    showChangePassword = false
                    changePasswordError = null
                }
            },
            onConfirm = { oldPassword, newPassword, confirmPassword ->
                changePasswordBusy = true
                changePasswordError = null
                onChangePassword(oldPassword, newPassword, confirmPassword) { result ->
                    changePasswordBusy = false
                    result
                        .onSuccess {
                            showChangePassword = false
                            Toast.makeText(context, "密码已更新", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            changePasswordError = it.message ?: "修改失败"
                        }
                }
            },
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        if (!hasStellarWallpaperBackground()) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-40).sdp(), y = (-60).sdp())
                    .size(220.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Pink.copy(alpha = 0.12f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 50.sdp(), y = 40.sdp())
                    .size(260.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Cyan.copy(alpha = 0.10f)),
            )
        }

        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(title = title, onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.sdp())
                    .padding(bottom = 28.sdp()),
                verticalArrangement = Arrangement.spacedBy(16.sdp()),
            ) {
                Column(Modifier.padding(top = 8.sdp(), bottom = 8.sdp())) {
                    Text(
                        text = "配置你的沉浸式学习体验。",
                        color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 16.ssp(),
                    )
                }

                AestheticsCard(
                    selected = settings.accentStyle,
                    onSelect = { style ->
                        onChange {
                            it.copy(
                                accentStyle = style,
                                appTheme = if (style == AccentStyle.Frost) AppTheme.Light else AppTheme.Dark,
                            )
                        }
                    },
                )

                PreferencesCard(
                    autoPronounce = settings.speakOnPageChange,
                    dailyReminder = settings.dailyReminder,
                    aiImageAutoGen = settings.aiImageAutoGen,
                    imageProvider = settings.imageProvider,
                    podcastPlayWhenScreenOff = settings.podcastPlayWhenScreenOff,
                    shortsMetaVisibleDefault = settings.shortsMetaVisibleDefault,
                    notebooks = notebooks,
                    defaultNotebookId = settings.defaultNotebookId,
                    onAutoPronounce = { enabled -> onChange { it.copy(speakOnPageChange = enabled) } },
                    onDailyReminder = { enabled -> onChange { it.copy(dailyReminder = enabled) } },
                    onAiImageAutoGen = { enabled -> onChange { it.copy(aiImageAutoGen = enabled) } },
                    onImageProvider = { provider -> onChange { it.copy(imageProvider = provider) } },
                    onPodcastPlayWhenScreenOff = { enabled ->
                        onChange { it.copy(podcastPlayWhenScreenOff = enabled) }
                    },
                    onShortsMetaVisibleDefault = { enabled ->
                        onChange { it.copy(shortsMetaVisibleDefault = enabled) }
                    },
                    onDefaultNotebook = { id -> onChange { it.copy(defaultNotebookId = id) } },
                )

                StorageCacheCard(
                    cacheLabel = cacheLabel,
                    busy = cacheBusy,
                    onClear = { showClearCacheConfirm = true },
                )

                AdDiagCard()

                if (loggedIn) {
                    AccountSecurityCard(
                        biometricLogin = settings.biometricLogin,
                        onBiometricLogin = onBiometricLoginChange,
                        onChangePassword = {
                            changePasswordError = null
                            showChangePassword = true
                        },
                        deletionPending = deletionPending,
                        onOpenAccountDeletion = onOpenAccountDeletion,
                    )

                    SettingsActionGroup {
                        SettingsActionRow(
                            title = "切换账号",
                            onClick = onSwitchAccount,
                        )
                        SettingsGroupDivider()
                        SettingsActionRow(
                            title = "退出登录",
                            onClick = { showLogoutConfirm = true },
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(vertical = 2.sdp()),
        content = { content() },
    )
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Stellar.Outline.copy(alpha = 0.45f),
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.sdp()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (destructive) Stellar.Pink else Stellar.OnSurface,
            fontSize = 16.ssp(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AestheticsCard(
    selected: AccentStyle,
    onSelect: (AccentStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                tint = Stellar.Pink,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "外观主题",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(14.sdp()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前主题：",
                color = Stellar.OnSurfaceVariant,
                fontSize = 15.ssp(),
            )
            Spacer(Modifier.width(8.sdp()))
            Text(
                text = selected.label,
                color = Color(selected.swatchArgb),
                fontSize = 15.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.sdp()))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
        ) {
            AccentStyle.entries.forEach { style ->
                ThemeSwatch(
                    color = Color(style.swatchArgb),
                    thumbnailRes = StellarPalettes.forStyle(style).backgroundImageRes,
                    selected = style == selected,
                    onClick = { onSelect(style) },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailRes: Int? = null,
) {
    val shape = CircleShape
    Box(
        Modifier
            .size(if (selected) 42.sdp() else 38.sdp())
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = color.copy(alpha = 0.55f),
                        spotColor = color.copy(alpha = 0.55f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (thumbnailRes != null) {
                    Modifier
                } else {
                    Modifier.background(color)
                },
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, stellarScreenBackgroundColor(), shape)
                        .border(3.dp, color, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailRes != null) {
            Image(
                painter = painterResource(thumbnailRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PreferencesCard(
    autoPronounce: Boolean,
    dailyReminder: Boolean,
    aiImageAutoGen: Boolean,
    imageProvider: ImageGenProvider,
    podcastPlayWhenScreenOff: Boolean,
    shortsMetaVisibleDefault: Boolean,
    notebooks: List<Notebook>,
    defaultNotebookId: Long,
    onAutoPronounce: (Boolean) -> Unit,
    onDailyReminder: (Boolean) -> Unit,
    onAiImageAutoGen: (Boolean) -> Unit,
    onImageProvider: (ImageGenProvider) -> Unit,
    onPodcastPlayWhenScreenOff: (Boolean) -> Unit,
    onShortsMetaVisibleDefault: (Boolean) -> Unit,
    onDefaultNotebook: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "偏好设置",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.sdp()))
        ImageProviderPicker(
            selected = imageProvider,
            onSelect = onImageProvider,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "自动朗读",
            subtitle = "翻到生词时自动播放发音",
            checked = autoPronounce,
            accentOnHover = Stellar.Cyan,
            onChecked = onAutoPronounce,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.Notifications,
            title = "每日提醒",
            subtitle = "提醒你坚持背单词",
            checked = dailyReminder,
            accentOnHover = Stellar.Cyan,
            onChecked = onDailyReminder,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.Headset,
            title = "息屏后仍可后台播放",
            subtitle = "关闭后锁屏即暂停播客/电台，默认关闭",
            checked = podcastPlayWhenScreenOff,
            accentOnHover = Stellar.Cyan,
            onChecked = onPodcastPlayWhenScreenOff,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.Visibility,
            title = "短视频默认显示文案",
            subtitle = "关闭后播放时默认藏文案，仍可单条点开",
            checked = shortsMetaVisibleDefault,
            accentOnHover = Stellar.Cyan,
            onChecked = onShortsMetaVisibleDefault,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.AutoAwesome,
            title = "AI 自动生成配图",
            subtitle = "收藏生词时自动生成助记图",
            checked = aiImageAutoGen,
            accentOnHover = Stellar.Pink,
            onChecked = onAiImageAutoGen,
        )
        PreferenceDivider()
        DefaultNotebookPicker(
            notebooks = notebooks,
            selectedId = defaultNotebookId,
            onSelect = onDefaultNotebook,
        )
    }
}

@Composable
private fun StorageCacheCard(
    cacheLabel: String,
    busy: Boolean,
    onClear: () -> Unit,
) {
    Spacer(Modifier.height(14.sdp()))
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "存储与缓存",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.sdp()))
        PreferenceAction(
            icon = Icons.Filled.DeleteOutline,
            title = "清除媒体缓存",
            subtitle = if (busy) "正在清除…" else "短视频 / 播客本地缓存 · 当前 $cacheLabel",
            onClick = onClear,
        )
    }
}

@Composable
private fun AdDiagCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AdDiagStore.load(context) }
    val splash by AdDiagStore.splash.collectAsState()
    val draw by AdDiagStore.draw.collectAsState()
    val reward by AdDiagStore.reward.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "广告诊断",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.sdp()))
        AdDiagRow(title = "开屏", slotId = BuildConfig.CSJ_SPLASH_CODE_ID, status = splash)
        AdDiagRow(title = "短视频 Draw", slotId = BuildConfig.CSJ_DRAW_CODE_ID, status = draw)
        AdDiagRow(title = "激励视频", slotId = BuildConfig.CSJ_REWARD_CODE_ID, status = reward)
        Spacer(Modifier.height(10.sdp()))
        PreferenceAction(
            icon = Icons.Filled.Refresh,
            title = "重试 Draw 广告",
            subtitle = "短视频翻页广告无填充时点此重试",
            onClick = {
                val act = context.findActivity()
                if (act == null) {
                    Toast.makeText(context, "页面不可用", Toast.LENGTH_SHORT).show()
                } else {
                    DrawFeedController.start(act)
                    Toast.makeText(context, "已发起 Draw 请求", Toast.LENGTH_SHORT).show()
                }
            },
        )
        PreferenceAction(
            icon = Icons.Filled.Refresh,
            title = "重试激励广告",
            subtitle = "补签/下载提示无填充时点此重试",
            onClick = {
                val act = context.findActivity()
                if (act == null) {
                    Toast.makeText(context, "页面不可用", Toast.LENGTH_SHORT).show()
                } else {
                    RewardVideoController.preload(act)
                    Toast.makeText(context, "已发起激励请求", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun AdDiagRow(title: String, slotId: String, status: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = "位 $slotId · $status",
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 13.ssp(),
            )
        }
    }
}

@Composable
private fun AccountSecurityCard(
    biometricLogin: Boolean,
    onBiometricLogin: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    deletionPending: Boolean,
    onOpenAccountDeletion: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "账号与安全",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.sdp()))
        PreferenceAction(
            icon = Icons.Filled.Lock,
            title = "修改密码",
            subtitle = "用当前密码设置新密码",
            onClick = onChangePassword,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.Fingerprint,
            title = "指纹解锁",
            subtitle = "下次打开应用时验证指纹；密码/验证码登录后不会再要求",
            checked = biometricLogin,
            accentOnHover = Stellar.Cyan,
            onChecked = onBiometricLogin,
        )
        PreferenceDivider()
        PreferenceAction(
            icon = Icons.Filled.PersonOff,
            title = "注销账号",
            subtitle = if (deletionPending) "注销冷静期中，可随时撤销" else "阅读须知并验证后进入7天冷静期",
            onClick = onOpenAccountDeletion,
            destructive = true,
        )
    }
}

@Composable
private fun PreferenceAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(22.sdp()),
        )
        Spacer(Modifier.width(12.sdp()))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (destructive) Stellar.Pink else Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = subtitle,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 13.ssp(),
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(18.sdp()),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageProviderPicker(
    selected: ImageGenProvider,
    onSelect: (ImageGenProvider) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(22.sdp()),
            )
            Spacer(Modifier.width(12.sdp()))
            Text(
                text = "文生图接口",
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.sdp()))
        FlowRow(
            modifier = Modifier.padding(start = 34.sdp()),
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalArrangement = Arrangement.spacedBy(8.sdp()),
        ) {
            ImageGenProvider.entries.forEach { provider ->
                val chosen = provider == selected
                Text(
                    text = provider.label,
                    color = if (chosen) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.sdp()))
                        .background(if (chosen) Stellar.CyanSoft else Stellar.SurfaceHigh)
                        .clickable { onSelect(provider) }
                        .padding(horizontal = 14.sdp(), vertical = 8.sdp()),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultNotebookPicker(
    notebooks: List<Notebook>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(22.sdp()),
            )
            Spacer(Modifier.width(12.sdp()))
            Text(
                text = "默认收藏生词本",
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.sdp()))
        Text(
            text = "首页查词点星星时，词条会保存到所选生词本",
            color = Stellar.OnSurfaceVariant,
            fontSize = 13.ssp(),
            modifier = Modifier.padding(start = 34.sdp()),
        )
        Spacer(Modifier.height(12.sdp()))
        FlowRow(
            modifier = Modifier.padding(start = 34.sdp()),
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalArrangement = Arrangement.spacedBy(8.sdp()),
        ) {
            notebooks.filter { !it.isSystem }.forEach { notebook ->
                val selected = notebook.id == selectedId
                Text(
                    text = notebook.name,
                    color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.sdp()))
                        .background(if (selected) Stellar.CyanSoft else Stellar.SurfaceHigh)
                        .clickable { onSelect(notebook.id) }
                        .padding(horizontal = 14.sdp(), vertical = 8.sdp()),
                )
            }
        }
    }
}

@Composable
private fun PreferenceDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.sdp())
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f)),
    )
}

@Composable
private fun PreferenceToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentOnHover: Color,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(22.sdp()),
        )
        Spacer(Modifier.width(12.sdp()))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = subtitle,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 13.ssp(),
            )
        }
        StellarToggle(checked = checked, glow = accentOnHover, onChecked = onChecked)
    }
}

@Composable
private fun StellarToggle(
    checked: Boolean,
    glow: Color,
    onChecked: (Boolean) -> Unit,
) {
    val trackColor = if (checked) Stellar.Cyan else Stellar.SurfaceHigh
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .width(48.sdp())
            .height(26.sdp())
            .then(
                if (checked) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = glow.copy(alpha = 0.40f),
                        spotColor = glow.copy(alpha = 0.40f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(trackColor)
            .clickable { onChecked(!checked) }
            .padding(2.sdp()),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.sdp())
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
