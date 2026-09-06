package com.zeroglab.hotwords.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import androidx.compose.ui.text.font.FontWeight
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
    onChangePassword: (
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (Result<Unit>) -> Unit,
    ) -> Unit = { _, _, _, _ -> },
    avatarBitmap: Bitmap? = null,
    avatarBusy: Boolean = false,
    onUploadAvatar: (Uri, (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showAvatarSource by remember { mutableStateOf(false) }
    var changePasswordBusy by remember { mutableStateOf(false) }
    var changePasswordError by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val loggedIn = !phone.isNullOrBlank()

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

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) handlePickedAvatar(uri)
    }

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
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
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
                        Toast.makeText(context, "账号：$phone", Toast.LENGTH_SHORT).show()
                    } else {
                        onLogin()
                    }
                },
                onOpenSettings = onOpenSettings,
            )

            if (loggedIn) {
                ProfileMenuCard {
                    ProfileMenuRow(
                        icon = Icons.Outlined.Lock,
                        iconTint = Stellar.Cyan,
                        title = "修改密码",
                        trailing = "当前密码 + 新密码",
                        onClick = {
                            changePasswordError = null
                            showChangePassword = true
                        },
                    )
                }
            }

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
    avatarBitmap: Bitmap?,
    avatarBusy: Boolean,
    onAvatarClick: () -> Unit,
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
private fun AvatarSourceDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.sdp())
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.35f),
                    spotColor = accent.copy(alpha = 0.28f),
                )
                .clip(shape)
                .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                .border(1.dp, accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 22.sdp(), vertical = 20.sdp()),
        ) {
            Text(
                text = "更换头像",
                color = Stellar.CyanSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.sdp()))
            Text(
                text = "自拍一张，或从相册选择图片，保存后会同步到服务器。",
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
                fontSize = 15.ssp(),
            )
            Spacer(Modifier.height(16.sdp()))
            AvatarSourceRow(
                icon = Icons.Outlined.PhotoCamera,
                title = "拍照",
                onClick = onCamera,
            )
            Spacer(Modifier.height(8.sdp()))
            AvatarSourceRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "从相册选择",
                onClick = onGallery,
            )
            Spacer(Modifier.height(12.sdp()))
            Text(
                text = "取消",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
            )
        }
    }
}

@Composable
private fun AvatarSourceRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .background(Stellar.SurfaceHigh.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Stellar.Cyan, modifier = Modifier.size(20.sdp()))
        Spacer(Modifier.width(12.sdp()))
        Text(title, color = Stellar.OnSurface, fontSize = 16.ssp(), fontWeight = FontWeight.Medium)
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
