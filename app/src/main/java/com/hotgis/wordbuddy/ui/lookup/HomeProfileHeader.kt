package com.hotgis.wordbuddy.ui.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp

@Composable
fun HomeProfileHeader(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Shown centered when [onBack] is set (e.g. shorts → lookup). */
    title: String? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 12.sdp()),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.OnSurface,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.sdp())
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(10.sdp()),
            )
            Text(
                text = title?.trim()?.takeIf { it.isNotEmpty() } ?: "词搭子",
                color = Stellar.CyanSoft,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 48.sdp()),
            )
        } else {
            Text(
                text = "词搭子",
                color = Stellar.CyanSoft,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
