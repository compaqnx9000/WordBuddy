package com.hotgis.wordbuddy.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground

@Composable
fun BindPhoneAfterWechatScreen(
    phone: String,
    code: String,
    password: String,
    passwordConfirm: String,
    buddyIdHint: String?,
    sending: Boolean,
    loggingIn: Boolean,
    countdownSec: Int,
    error: String?,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onBind: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = countdownSec <= 0 && !sending && phone.length == 11
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.sdp()),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
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
        }
        BrandMark()
        Spacer(Modifier.height(28.sdp()))
        Column(
            Modifier
                .fillMaxWidth()
                .stellarGlass(neon = true)
                .padding(horizontal = 18.sdp(), vertical = 20.sdp()),
        ) {
            Text(
                text = "绑定手机号",
                color = Stellar.OnSurface,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = buildString {
                    append("微信登录成功。")
                    if (!buddyIdHint.isNullOrBlank()) {
                        append("你的搭子号是 ")
                        append(buddyIdHint)
                        append("。")
                    }
                    append("请绑定手机号，以便找回账号与提现。微信头像已同步为搭子头像。")
                },
                color = Stellar.OnSurfaceVariant,
                fontSize = 12.ssp(),
            )
            Spacer(Modifier.height(16.sdp()))
            LoginField(
                value = phone,
                onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(11)) },
                placeholder = "手机号",
                leading = Icons.Outlined.PhoneIphone,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(12.sdp()))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.sdp()),
            ) {
                LoginField(
                    value = code,
                    onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
                    placeholder = "验证码",
                    leading = Icons.Outlined.Sms,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.sdp()))
                        .background(if (canSend) Stellar.Cyan else Stellar.SurfaceHigh)
                        .clickable(enabled = canSend, onClick = onSendCode)
                        .padding(horizontal = 12.sdp(), vertical = 14.sdp()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            sending -> "发送中"
                            countdownSec > 0 -> "${countdownSec}s"
                            else -> "获取验证码"
                        },
                        color = if (canSend) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.sdp()))
            LoginField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "设置密码（选填）",
                leading = Icons.Outlined.Lock,
                keyboardType = KeyboardType.Password,
                password = true,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(12.sdp()))
            LoginField(
                value = passwordConfirm,
                onValueChange = onPasswordConfirmChange,
                placeholder = "再输入一次密码（若已填）",
                leading = Icons.Outlined.Lock,
                keyboardType = KeyboardType.Password,
                password = true,
                imeAction = ImeAction.Done,
                onImeAction = onBind,
            )
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.sdp()))
                Text(error, color = Stellar.Pink, fontSize = 13.ssp())
            }
            Spacer(Modifier.height(18.sdp()))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp()))
                    .background(Stellar.Cyan)
                    .clickable(enabled = !loggingIn, onClick = onBind)
                    .padding(vertical = 14.sdp()),
                contentAlignment = Alignment.Center,
            ) {
                if (loggingIn) {
                    CircularProgressIndicator(
                        color = Stellar.OnPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.sdp()),
                    )
                } else {
                    Text(
                        text = "完成绑定并进入",
                        color = Stellar.OnPrimary,
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.sdp()))
        Text(
            text = "返回将退出当前微信登录，需重新授权。",
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.ssp(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.sdp()))
    }
}
