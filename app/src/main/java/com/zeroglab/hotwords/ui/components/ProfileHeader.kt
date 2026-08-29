package com.zeroglab.hotwords.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.theme.HwColors
import com.zeroglab.hotwords.ui.theme.hwColors

@Composable
fun ProfileHeader(
    userName: String,
    wordCount: Int,
    onProfileClick: (() -> Unit)? = null,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .then(
                    if (onProfileClick != null) {
                        Modifier.clickable(onClick = onProfileClick)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                hwColors().accentBlue,
                                Color(0xFF5EB0FF),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                WordBuddyAvatarIcon(
                    modifier = Modifier.size(30.sdp()),
                    detailTint = hwColors().accentBlue,
                )
            }
            Spacer(Modifier.width(12.sdp()))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userName,
                        color = HwColors.TextPrimary,
                        fontSize = 17.ssp(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (onProfileClick != null) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = HwColors.TextTertiary,
                            modifier = Modifier.size(20.sdp()),
                        )
                    }
                }
                Spacer(Modifier.height(6.sdp()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp())) {
                    HeaderChip(text = "已收藏 $wordCount 词")
                    HeaderChip(text = "学习中")
                }
            }
        }
        IconButton(onClick = onToggleTheme, modifier = Modifier.size(40.sdp())) {
            Icon(
                Icons.Outlined.Checkroom,
                contentDescription = "切换风格",
                tint = HwColors.TextPrimary,
                modifier = Modifier.size(22.sdp()),
            )
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.sdp())) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = HwColors.TextPrimary,
                modifier = Modifier.size(22.sdp()),
            )
        }
    }
}

@Composable
private fun HeaderChip(text: String) {
    Text(
        text = text,
        color = HwColors.TextSecondary,
        fontSize = 11.ssp(),
        modifier = Modifier
            .clip(RoundedCornerShape(10.sdp()))
            .background(HwColors.SurfaceGray)
            .padding(horizontal = 8.sdp(), vertical = 4.sdp()),
    )
}
