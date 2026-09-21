package com.hotgis.wordbuddy.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hotgis.wordbuddy.ads.findActivity
import com.hotgis.wordbuddy.data.HotWordsApi
import com.hotgis.wordbuddy.data.InviteStore
import com.hotgis.wordbuddy.pay.AlipayPayHelper
import com.hotgis.wordbuddy.pay.isAlipayAuthIdentity
import com.hotgis.wordbuddy.ui.components.WordBuddyAvatarIcon
import com.hotgis.wordbuddy.ui.components.rememberImagePickerLauncher
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun AccountProfileScreen(
    userName: String,
    phone: String,
    userId: Long,
    nickname: String?,
    gender: String?,
    region: String?,
    buddyId: String?,
    signature: String?,
    email: String?,
    alipayAccount: String?,
    alipayName: String?,
    wechatAccount: String?,
    shippingSummary: String?,
    shippingName: String?,
    shippingPhone: String?,
    shippingDetail: String?,
    avatarBitmap: Bitmap?,
    avatarBusy: Boolean,
    onBack: () -> Unit,
    onUploadAvatar: (Uri, (Result<Unit>) -> Unit) -> Unit,
    onRequestAlipayAuthInfo: ((Result<String>) -> Unit) -> Unit,
    onCompleteAlipayBind: (String, (Result<Unit>) -> Unit) -> Unit,
    onUpdateAccountProfile: (
        nickname: String?,
        gender: String?,
        region: String?,
        signature: String?,
        email: String?,
        alipayAccount: String?,
        alipayName: String?,
        wechatAccount: String?,
        onResult: (Result<Unit>) -> Unit,
    ) -> Unit,
    onVerifyPassword: (password: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onSendChangePhoneCode: (newPhone: String, onResult: (Result<String?>) -> Unit) -> Unit,
    onChangePhone: (
        password: String,
        newPhone: String,
        code: String,
        onResult: (Result<Unit>) -> Unit,
    ) -> Unit,
    onUpdateShipping: (String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onFetchInviteInfo: ((Result<HotWordsApi.InviteInfo>) -> Unit) -> Unit = {},
    onBindInviteCode: (String, (Result<HotWordsApi.BindInviteResult>) -> Unit) -> Unit = { _, cb ->
        cb(Result.failure(IllegalStateException("未实现")))
    },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alipayBinding by remember { mutableStateOf(false) }
    var showUnbindAlipay by remember { mutableStateOf(false) }
    var showAvatarSource by remember { mutableStateOf(false) }
    var showNickname by remember { mutableStateOf(false) }
    var showGender by remember { mutableStateOf(false) }
    var showRegion by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }
    var showEmail by remember { mutableStateOf(false) }
    var showWechat by remember { mutableStateOf(false) }
    var showPhone by remember { mutableStateOf(false) }
    var showShipping by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showBindInvite by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var canBindInvite by remember { mutableStateOf(false) }
    var invitedByBuddyId by remember { mutableStateOf<String?>(null) }
    var inviteeReward by remember { mutableStateOf(10) }

    val resolvedBuddyId = buddyId?.trim().orEmpty()

    LaunchedEffect(Unit) {
        onFetchInviteInfo { result ->
            result.onSuccess { info ->
                canBindInvite = info.canBindInvite
                invitedByBuddyId = info.invitedByBuddyId
                inviteeReward = info.inviteeReward
            }
        }
    }

    BackHandler(onBack = onBack)

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

    fun saveProfile(
        nicknameValue: String? = null,
        genderValue: String? = null,
        regionValue: String? = null,
        signatureValue: String? = null,
        emailValue: String? = null,
        alipayValue: String? = null,
        alipayNameValue: String? = null,
        wechatValue: String? = null,
        successMessage: String,
        onSuccess: () -> Unit,
    ) {
        editBusy = true
        editError = null
        onUpdateAccountProfile(
            nicknameValue,
            genderValue,
            regionValue,
            signatureValue,
            emailValue,
            alipayValue,
            alipayNameValue,
            wechatValue,
        ) { result ->
            editBusy = false
            result
                .onSuccess {
                    onSuccess()
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                }
                .onFailure { editError = it.message ?: "保存失败" }
        }
    }

    fun bindAlipay() {
        if (alipayBinding) return
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "无法打开支付宝", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AlipayPayHelper.isInstalled(context)) {
            Toast.makeText(context, "请先安装支付宝，再绑定收款账号", Toast.LENGTH_SHORT).show()
            return
        }
        alipayBinding = true
        onRequestAlipayAuthInfo { infoResult ->
            infoResult.onFailure {
                alipayBinding = false
                Toast.makeText(context, it.message ?: "获取授权信息失败", Toast.LENGTH_SHORT).show()
            }
            infoResult.onSuccess { authInfo ->
                scope.launch {
                    val auth = runCatching { AlipayPayHelper.auth(activity, authInfo) }.getOrElse {
                        alipayBinding = false
                        Toast.makeText(context, it.message ?: "调起支付宝失败", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val code = auth.authCode
                    when {
                        auth.cancelled -> {
                            alipayBinding = false
                            Toast.makeText(context, "已取消绑定", Toast.LENGTH_SHORT).show()
                        }
                        code.isNullOrBlank() -> {
                            alipayBinding = false
                            Toast.makeText(
                                context,
                                auth.memo.ifBlank { "支付宝授权失败" },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        else -> onCompleteAlipayBind(code) { result ->
                            alipayBinding = false
                            result
                                .onSuccess {
                                    Toast.makeText(context, "支付宝已绑定", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "绑定失败", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
            }
        }
    }

    if (showUnbindAlipay) {
        AlertDialog(
            onDismissRequest = { if (!editBusy) showUnbindAlipay = false },
            containerColor = Stellar.SurfaceContainer,
            titleContentColor = Stellar.CyanSoft,
            textContentColor = Stellar.OnSurfaceVariant,
            title = {
                Text("解除支付宝绑定", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("解绑后不能用该账号提现。需要时可以重新打开支付宝绑定。")
                    if (!editError.isNullOrBlank()) {
                        Spacer(Modifier.height(8.sdp()))
                        Text(editError.orEmpty(), color = Stellar.Pink, fontSize = 13.ssp())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editBusy,
                    onClick = {
                        saveProfile(
                            alipayValue = "",
                            alipayNameValue = "",
                            successMessage = "已解除支付宝绑定",
                            onSuccess = { showUnbindAlipay = false },
                        )
                    },
                ) {
                    Text(if (editBusy) "解绑中…" else "解绑", color = Stellar.Pink)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !editBusy,
                    onClick = { showUnbindAlipay = false },
                ) {
                    Text("取消", color = Stellar.OnSurfaceVariant)
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
                launchAvatarGallery()
            },
        )
    }
    if (showNickname) {
        EditNicknameDialog(
            initial = nickname.orEmpty().ifBlank { userName },
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showNickname = false
                    editError = null
                }
            },
            onConfirm = { name ->
                saveProfile(
                    nicknameValue = name,
                    successMessage = "昵称已更新",
                    onSuccess = { showNickname = false },
                )
            },
        )
    }
    if (showGender) {
        EditGenderDialog(
            selected = gender,
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showGender = false
                    editError = null
                }
            },
            onConfirm = { value ->
                saveProfile(
                    genderValue = value,
                    successMessage = "性别已更新",
                    onSuccess = { showGender = false },
                )
            },
        )
    }
    if (showRegion) {
        EditRegionDialog(
            initial = region.orEmpty(),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showRegion = false
                    editError = null
                }
            },
            onConfirm = { value ->
                saveProfile(
                    regionValue = value,
                    successMessage = "地区已更新",
                    onSuccess = { showRegion = false },
                )
            },
        )
    }
    if (showSignature) {
        EditSignatureDialog(
            initial = signature.orEmpty(),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showSignature = false
                    editError = null
                }
            },
            onConfirm = { value ->
                saveProfile(
                    signatureValue = value,
                    successMessage = "签名已更新",
                    onSuccess = { showSignature = false },
                )
            },
        )
    }
    if (showEmail) {
        EditEmailDialog(
            initial = email.orEmpty(),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showEmail = false
                    editError = null
                }
            },
            onConfirm = { value ->
                saveProfile(
                    emailValue = value,
                    successMessage = "邮箱已更新",
                    onSuccess = { showEmail = false },
                )
            },
        )
    }
    if (showWechat) {
        EditWechatAccountDialog(
            initial = wechatAccount.orEmpty(),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showWechat = false
                    editError = null
                }
            },
            onConfirm = { value ->
                saveProfile(
                    wechatValue = value,
                    successMessage = "微信账号已保存",
                    onSuccess = { showWechat = false },
                )
            },
        )
    }
    if (showBindInvite) {
        EditInviteCodeDialog(
            busy = editBusy,
            error = editError,
            inviteeReward = inviteeReward,
            onDismiss = {
                if (!editBusy) {
                    showBindInvite = false
                    editError = null
                }
            },
            onConfirm = { code ->
                editBusy = true
                editError = null
                onBindInviteCode(code) { result ->
                    editBusy = false
                    result
                        .onSuccess {
                            canBindInvite = false
                            invitedByBuddyId = it.invitedByBuddyId
                            showBindInvite = false
                            Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { editError = it.message ?: "填写失败" }
                }
            },
        )
    }
    if (showPhone) {
        ChangePhoneDialog(
            currentPhoneMasked = maskAccountPhone(phone),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showPhone = false
                    editError = null
                }
            },
            onVerifyPassword = onVerifyPassword,
            onSendCode = onSendChangePhoneCode,
            onConfirm = { password, newPhone, code ->
                editBusy = true
                editError = null
                onChangePhone(password, newPhone, code) { result ->
                    editBusy = false
                    result
                        .onSuccess {
                            showPhone = false
                            Toast.makeText(context, "手机号已更新", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { editError = it.message ?: "修改失败" }
                }
            },
        )
    }
    if (showShipping) {
        EditShippingDialog(
            initialName = shippingName.orEmpty(),
            initialPhone = shippingPhone.orEmpty(),
            initialDetail = shippingDetail.orEmpty(),
            busy = editBusy,
            error = editError,
            onDismiss = {
                if (!editBusy) {
                    showShipping = false
                    editError = null
                }
            },
            onConfirm = { name, shipPhone, detail ->
                editBusy = true
                editError = null
                onUpdateShipping(name, shipPhone, detail) { result ->
                    editBusy = false
                    result
                        .onSuccess {
                            showShipping = false
                            Toast.makeText(context, "收货地址已保存", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { editError = it.message ?: "保存失败" }
                }
            },
        )
    }
    val qrBitmap = remember(resolvedBuddyId) { generateBuddyQrBitmap(resolvedBuddyId) }
    if (showQr) {
        BuddyQrDialog(
            buddyId = resolvedBuddyId.ifBlank { "未设置" },
            qrBitmap = qrBitmap,
            onDismiss = { showQr = false },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        AccountProfileTopBar(onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.sdp(), vertical = 12.sdp())
                .padding(bottom = 28.sdp()),
            verticalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            AccountProfileGroup {
                AccountProfileRow(
                    title = "头像",
                    onClick = { if (!avatarBusy) showAvatarSource = true },
                    trailingContent = {
                        Box(
                            Modifier
                                .size(46.sdp())
                                .clip(RoundedCornerShape(8.sdp()))
                                .background(Stellar.SurfaceHigh),
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
                                    modifier = Modifier.size(28.sdp()),
                                    detailTint = Stellar.Cyan,
                                )
                            }
                        }
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "名字",
                    value = nickname?.takeIf { it.isNotBlank() } ?: userName,
                    onClick = {
                        editError = null
                        showNickname = true
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "性别",
                    onClick = {
                        editError = null
                        showGender = true
                    },
                    trailingContent = {
                        GenderLabelText(
                            gender = gender,
                            placeholder = "去设置",
                            fontSize = 15.ssp(),
                        )
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "地区",
                    value = region?.takeIf { it.isNotBlank() } ?: "去设置",
                    onClick = {
                        editError = null
                        showRegion = true
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "手机号",
                    value = maskAccountPhone(phone),
                    onClick = {
                        editError = null
                        showPhone = true
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "邮箱",
                    value = email?.takeIf { it.isNotBlank() } ?: "去填写",
                    onClick = {
                        editError = null
                        showEmail = true
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "搭子号",
                    value = resolvedBuddyId.ifBlank { "分配中…" },
                    showChevron = false,
                    onClick = {
                        Toast.makeText(context, "搭子号由系统分配，不可修改", Toast.LENGTH_SHORT).show()
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "邀请好友",
                    value = if (resolvedBuddyId.isBlank()) "分配中…" else "分享链接赚积分",
                    onClick = {
                        if (resolvedBuddyId.isBlank()) {
                            Toast.makeText(context, "搭子号尚未分配，请稍后重试", Toast.LENGTH_SHORT).show()
                            return@AccountProfileRow
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, InviteStore.shareText(resolvedBuddyId))
                        }
                        context.startActivity(Intent.createChooser(send, "邀请好友"))
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "填写邀请码",
                    value = when {
                        !canBindInvite && !invitedByBuddyId.isNullOrBlank() -> "已绑定 $invitedByBuddyId"
                        !canBindInvite -> "已填写"
                        else -> "注册漏填可补一次"
                    },
                    showChevron = canBindInvite,
                    onClick = {
                        if (!canBindInvite) {
                            Toast.makeText(
                                context,
                                if (!invitedByBuddyId.isNullOrBlank()) {
                                    "已绑定邀请人 $invitedByBuddyId"
                                } else {
                                    "已填写过邀请码"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            editError = null
                            showBindInvite = true
                        }
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "我的二维码",
                    onClick = {
                        if (resolvedBuddyId.isBlank()) {
                            Toast.makeText(context, "搭子号尚未分配，请稍后重试", Toast.LENGTH_SHORT).show()
                        } else {
                            showQr = true
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.Outlined.QrCode2,
                            contentDescription = null,
                            tint = Stellar.OnSurfaceVariant,
                            modifier = Modifier.size(22.sdp()),
                        )
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "签名",
                    value = signature?.takeIf { it.isNotBlank() } ?: "去填写",
                    onClick = {
                        editError = null
                        showSignature = true
                    },
                )
            }

            AccountProfileGroup {
                AccountProfileRow(
                    title = "我的地址",
                    value = shortShippingLabel(shippingName, shippingSummary),
                    onClick = {
                        editError = null
                        showShipping = true
                    },
                )
            }

            AccountProfileGroup {
                val alipayBound = !alipayAccount.isNullOrBlank()
                AccountProfileRow(
                    title = "支付宝账号",
                    value = when {
                        alipayBinding -> "正在打开支付宝…"
                        !alipayBound -> "去绑定"
                        isAlipayAuthIdentity(alipayAccount) -> "已绑定"
                        !alipayName.isNullOrBlank() -> "${maskAlipay(alipayAccount.orEmpty())} · $alipayName"
                        else -> maskAlipay(alipayAccount.orEmpty())
                    },
                    onClick = { if (!alipayBinding && !editBusy) bindAlipay() },
                    trailingContent = if (alipayBound && !alipayBinding) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isAlipayAuthIdentity(alipayAccount)) {
                                        "已绑定"
                                    } else if (!alipayName.isNullOrBlank()) {
                                        "${maskAlipay(alipayAccount.orEmpty())} · $alipayName"
                                    } else {
                                        maskAlipay(alipayAccount.orEmpty())
                                    },
                                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
                                    fontSize = 15.ssp(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(10.sdp()))
                                Text(
                                    text = "解绑",
                                    color = Stellar.Pink,
                                    fontSize = 15.ssp(),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        if (!editBusy) {
                                            editError = null
                                            showUnbindAlipay = true
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                AccountProfileDivider()
                AccountProfileRow(
                    title = "微信账号",
                    value = wechatAccount?.takeIf { it.isNotBlank() } ?: "去设置",
                    onClick = {
                        editError = null
                        showWechat = true
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountProfileTopBar(onBack: () -> Unit) {
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
                .clip(RoundedCornerShape(999.dp))
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
            text = "个人资料",
            modifier = Modifier.align(Alignment.Center),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AccountProfileGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(vertical = 2.sdp()),
        content = { content() },
    )
}

private fun maskAlipay(value: String): String {
    val text = value.trim()
    if (text.contains('@') && text.length > 5) {
        val at = text.indexOf('@')
        val head = text.take(at.coerceAtMost(2))
        return "$head***${text.substring(at)}"
    }
    val digits = text.filter { it.isDigit() }
    if (digits.length >= 7) return digits.take(3) + "****" + digits.takeLast(4)
    return text
}

@Composable
private fun AccountProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.sdp()),
        thickness = 0.5.dp,
        color = Stellar.Outline.copy(alpha = 0.45f),
    )
}

@Composable
private fun AccountProfileRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Stellar.OnSurface,
            fontSize = 16.ssp(),
            modifier = Modifier.width(88.sdp()),
        )
        Box(
            Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (trailingContent != null) {
                trailingContent()
            } else if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
                    fontSize = 15.ssp(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(6.sdp()))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.sdp()),
            )
        }
    }
}

internal fun maskAccountPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length >= 7) {
        digits.take(3) + "****" + digits.takeLast(2)
    } else {
        phone.ifBlank { "未绑定" }
    }
}

/** List-row hint only — never dump the full address here. */
internal fun shortShippingLabel(shippingName: String?, shippingSummary: String?): String {
    val name = shippingName?.trim().orEmpty()
    return when {
        name.isNotEmpty() -> name
        !shippingSummary.isNullOrBlank() -> "已填写"
        else -> "去填写"
    }
}

internal fun genderSymbolColor(gender: String?): Color = when (gender?.trim()) {
    "男" -> Color(0xFF3B82F6)
    "女" -> Color(0xFFEC4899)
    "未知" -> Color(0xFF9CA3AF)
    else -> Color(0xFF9CA3AF)
}

@Composable
internal fun GenderLabelText(
    gender: String?,
    placeholder: String = "—",
    fontSize: androidx.compose.ui.unit.TextUnit = 15.ssp(),
) {
    val value = gender?.trim().orEmpty()
    if (value.isEmpty()) {
        Text(
            text = placeholder,
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
            fontSize = fontSize,
            maxLines = 1,
        )
        return
    }
    val symbol = when (value) {
        "男" -> "♂"
        "女" -> "♀"
        "未知" -> "○"
        else -> null
    }
    if (symbol == null) {
        Text(
            text = value,
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = symbol,
            color = genderSymbolColor(value),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.sdp()))
        Text(
            text = value,
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
            fontSize = fontSize,
            maxLines = 1,
        )
    }
}

internal fun generateBuddyQrBitmap(buddyId: String, size: Int = 640): Bitmap? {
    val content = InviteStore.inviteUrl(buddyId).ifBlank { return null }
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
        }
    }.getOrNull()
}
