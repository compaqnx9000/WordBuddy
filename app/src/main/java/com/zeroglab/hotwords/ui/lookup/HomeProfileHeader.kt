package com.zeroglab.hotwords.ui.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp

@Composable
fun HomeProfileHeader(
    onOpenMenu: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 20.sdp()),
    ) {
        Icon(
            Icons.Outlined.Menu,
            contentDescription = "菜单",
            tint = Stellar.CyanSoft,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(26.sdp())
                .clickable(onClick = onOpenMenu),
        )
        Text(
            text = "Stellar Vocab",
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = "我的",
            tint = Stellar.CyanSoft,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(28.sdp())
                .clickable(onClick = onOpenAccount),
        )
    }
}
