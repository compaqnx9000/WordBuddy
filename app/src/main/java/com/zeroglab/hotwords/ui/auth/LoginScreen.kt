package com.zeroglab.hotwords.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zeroglab.hotwords.R
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.stellarGlass
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground

@Composable
fun LoginScreen(
    phone: String,
    code: String,
    password: String,
    passwordConfirm: String,
    needPassword: Boolean,
    sending: Boolean,
    loggingIn: Boolean,
    countdownSec: Int,
    error: String?,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onBack: (() -> Unit)? = null,
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
        if (onBack != null) {
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
        } else {
            Spacer(Modifier.height(36.sdp()))
        }
        BrandMark()
        Spacer(Modifier.height(28.sdp()))
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                color = Stellar.CyanSoft,
                fontSize = 14.ssp(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.sdp()),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .stellarGlass(neon = true)
                .padding(horizontal = 18.sdp(), vertical = 20.sdp()),
        ) {
            Text(
                text = if (needPassword) "新用户，请设置登录密码" else "手机号登录",
                color = Stellar.OnSurface,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = if (needPassword) "验证通过后设置密码，之后可用同一手机号登录" else "验证码登录，未注册将自动创建账号",
                color = Stellar.OnSurfaceVariant,
                fontSize = 12.ssp(),
            )
            Spacer(Modifier.height(18.sdp()))
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
                    imeAction = if (needPassword) ImeAction.Next else ImeAction.Done,
                    onImeAction = { if (!needPassword) onLogin() },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        sending -> "发送中"
                        countdownSec > 0 -> "${countdownSec}s"
                        else -> "获取验证码"
                    },
                    color = if (canSend) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.sdp()))
                        .background(if (canSend) Stellar.Cyan else Stellar.SurfaceHigh)
                        .clickable(enabled = canSend, onClick = onSendCode)
                        .padding(horizontal = 12.sdp(), vertical = 16.sdp()),
                )
            }
            if (needPassword) {
                Spacer(Modifier.height(12.sdp()))
                LoginField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = "新密码（至少 6 位）",
                    leading = Icons.Outlined.Lock,
                    keyboardType = KeyboardType.Password,
                    password = true,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(12.sdp()))
                LoginField(
                    value = passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    placeholder = "再输入一次密码",
                    leading = Icons.Outlined.Lock,
                    keyboardType = KeyboardType.Password,
                    password = true,
                    imeAction = ImeAction.Done,
                    onImeAction = onLogin,
                )
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.sdp()))
                Text(text = error, color = Stellar.Pink, fontSize = 13.ssp())
            }
            Spacer(Modifier.height(20.sdp()))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp()))
                    .background(Stellar.CyanSoft)
                    .clickable(enabled = !loggingIn, onClick = onLogin)
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
                        text = if (needPassword) "设置密码并进入" else "登录",
                        color = Stellar.OnPrimary,
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.em,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.sdp()))
        Text(
            text = "开发阶段验证码未接入短信，任意 6 位数字即可。",
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.ssp(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.sdp()))
    }
}

@Composable
private fun BrandMark() {
    val logoShape = RoundedCornerShape(28.sdp())
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(132.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Cyan.copy(alpha = 0.16f)),
            )
            Image(
                painter = painterResource(R.drawable.ic_wordbuddy_logo),
                contentDescription = "词搭子",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .shadow(
                        elevation = 18.sdp(),
                        shape = logoShape,
                        ambientColor = Stellar.Cyan.copy(alpha = 0.45f),
                        spotColor = Stellar.Cyan.copy(alpha = 0.35f),
                    )
                    .size(104.sdp())
                    .clip(logoShape),
            )
        }
        Spacer(Modifier.height(16.sdp()))
        Text(
            text = "词搭子",
            color = Stellar.CyanSoft,
            fontSize = 28.ssp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.em,
        )
        Spacer(Modifier.height(6.sdp()))
        Text(
            text = "和搭子一起记单词",
            color = Stellar.OnSurfaceVariant,
            fontSize = 14.ssp(),
        )
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
    keyboardType: KeyboardType,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.sdp())
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Stellar.SurfaceHigh)
            .border(1.dp, Stellar.Outline.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.sdp()),
    ) {
        Icon(
            leading,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant,
            modifier = Modifier.size(20.sdp()),
        )
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Stellar.OnSurface,
                    fontSize = 15.ssp(),
                ),
                cursorBrush = SolidColor(Stellar.CyanBright),
                visualTransformation = if (password && !visible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 15.ssp(),
                )
            }
        }
        if (password) {
            Icon(
                imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (visible) "隐藏密码" else "显示密码",
                tint = Stellar.OnSurfaceVariant,
                modifier = Modifier
                    .size(20.sdp())
                    .clickable { visible = !visible },
            )
        }
    }
}
