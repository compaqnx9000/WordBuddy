package com.zeroglab.hotwords.ui.profile

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
import androidx.compose.runtime.getValue
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
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.LocalStellar
import com.zeroglab.hotwords.ui.lookup.Stellar

@Composable
fun ChangePasswordDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String, confirmPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
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
            Text(
                text = "修改密码",
                color = Stellar.CyanSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.sdp()))
            Text(
                text = "请输入当前密码，并设置新密码（至少 6 位）",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
            )
            Spacer(Modifier.height(16.sdp()))
            PasswordField(value = oldPassword, onValueChange = { oldPassword = it }, placeholder = "当前密码")
            Spacer(Modifier.height(10.sdp()))
            PasswordField(value = newPassword, onValueChange = { newPassword = it }, placeholder = "新密码")
            Spacer(Modifier.height(10.sdp()))
            PasswordField(value = confirmPassword, onValueChange = { confirmPassword = it }, placeholder = "再输入一次新密码")
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.sdp()))
                Text(text = error, color = Stellar.Pink, fontSize = 13.ssp())
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
                        .clickable(enabled = !busy) {
                            onConfirm(oldPassword, newPassword, confirmPassword)
                        }
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
                            text = "保存",
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
private fun PasswordField(
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
