package com.hotgis.wordbuddy.ui.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.LocalStellar
import com.hotgis.wordbuddy.ui.lookup.Stellar
import kotlinx.coroutines.delay

/**
 * Step 1: re-enter login password.
 * Step 2: new phone + SMS code → submit change.
 */
@Composable
fun ChangePhoneDialog(
    currentPhoneMasked: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onVerifyPassword: (password: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onSendCode: (newPhone: String, onResult: (Result<String?>) -> Unit) -> Unit,
    onConfirm: (password: String, newPhone: String, code: String) -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var password by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var localBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var countdownSec by remember { mutableIntStateOf(0) }
    var sendingCode by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    val palette = LocalStellar.current
    val saveFill = if (palette.isLight) Stellar.Cyan else Stellar.CyanSoft
    val displayError = localError ?: error
    val blocking = busy || localBusy

    LaunchedEffect(countdownSec) {
        if (countdownSec <= 0) return@LaunchedEffect
        delay(1000)
        countdownSec -= 1
    }

    Dialog(
        onDismissRequest = { if (!blocking) onDismiss() },
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
                text = "修改手机号",
                color = Stellar.CyanSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.sdp()))
            Text(
                text = if (step == 1) {
                    "当前号码 $currentPhoneMasked\n请先输入登录密码以确认身份"
                } else {
                    "密码已验证。请输入新手机号并完成短信验证"
                },
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
            )
            Spacer(Modifier.height(16.sdp()))

            if (step == 1) {
                ChangePhonePasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    placeholder = "当前登录密码",
                )
            } else {
                ChangePhonePlainField(
                    value = newPhone,
                    onValueChange = {
                        newPhone = it.filter { ch -> ch.isDigit() }.take(11)
                        localError = null
                    },
                    placeholder = "新手机号",
                    keyboardType = KeyboardType.Phone,
                )
                Spacer(Modifier.height(10.sdp()))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.sdp()),
                ) {
                    Box(Modifier.weight(1f)) {
                        ChangePhonePlainField(
                            value = code,
                            onValueChange = {
                                code = it.filter { ch -> ch.isDigit() }.take(6)
                                localError = null
                            },
                            placeholder = "短信验证码",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    val canSend = countdownSec <= 0 && !sendingCode && !blocking && newPhone.length == 11
                    Text(
                        text = when {
                            countdownSec > 0 -> "${countdownSec}s"
                            sendingCode -> "发送中"
                            else -> "获取验证码"
                        },
                        color = if (canSend) Stellar.CyanSoft else Stellar.OnSurfaceVariant,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Stellar.SurfaceHigh)
                            .border(1.dp, Stellar.Outline.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .clickable(enabled = canSend) {
                                sendingCode = true
                                localError = null
                                onSendCode(newPhone) { result ->
                                    sendingCode = false
                                    result
                                        .onSuccess {
                                            countdownSec = 60
                                            localError = null
                                        }
                                        .onFailure {
                                            localError = it.message ?: "验证码发送失败"
                                        }
                                }
                            }
                            .padding(horizontal = 12.sdp(), vertical = 12.sdp()),
                    )
                }
            }

            if (!displayError.isNullOrBlank()) {
                Spacer(Modifier.height(10.sdp()))
                Text(text = displayError, color = Stellar.Pink, fontSize = 13.ssp())
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
                        .clickable(enabled = !blocking, onClick = onDismiss)
                        .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(saveFill)
                        .clickable(enabled = !blocking) {
                            if (step == 1) {
                                localBusy = true
                                localError = null
                                onVerifyPassword(password) { result ->
                                    localBusy = false
                                    result
                                        .onSuccess {
                                            step = 2
                                            localError = null
                                        }
                                        .onFailure {
                                            localError = it.message ?: "密码验证失败"
                                        }
                                }
                            } else {
                                onConfirm(password, newPhone, code)
                            }
                        }
                        .padding(horizontal = 18.sdp(), vertical = 10.sdp()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (blocking) {
                        CircularProgressIndicator(
                            color = Stellar.OnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.sdp()),
                        )
                    } else {
                        Text(
                            text = if (step == 1) "下一步" else "完成修改",
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
private fun ChangePhonePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    var visible by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(14.sdp())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(Stellar.SurfaceHigh)
            .border(1.dp, Stellar.Outline.copy(alpha = 0.55f), fieldShape)
            .padding(horizontal = 14.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 15.ssp()),
                cursorBrush = SolidColor(Stellar.CyanBright),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(placeholder, color = Stellar.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.ssp())
            }
        }
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

@Composable
private fun ChangePhonePlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
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
            singleLine = true,
            textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 15.ssp()),
            cursorBrush = SolidColor(Stellar.CyanBright),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, color = Stellar.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.ssp())
        }
    }
}
