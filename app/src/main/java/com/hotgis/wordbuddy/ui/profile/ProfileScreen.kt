package com.hotgis.wordbuddy.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.PersonAdd
import java.time.LocalDate
import java.time.YearMonth
import android.content.Intent
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.BuildConfig
import com.hotgis.wordbuddy.ads.RewardVideoController
import com.hotgis.wordbuddy.ads.findActivity
import com.hotgis.wordbuddy.data.CheckInResult
import com.hotgis.wordbuddy.data.CheckInState
import com.hotgis.wordbuddy.data.CheckInStore
import com.hotgis.wordbuddy.data.InviteStore
import com.hotgis.wordbuddy.data.NotebookImportResult
import com.hotgis.wordbuddy.ui.components.StellarConfirmDialog
import com.hotgis.wordbuddy.ui.components.WordBuddyAvatarIcon
import com.hotgis.wordbuddy.ui.components.rememberImagePickerLauncher
import com.hotgis.wordbuddy.ui.design.FoldableDualPaneRow
import com.hotgis.wordbuddy.ui.design.LocalFoldableLayout
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import com.hotgis.wordbuddy.ui.settings.AboutWordBuddyDialog
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
    level: Int = 1,
    networkRegion: String? = null,
    networkRegionDetail: String? = null,
    checkIn: CheckInState = CheckInState(),
    onRefreshCheckIn: () -> Unit = {},
    onCheckIn: (onResult: (CheckInResult) -> Unit) -> Unit = { it(CheckInResult.AlreadyCheckedIn) },
    onMakeupCheckIn: (date: String, onResult: (CheckInResult) -> Unit) -> Unit = { _, cb ->
        cb(CheckInResult.Failed("未实现"))
    },
    onOpenPointsMall: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    buddyId: String? = null,
    onLogin: () -> Unit = {},
    onOpenAccountProfile: () -> Unit = {},
    onRefreshNetworkRegion: ((Result<String>) -> Unit) -> Unit = { it(Result.failure(IllegalStateException("未实现"))) },
    avatarBitmap: Bitmap? = null,
    avatarBusy: Boolean = false,
    onUploadAvatar: (Uri, (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAvatarSource by remember { mutableStateOf(false) }
    var showNetworkRegion by remember { mutableStateOf(false) }
    var networkRefreshBusy by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var checkInSuccess by remember { mutableStateOf<CheckInResult.Success?>(null) }
    var checkInBusy by remember { mutableStateOf(false) }
    var makeupDate by remember { mutableStateOf<LocalDate?>(null) }
    var makeupConfirmDate by remember { mutableStateOf<LocalDate?>(null) }
    val loggedIn = !phone.isNullOrBlank()
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) {
        onRefreshCheckIn()
    }
    LaunchedEffect(loggedIn, activity) {
        if (loggedIn) {
            activity?.let(RewardVideoController::preload)
        }
    }

    fun applyCheckInResult(result: CheckInResult, makeup: Boolean = false) {
        when (result) {
            is CheckInResult.Success -> {
                checkInSuccess = result
            }
            CheckInResult.AlreadyCheckedIn -> {
                Toast.makeText(
                    context,
                    if (makeup) "该日已经签到过了" else "今天已经签到过了",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            CheckInResult.NeedLogin -> onLogin()
            is CheckInResult.Failed -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startMakeupWithReward(date: LocalDate) {
        val act = activity
        if (act == null) {
            Toast.makeText(context, "无法播放广告，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkInBusy) return
        checkInBusy = true
        makeupDate = date
        var rewarded = false
        fun finishMakeupApi() {
            onMakeupCheckIn(date.toString()) { result ->
                checkInBusy = false
                makeupDate = null
                applyCheckInResult(result, makeup = true)
                act.runOnUiThread { RewardVideoController.preload(act) }
            }
        }
        RewardVideoController.preload(act)
        act.runOnUiThread {
            if (!RewardVideoController.hasReadyAd()) {
                // Wait briefly for preload then show or fail.
                scope.launch {
                    var waits = 0
                    while (!RewardVideoController.hasReadyAd() && waits < 25) {
                        kotlinx.coroutines.delay(200)
                        waits++
                    }
                    if (!RewardVideoController.hasReadyAd()) {
                        checkInBusy = false
                        makeupDate = null
                        Toast.makeText(context, "广告暂未填充，请稍后再试", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    act.runOnUiThread {
                        RewardVideoController.show(
                            act,
                            object : RewardVideoController.Callbacks {
                                override fun onShown() = Unit
                                override fun onRewarded() {
                                    rewarded = true
                                    finishMakeupApi()
                                }
                                override fun onClosed() {
                                    if (!rewarded) {
                                        checkInBusy = false
                                        makeupDate = null
                                        Toast.makeText(context, "需看完广告才能补签", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onFailed(reason: String) {
                                    checkInBusy = false
                                    makeupDate = null
                                    Toast.makeText(context, "广告播放失败：$reason", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
                return@runOnUiThread
            }
            RewardVideoController.show(
                act,
                object : RewardVideoController.Callbacks {
                    override fun onShown() = Unit
                    override fun onRewarded() {
                        rewarded = true
                        finishMakeupApi()
                    }
                    override fun onClosed() {
                        if (!rewarded) {
                            checkInBusy = false
                            makeupDate = null
                            Toast.makeText(context, "需看完广告才能补签", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailed(reason: String) {
                        checkInBusy = false
                        makeupDate = null
                        Toast.makeText(context, "广告播放失败：$reason", Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
    }

    fun handlePickedAvatar(uri: Uri) {
        onUploadAvatar(uri) { result ->
            result
                .onSuccess { Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "头像上传失败", Toast.LENGTH_LONG).show() }
        }
    }

    fun createCameraUri(): Uri {
        val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) handlePickedAvatar(uri)
    }

    val launchAvatarGallery = rememberImagePickerLauncher(
        onImagePicked = { uri -> handlePickedAvatar(uri) },
    )

    fun launchCamera() {
        runCatching {
            val uri = createCameraUri()
            cameraUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure {
            Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "需要相机权限才能自拍", Toast.LENGTH_SHORT).show()
    }

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
            message = "使用中遇到问题，可通过应用商店评论反馈，或联系 hi@wordbuddy.cc。\n\n我们会持续改进查词、收藏与背诵体验。",
            confirmText = "知道了",
            dismissText = "",
            onDismiss = { showHelp = false },
            onConfirm = { showHelp = false },
        )
    }
    if (showAbout) {
        AboutWordBuddyDialog(onDismiss = { showAbout = false })
    }
    checkInSuccess?.let { success ->
        val makeupDay = success.makeupDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        StellarConfirmDialog(
            title = if (makeupDay != null) "补签成功" else "签到成功",
            message = if (makeupDay != null) {
                "补签 ${makeupDay.monthValue}月${makeupDay.dayOfMonth}日成功，该日连续第 ${success.streakAtDate.coerceAtLeast(1)} 天，获得 ${success.pointsEarned} 积分\n当前累计 ${success.totalPoints} 分"
            } else {
                "连续第 ${success.streakDays} 天，获得 ${success.pointsEarned} 积分\n当前累计 ${success.totalPoints} 分"
            },
            confirmText = "太棒了",
            dismissText = "",
            onDismiss = { checkInSuccess = null },
            onConfirm = { checkInSuccess = null },
        )
    }
    makeupConfirmDate?.let { date ->
        val makeupPoints = CheckInStore.makeupReward(checkIn, date)
        StellarConfirmDialog(
            title = "补签 ${date.monthValue}月${date.dayOfMonth}日",
            message = "观看完整激励视频后即可完成补签，并获得 $makeupPoints 积分。",
            confirmText = "观看广告",
            dismissText = "取消",
            onDismiss = { if (!checkInBusy) makeupConfirmDate = null },
            onConfirm = {
                makeupConfirmDate = null
                startMakeupWithReward(date)
            },
        )
    }
    if (showAvatarSource) {
        AvatarSourceDialog(
            onDismiss = { showAvatarSource = false },
            onCamera = {
                showAvatarSource = false
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGallery = {
                showAvatarSource = false
                launchAvatarGallery()
            },
        )
    }
    if (showNetworkRegion) {
        NetworkRegionDialog(
            regionLabel = networkRegion,
            regionDetail = networkRegionDetail,
            avatarBitmap = avatarBitmap,
            busy = networkRefreshBusy,
            onDismiss = { if (!networkRefreshBusy) showNetworkRegion = false },
            onRefresh = {
                networkRefreshBusy = true
                onRefreshNetworkRegion { result ->
                    networkRefreshBusy = false
                    result
                        .onSuccess {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "校准失败", Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        ProfileTopBar()
        val foldable = LocalFoldableLayout.current
        val useSplit = foldable.supportsDualPaneListCard
        val hero: @Composable () -> Unit = {
            ProfileHeroCard(
                userName = userName,
                phone = phone,
                level = level,
                wordCount = wordCount,
                totalPoints = checkIn.totalPoints,
                avatarBitmap = avatarBitmap,
                avatarBusy = avatarBusy,
                onAvatarClick = {
                    if (loggedIn) {
                        if (!avatarBusy) showAvatarSource = true
                    } else {
                        onLogin()
                    }
                },
                onAccountClick = {
                    if (loggedIn) {
                        onOpenAccountProfile()
                    } else {
                        onLogin()
                    }
                },
                onOpenSettings = onOpenSettings,
            )
        }
        val checkInBlock: @Composable (monthGrid: Boolean) -> Unit = { monthGrid ->
            DailyCheckInCard(
                state = checkIn,
                loggedIn = loggedIn,
                busy = checkInBusy,
                monthGrid = monthGrid,
                onCheckIn = {
                    if (!loggedIn) {
                        onLogin()
                        return@DailyCheckInCard
                    }
                    if (checkInBusy || checkIn.checkedInToday) return@DailyCheckInCard
                    checkInBusy = true
                    onCheckIn { result ->
                        checkInBusy = false
                        applyCheckInResult(result)
                    }
                },
                onMakeupDay = { date ->
                    if (!loggedIn) {
                        onLogin()
                        return@DailyCheckInCard
                    }
                    if (checkInBusy) return@DailyCheckInCard
                    makeupConfirmDate = date
                },
            )
        }
        val rightMenus: @Composable () -> Unit = {
            ProfileMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.CardGiftcard,
                    iconTint = Stellar.Gold,
                    title = "积分兑礼",
                    trailing = "可用 ${checkIn.totalPoints} 分",
                    onClick = onOpenPointsMall,
                )
                ProfileMenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.PersonAdd,
                    iconTint = Stellar.Cyan,
                    title = "邀请好友",
                    trailing = "各得积分",
                    onClick = {
                        val id = buddyId?.trim().orEmpty()
                        if (id.isBlank()) {
                            Toast.makeText(context, "请先登录并等待搭子号分配", Toast.LENGTH_SHORT).show()
                            if (!loggedIn) onLogin()
                            return@ProfileMenuRow
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, InviteStore.shareText(id))
                        }
                        context.startActivity(Intent.createChooser(send, "邀请好友"))
                    },
                )
            }
            ProfileMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.Build,
                    iconTint = Stellar.Cyan,
                    title = "工具",
                    onClick = onOpenTools,
                )
            }
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
                ProfileMenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.Info,
                    iconTint = Stellar.Cyan,
                    title = "关于词搭子",
                    trailing = "v${BuildConfig.VERSION_NAME}",
                    onClick = { showAbout = true },
                )
                ProfileMenuDivider()
                ProfileMenuRow(
                    icon = Icons.Outlined.Public,
                    iconTint = Stellar.Cyan,
                    title = "网络属地",
                    trailing = networkRegion?.takeIf { it.isNotBlank() } ?: "查看说明",
                    onClick = {
                        if (!loggedIn) {
                            onLogin()
                            return@ProfileMenuRow
                        }
                        showNetworkRegion = true
                    },
                )
            }
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}",
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
                fontSize = 12.ssp(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.sdp(), bottom = 8.sdp()),
            )
        }

        if (useSplit) {
            FoldableDualPaneRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                listWeight = 0.48f,
                detailWeight = 0.52f,
                listPane = {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.sdp())
                            .padding(bottom = 24.sdp()),
                        verticalArrangement = Arrangement.spacedBy(14.sdp()),
                    ) {
                        Spacer(Modifier.height(4.sdp()))
                        hero()
                        checkInBlock(true)
                    }
                },
                detailPane = {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.sdp())
                            .padding(bottom = 24.sdp()),
                        verticalArrangement = Arrangement.spacedBy(14.sdp()),
                    ) {
                        Spacer(Modifier.height(4.sdp()))
                        rightMenus()
                    }
                },
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.sdp())
                    .padding(bottom = 24.sdp()),
                verticalArrangement = Arrangement.spacedBy(14.sdp()),
            ) {
                hero()
                checkInBlock(false)
                rightMenus()
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
private fun DailyCheckInCard(
    state: CheckInState,
    loggedIn: Boolean,
    busy: Boolean = false,
    monthGrid: Boolean = false,
    onCheckIn: () -> Unit,
    onMakeupDay: (LocalDate) -> Unit = {},
) {
    val cardShape = RoundedCornerShape(18.sdp())
    val today = remember { CheckInStore.todayShanghai() }
    val daySlots = remember(
        state.checkedInToday,
        state.streakDays,
        state.todayReward,
        state.recentDates,
        state.lastCheckInDate,
        today,
    ) {
        buildCheckInDaySlots(today, state)
    }
    val listState = rememberLazyListState()
    val todayIndex = remember(daySlots, today) {
        daySlots.indexOfFirst { it.date == today }.coerceAtLeast(0)
    }
    LaunchedEffect(monthGrid, todayIndex, daySlots.size) {
        if (!monthGrid && daySlots.isNotEmpty()) {
            val target = (todayIndex - 2).coerceAtLeast(0)
            listState.scrollToItem(target)
        }
    }
    val buttonLabel = when {
        !loggedIn -> "登录签到"
        busy -> "请稍候…"
        state.checkedInToday -> "已签到"
        else -> "签到 +${state.todayReward}"
    }
    val buttonEnabled = when {
        !loggedIn -> true
        busy -> false
        state.checkedInToday -> false
        else -> true
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .stellarGlass()
            .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
        verticalArrangement = Arrangement.spacedBy(12.sdp()),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.sdp())
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(Stellar.Gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = Stellar.Gold,
                    modifier = Modifier.size(20.sdp()),
                )
            }
            Spacer(Modifier.width(12.sdp()))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "每日签到 · ${today.monthValue}月",
                    color = Stellar.OnSurface,
                    fontSize = 16.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        !loggedIn -> "登录后签到，积分将同步到云端"
                        monthGrid && state.streakDays > 0 ->
                            "已连签 ${state.streakDays} 天 · 本月日历可补签 · 累计 ${state.totalPoints} 分"
                        monthGrid ->
                            "本月日期一览，漏签可看广告补签 · 累计 ${state.totalPoints} 分"
                        state.streakDays > 0 ->
                            "已连签 ${state.streakDays} 天 · 左右滑动查看本月 · 累计 ${state.totalPoints} 分"
                        else ->
                            "左右滑动查看本月，漏签可看广告补签 · 累计 ${state.totalPoints} 分"
                    },
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.9f),
                    fontSize = 12.ssp(),
                )
            }
            Text(
                text = buttonLabel,
                color = if (!buttonEnabled && loggedIn) {
                    Stellar.OnSurfaceVariant
                } else {
                    Stellar.OnPrimary
                },
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (!buttonEnabled && loggedIn) {
                            Stellar.SurfaceHigh
                        } else {
                            Stellar.Gold
                        },
                    )
                    .clickable(
                        enabled = buttonEnabled,
                        onClick = onCheckIn,
                    )
                    .padding(horizontal = 14.sdp(), vertical = 8.sdp()),
            )
        }

        if (monthGrid) {
            CheckInMonthGrid(
                daySlots = daySlots,
                loggedIn = loggedIn,
                onCheckIn = onCheckIn,
                onMakeupDay = onMakeupDay,
            )
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.sdp()),
                contentPadding = PaddingValues(horizontal = 2.sdp()),
            ) {
                items(daySlots, key = { it.date.toString() }) { slot ->
                    CheckInDayCell(
                        slot = slot,
                        modifier = Modifier.width(48.sdp()),
                        onClick = {
                            when {
                                !loggedIn -> onCheckIn()
                                slot.claimed || slot.isFuture -> Unit
                                slot.isClaimTarget -> onCheckIn()
                                slot.canMakeup -> onMakeupDay(slot.date)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInMonthGrid(
    daySlots: List<CheckInDaySlot>,
    loggedIn: Boolean,
    onCheckIn: () -> Unit,
    onMakeupDay: (LocalDate) -> Unit,
) {
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    val first = daySlots.firstOrNull()?.date ?: return
    // Sunday-first: SUNDAY→0 … SATURDAY→6
    val leading = first.dayOfWeek.value % 7
    val cells: List<CheckInDaySlot?> = List(leading) { null } + daySlots.map { it }
    Column(verticalArrangement = Arrangement.spacedBy(6.sdp())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.sdp())) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 11.ssp(),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.sdp()),
            ) {
                week.forEach { slot ->
                    if (slot == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        CheckInDayCell(
                            slot = slot,
                            compact = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when {
                                    !loggedIn -> onCheckIn()
                                    slot.claimed || slot.isFuture -> Unit
                                    slot.isClaimTarget -> onCheckIn()
                                    slot.canMakeup -> onMakeupDay(slot.date)
                                }
                            },
                        )
                    }
                }
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class CheckInDaySlot(
    val date: LocalDate,
    val reward: Int,
    val isToday: Boolean,
    val claimed: Boolean,
    val isClaimTarget: Boolean,
    val canMakeup: Boolean,
    val isFuture: Boolean,
)

private fun buildCheckInDaySlots(today: LocalDate, state: CheckInState): List<CheckInDaySlot> {
    val claimedDates = CheckInStore.claimedDates(state)
    val month = YearMonth.from(today)
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val days = (0L..(last.toEpochDay() - first.toEpochDay())).map { first.plusDays(it) }
    val yesterday = today.minusDays(1)
    val streakThroughYesterday = CheckInStore.consecutiveEndingAt(claimedDates, yesterday)
    val todayClaimed = state.checkedInToday || today in claimedDates
    val afterSigningToday = if (todayClaimed) {
        CheckInStore.consecutiveEndingAt(claimedDates, today)
    } else if (streakThroughYesterday > 0) {
        streakThroughYesterday + 1
    } else {
        1
    }
    return days.map { date ->
        val isFuture = date.isAfter(today)
        val claimed = date in claimedDates
        val isToday = date == today
        val isClaimTarget = isToday && !claimed && !state.checkedInToday
        val canMakeup = !claimed && date.isBefore(today) && !date.isBefore(first)
        val reward = when {
            claimed -> 0
            isClaimTarget -> state.todayReward
            canMakeup -> CheckInStore.makeupReward(state, date)
            isFuture -> {
                val daysAhead = (date.toEpochDay() - today.toEpochDay()).toInt()
                CheckInStore.rewardForDay(afterSigningToday + daysAhead)
            }
            else -> 1
        }
        CheckInDaySlot(
            date = date,
            reward = reward.coerceAtLeast(1),
            isToday = isToday,
            claimed = claimed || (isToday && state.checkedInToday),
            isClaimTarget = isClaimTarget,
            canMakeup = canMakeup,
            isFuture = isFuture,
        )
    }
}

private fun formatCheckInDateLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return "今天"
    return "${date.dayOfMonth}日"
}

@Composable
private fun CheckInDayCell(
    slot: CheckInDaySlot,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit = {},
) {
    val today = remember { CheckInStore.todayShanghai() }
    val shape = RoundedCornerShape(if (compact) 10.sdp() else 12.sdp())
    val clickable = slot.isClaimTarget || slot.canMakeup
    Column(
        modifier
            .clip(shape)
            .background(
                when {
                    slot.isClaimTarget -> Stellar.Gold.copy(alpha = 0.14f)
                    slot.claimed -> Stellar.Cyan.copy(alpha = 0.10f)
                    slot.canMakeup -> Stellar.SurfaceHigh.copy(alpha = 0.9f)
                    else -> Stellar.SurfaceHigh.copy(alpha = 0.55f)
                },
            )
            .border(
                width = if (slot.isToday) 1.dp else 0.dp,
                color = if (slot.isToday) Stellar.Cyan.copy(alpha = 0.55f) else Color.Transparent,
                shape = shape,
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = if (compact) 6.sdp() else 8.sdp(), horizontal = 2.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (compact) {
                if (slot.isToday) "今" else "${slot.date.dayOfMonth}"
            } else {
                formatCheckInDateLabel(slot.date, today)
            },
            color = if (slot.isToday) Stellar.Cyan else Stellar.OnSurfaceVariant,
            fontSize = if (compact) 11.ssp() else 10.ssp(),
            fontWeight = if (slot.isToday) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 4.sdp() else 6.sdp()))
        Box(
            Modifier
                .size(if (compact) 24.sdp() else 28.sdp())
                .clip(CircleShape)
                .background(
                    when {
                        slot.claimed -> Stellar.Cyan
                        slot.isClaimTarget -> Stellar.Gold
                        slot.canMakeup -> Stellar.OnSurfaceVariant.copy(alpha = 0.28f)
                        else -> Stellar.SurfaceContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (slot.claimed) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Stellar.OnPrimary,
                    modifier = Modifier.size(if (compact) 14.sdp() else 16.sdp()),
                )
            } else {
                Text(
                    text = "${slot.reward}",
                    color = when {
                        slot.isClaimTarget -> Stellar.OnPrimary
                        slot.canMakeup -> Stellar.OnSurfaceVariant
                        else -> Stellar.OnSurfaceVariant.copy(alpha = 0.7f)
                    },
                    fontSize = if (compact) 11.ssp() else 12.ssp(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = when {
                    slot.claimed -> "已领取"
                    slot.isClaimTarget -> "+${slot.reward}分"
                    slot.canMakeup -> "补签"
                    slot.isFuture -> "待签到"
                    else -> "未签"
                },
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 9.ssp(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProfileHeroCard(
    userName: String,
    phone: String?,
    level: Int,
    wordCount: Int,
    totalPoints: Int,
    avatarBitmap: Bitmap?,
    avatarBusy: Boolean,
    onAvatarClick: () -> Unit,
    onAccountClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val loggedIn = !phone.isNullOrBlank()
    val titleName = if (loggedIn) userName else "未登录"
    val statsText = if (loggedIn) {
        "已收藏 $wordCount 词 · 积分 $totalPoints"
    } else {
        "登录后同步收藏与积分"
    }
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
                    .size(84.sdp())
                    .clickable(onClick = onAvatarClick),
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
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = "头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        WordBuddyAvatarIcon(
                            modifier = Modifier.size(48.sdp()),
                            detailTint = Stellar.Cyan,
                        )
                    }
                    if (avatarBusy) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.sdp()),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .size(26.sdp())
                        .clip(CircleShape)
                        .background(Stellar.SurfaceHigh)
                        .border(1.dp, Stellar.Outline.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "更换头像",
                        tint = Stellar.OnSurfaceVariant,
                        modifier = Modifier.size(14.sdp()),
                    )
                }
            }

            Row(
                Modifier
                    .offset(y = (-22).sdp())
                    .padding(horizontal = 8.sdp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            ) {
                Text(
                    text = titleName,
                    color = Stellar.OnSurface,
                    fontSize = 22.ssp(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (loggedIn) {
                    Text(
                        text = "Lv.$level",
                        color = Stellar.Cyan,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Stellar.Cyan.copy(alpha = 0.16f))
                            .border(1.dp, Stellar.Cyan.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.sdp(), vertical = 4.sdp()),
                    )
                }
            }
            Text(
                text = if (loggedIn) "@$phone" else "@未登录",
                color = Stellar.Cyan,
                fontSize = 14.ssp(),
                modifier = Modifier.offset(y = (-16).sdp()),
            )
            Text(
                text = statsText,
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
                    label = if (loggedIn) "个人资料" else "登录账号",
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
