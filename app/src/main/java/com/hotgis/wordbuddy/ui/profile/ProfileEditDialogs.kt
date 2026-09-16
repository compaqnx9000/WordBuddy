package com.hotgis.wordbuddy.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.ui.components.WordBuddyAvatarIcon
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.LocalStellar
import com.hotgis.wordbuddy.ui.lookup.Stellar

@Composable
fun EditNicknameDialog(
    initial: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    ProfileFormDialog(
        title = "修改昵称",
        subtitle = "最多 24 个字，会显示在个人资料和「我的」页顶部。",
        busy = busy,
        error = error,
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(value.trim()) },
    ) {
        ProfileTextField(
            value = value,
            onValueChange = { if (it.length <= 24) value = it },
            placeholder = "输入昵称",
        )
    }
}

@Composable
fun EditShippingDialog(
    initialName: String,
    initialPhone: String,
    initialDetail: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, detail: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var detail by remember { mutableStateOf(initialDetail) }
    ProfileFormDialog(
        title = "收货地址",
        subtitle = "用于积分兑礼发货，请填写真实可联系的信息。",
        busy = busy,
        error = error,
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim(), phone.trim(), detail.trim()) },
    ) {
        ProfileLabeledField(label = "收件人") {
            ProfileTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                placeholder = "请填写收件人姓名",
            )
        }
        Spacer(Modifier.height(12.sdp()))
        ProfileLabeledField(label = "电话") {
            ProfileTextField(
                value = phone,
                onValueChange = { if (it.length <= 20) phone = it },
                placeholder = "请填写联系电话",
                keyboardType = KeyboardType.Phone,
            )
        }
        Spacer(Modifier.height(12.sdp()))
        ProfileLabeledField(label = "地址") {
            ProfileTextField(
                value = detail,
                onValueChange = { if (it.length <= 200) detail = it },
                placeholder = "省市区 + 详细地址",
                singleLine = false,
                minLines = 3,
            )
        }
    }
}

@Composable
fun NetworkRegionDialog(
    regionLabel: String?,
    regionDetail: String?,
    avatarBitmap: Bitmap?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    val label = regionLabel?.takeIf { it.isNotBlank() } ?: "暂未识别"
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.3f),
                    spotColor = accent.copy(alpha = 0.22f),
                )
                .clip(shape)
                .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                .border(1.dp, accent.copy(alpha = 0.4f), shape)
                .padding(horizontal = 20.sdp(), vertical = 18.sdp()),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.sdp())
                        .clickable(enabled = !busy, onClick = onDismiss),
                )
            }
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Stellar.SurfaceHigh)
                    .border(1.dp, Stellar.Outline.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.sdp(), vertical = 8.sdp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.sdp())
                        .clip(CircleShape)
                        .background(Stellar.Cyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.sdp())
                                .clip(CircleShape),
                        )
                    } else {
                        WordBuddyAvatarIcon(modifier = Modifier.size(18.sdp()), detailTint = Stellar.Cyan)
                    }
                }
                Spacer(Modifier.width(8.sdp()))
                Text(label, color = Stellar.OnSurface, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.sdp()))
            Text(
                text = "网络属地说明",
                color = Stellar.CyanSoft,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.sdp()))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.sdp())
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "为让交流更透明可信，个人页会展示账号当前的网络属地。" +
                        "该结果来自运营商对接入网络的判定，不能手工改写，也不能关闭展示。",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 14.ssp(),
                    lineHeight = 21.ssp(),
                )
                if (!regionDetail.isNullOrBlank() && regionDetail != label) {
                    Spacer(Modifier.height(8.sdp()))
                    Text(
                        text = "当前识别：$regionDetail",
                        color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 12.ssp(),
                    )
                }
                Spacer(Modifier.height(12.sdp()))
                Text(
                    text = "若觉得位置不准，可以依次尝试：",
                    color = Stellar.OnSurface,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.sdp()))
                NetworkTip("点下方按钮，按当前网络重新校准")
                NetworkTip("关闭随身 Wi‑Fi、流量卡或虚拟卡后再刷新")
                NetworkTip("在 Wi‑Fi 与移动数据之间切换一次")
                NetworkTip("重启手机，或开关飞行模式后再校准")
                Spacer(Modifier.height(8.sdp()))
                Text(
                    text = "仍有偏差时，可向运营商确认当前接入归属。",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                )
            }
            Spacer(Modifier.height(16.sdp()))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Stellar.Outline.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .clickable(enabled = !busy, onClick = onRefresh)
                    .padding(vertical = 12.sdp()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Stellar.Cyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.sdp()),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = Stellar.OnSurface,
                        modifier = Modifier.size(18.sdp()),
                    )
                    Spacer(Modifier.width(8.sdp()))
                    Text(
                        text = "重新校准属地",
                        color = Stellar.OnSurface,
                        fontSize = 15.ssp(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkTip(text: String) {
    Row(Modifier.padding(vertical = 3.sdp())) {
        Text("·  ", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
        Text(text, color = Stellar.OnSurfaceVariant, fontSize = 14.ssp(), lineHeight = 20.ssp())
    }
}

@Composable
private fun ProfileFormDialog(
    title: String,
    subtitle: String,
    busy: Boolean,
    error: String?,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    val palette = LocalStellar.current
    val saveFill = if (palette.isLight) Stellar.Cyan else Stellar.CyanSoft
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
            Text(title, color = Stellar.CyanSoft, fontSize = 22.ssp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.sdp()))
            Text(subtitle, color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
            Spacer(Modifier.height(16.sdp()))
            content()
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.sdp()))
                Text(error, color = Stellar.Pink, fontSize = 13.ssp())
            }
            Spacer(Modifier.height(20.sdp()))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = !busy, onClick = onDismiss)
                        .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(saveFill)
                        .clickable(enabled = !busy, onClick = onConfirm)
                        .padding(horizontal = 18.sdp(), vertical = 10.sdp()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = Stellar.OnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.sdp()),
                        )
                    } else {
                        Text(
                            text = confirmText,
                            color = Stellar.OnPrimary,
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.06.em,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLabeledField(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Stellar.OnSurfaceVariant,
            fontSize = 13.ssp(),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.sdp()))
        content()
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val fieldShape = RoundedCornerShape(14.sdp())
    Box(
        Modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(Stellar.SurfaceHigh)
            .border(1.dp, Stellar.Outline.copy(alpha = 0.55f), fieldShape)
            .padding(horizontal = 14.sdp(), vertical = 12.sdp()),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 15.ssp()),
            cursorBrush = SolidColor(Stellar.CyanBright),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Stellar.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.ssp())
                }
                inner()
            },
        )
    }
}

private val REGION_OPTIONS = listOf(
    "北京", "天津", "上海", "重庆",
    "河北", "山西", "辽宁", "吉林", "黑龙江",
    "江苏", "浙江", "安徽", "福建", "江西", "山东",
    "河南", "湖北", "湖南", "广东", "海南",
    "四川", "贵州", "云南", "陕西", "甘肃", "青海",
    "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆",
    "香港", "澳门", "海外",
)

@Composable
fun EditGenderDialog(
    selected: String?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    ProfileFormDialog(
        title = "选择性别",
        subtitle = "仅自己可见，可随时修改。",
        busy = busy,
        error = error,
        confirmText = "关闭",
        onDismiss = onDismiss,
        onConfirm = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.sdp())) {
            listOf(
                Triple("男", "♂", Color(0xFF3B82F6)),
                Triple("女", "♀", Color(0xFFEC4899)),
                Triple("未知", "○", Color(0xFF9CA3AF)),
            ).forEach { (value, symbol, symbolColor) ->
                val active = selected == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.sdp()))
                        .background(
                            if (active) Stellar.Cyan.copy(alpha = 0.18f)
                            else Stellar.SurfaceHigh.copy(alpha = 0.75f),
                        )
                        .clickable(enabled = !busy) { onConfirm(value) }
                        .padding(horizontal = 14.sdp(), vertical = 12.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = symbol,
                        color = symbolColor,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.sdp()))
                    Text(
                        text = value,
                        color = Stellar.OnSurface,
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Stellar.Cyan,
                            modifier = Modifier.size(18.sdp()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditRegionDialog(
    initial: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var custom by remember { mutableStateOf(initial) }
    ProfileFormDialog(
        title = "选择地区",
        subtitle = "点选常用地区，或自行填写（最多 40 字）。",
        busy = busy,
        error = error,
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(custom.trim()) },
    ) {
        ProfileTextField(
            value = custom,
            onValueChange = { if (it.length <= 40) custom = it },
            placeholder = "例如：北京",
        )
        Spacer(Modifier.height(12.sdp()))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.sdp())
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.sdp()),
        ) {
            REGION_OPTIONS.forEach { option ->
                val active = custom == option
                Text(
                    text = option,
                    color = if (active) Stellar.Cyan else Stellar.OnSurface,
                    fontSize = 15.ssp(),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.sdp()))
                        .background(
                            if (active) Stellar.Cyan.copy(alpha = 0.16f)
                            else Stellar.SurfaceHigh.copy(alpha = 0.55f),
                        )
                        .clickable(enabled = !busy) { custom = option }
                        .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                )
            }
        }
    }
}

@Composable
fun EditSignatureDialog(
    initial: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    ProfileFormDialog(
        title = "修改签名",
        subtitle = "最多 40 个字，展示在个人资料页。",
        busy = busy,
        error = error,
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(value.trim()) },
    ) {
        ProfileTextField(
            value = value,
            onValueChange = { if (it.length <= 40) value = it },
            placeholder = "介绍一下自己",
            singleLine = false,
            minLines = 3,
        )
    }
}

@Composable
fun EditEmailDialog(
    initial: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    ProfileFormDialog(
        title = "修改邮箱",
        subtitle = "用于账号联系与找回，仅自己和管理员可见。",
        busy = busy,
        error = error,
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(value.trim()) },
    ) {
        ProfileTextField(
            value = value,
            onValueChange = { if (it.length <= 80) value = it },
            placeholder = "name@example.com",
            keyboardType = KeyboardType.Email,
        )
    }
}

@Composable
fun BuddyQrDialog(
    buddyId: String,
    qrBitmap: Bitmap?,
    onDismiss: () -> Unit,
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
                .padding(horizontal = 36.sdp())
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.35f),
                    spotColor = accent.copy(alpha = 0.28f),
                )
                .clip(shape)
                .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                .border(1.dp, accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 22.sdp(), vertical = 22.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("我的二维码", color = Stellar.CyanSoft, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = "搭子号  $buddyId",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
            )
            Spacer(Modifier.height(16.sdp()))
            Box(
                Modifier
                    .size(220.sdp())
                    .clip(RoundedCornerShape(16.sdp()))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(12.sdp()),
                contentAlignment = Alignment.Center,
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "搭子号二维码",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("二维码生成失败", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                }
            }
            Spacer(Modifier.height(18.sdp()))
            Text(
                text = "关闭",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.sdp(), vertical = 10.sdp()),
            )
        }
    }
}

@Composable
fun AvatarSourceDialog(
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
