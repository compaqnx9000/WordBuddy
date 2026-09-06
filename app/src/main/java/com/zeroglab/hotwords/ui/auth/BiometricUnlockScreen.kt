package com.zeroglab.hotwords.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground

@Composable
fun BiometricUnlockScreen(
    phoneHint: String?,
    error: String?,
    onUnlock: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground()
            .padding(horizontal = 28.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.sdp())
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = Stellar.Cyan.copy(alpha = 0.35f),
                    spotColor = Stellar.Cyan.copy(alpha = 0.3f),
                )
                .clip(CircleShape)
                .background(Stellar.SurfaceHigh)
                .border(1.dp, Stellar.Cyan.copy(alpha = 0.45f), CircleShape)
                .clickable(onClick = onUnlock),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Fingerprint,
                contentDescription = "指纹登录",
                tint = Stellar.Cyan,
                modifier = Modifier.size(44.sdp()),
            )
        }
        Spacer(Modifier.height(24.sdp()))
        Text(
            text = "指纹登录",
            color = Stellar.CyanSoft,
            fontSize = 24.ssp(),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.sdp()))
        Text(
            text = phoneHint?.let { "验证指纹后进入 @$it" } ?: "验证指纹后继续使用词搭子",
            color = Stellar.OnSurfaceVariant,
            fontSize = 14.ssp(),
            textAlign = TextAlign.Center,
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.sdp()))
            Text(
                text = error,
                color = Stellar.Pink,
                fontSize = 13.ssp(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.sdp()))
        Text(
            text = "点击验证",
            color = Stellar.OnPrimary,
            fontSize = 14.ssp(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Stellar.Cyan)
                .clickable(onClick = onUnlock)
                .padding(horizontal = 28.sdp(), vertical = 12.sdp()),
        )
        Spacer(Modifier.height(18.sdp()))
        Text(
            text = "退出登录",
            color = Stellar.OnSurfaceVariant,
            fontSize = 13.ssp(),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onLogout)
                .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
        )
    }
}
