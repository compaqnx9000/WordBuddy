package com.hotgis.wordbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.LocalStellar
import com.hotgis.wordbuddy.ui.lookup.Stellar

@Composable
fun StellarConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    val palette = LocalStellar.current
    val accent = Stellar.Cyan
    val accentSoft = Stellar.CyanSoft
    val confirmFill = when {
        destructive -> Stellar.Pink
        palette.isLight -> Stellar.Cyan
        else -> Stellar.CyanSoft
    }

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
                text = title,
                color = accentSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.sdp()))
            Text(
                text = message,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.92f),
                fontSize = 15.ssp(),
                lineHeight = 22.ssp(),
            )
            Spacer(Modifier.height(22.sdp()))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissText.isNotBlank()) {
                    Text(
                        text = dismissText,
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
                    )
                    Spacer(Modifier.padding(horizontal = 4.sdp()))
                }
                Text(
                    text = confirmText,
                    color = Stellar.OnPrimary,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.em,
                    modifier = Modifier
                        .shadow(
                            elevation = if (destructive) 8.dp else 10.dp,
                            shape = RoundedCornerShape(999.dp),
                            ambientColor = confirmFill.copy(alpha = 0.35f),
                            spotColor = confirmFill.copy(alpha = 0.35f),
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(confirmFill)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 18.sdp(), vertical = 10.sdp()),
                )
            }
        }
    }
}

@Composable
fun StellarInputDialog(
    title: String,
    placeholder: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    val accentSoft = Stellar.CyanSoft
    val palette = LocalStellar.current
    val saveFill = if (palette.isLight) Stellar.Cyan else Stellar.CyanSoft
    val fieldShape = RoundedCornerShape(14.sdp())

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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(horizontal = 22.sdp(), vertical = 20.sdp()),
        ) {
            Text(
                text = title,
                color = accentSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.sdp()))
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
                    onValueChange = { value = it },
                    textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 15.ssp()),
                    singleLine = true,
                    cursorBrush = SolidColor(accentSoft),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(placeholder, color = Stellar.OnSurfaceVariant, fontSize = 15.ssp())
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error != null) {
                Spacer(Modifier.height(8.sdp()))
                Text(error, color = Stellar.Pink, fontSize = 12.ssp())
            }
            Spacer(Modifier.height(20.sdp()))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dismissText,
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
                )
                Spacer(Modifier.padding(horizontal = 4.sdp()))
                Text(
                    text = confirmText,
                    color = Stellar.OnPrimary,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.em,
                    modifier = Modifier
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(999.dp),
                            ambientColor = saveFill.copy(alpha = 0.35f),
                            spotColor = saveFill.copy(alpha = 0.35f),
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(saveFill)
                        .clickable(onClick = { onConfirm(value.trim()) })
                        .padding(horizontal = 18.sdp(), vertical = 10.sdp()),
                )
            }
        }
    }
}
