package com.zeroglab.hotwords.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.hasStellarWallpaperBackground
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.theme.HwColors

enum class MainTab {
    Home,
    Notebook,
    Me,
}

@Composable
fun MainBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    stellar: Boolean = false,
) {
    if (stellar) {
        StellarBottomBar(
            selected = selected,
            onSelect = onSelect,
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(HwColors.Background)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = HwColors.Divider)
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.sdp()),
        ) {
            BottomTabItem(
                label = "首页",
                selected = selected == MainTab.Home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                onClick = { onSelect(MainTab.Home) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                label = "生词本",
                selected = selected == MainTab.Notebook,
                selectedIcon = Icons.Filled.MenuBook,
                unselectedIcon = Icons.Outlined.MenuBook,
                onClick = { onSelect(MainTab.Notebook) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                label = "我",
                selected = selected == MainTab.Me,
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                onClick = { onSelect(MainTab.Me) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StellarBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 16.sdp(), topEnd = 16.sdp()),
                ambientColor = Stellar.Cyan.copy(alpha = 0.25f),
                spotColor = Stellar.Cyan.copy(alpha = 0.25f),
            )
            .clip(RoundedCornerShape(topStart = 16.sdp(), topEnd = 16.sdp()))
            .background(
                if (hasStellarWallpaperBackground()) {
                    Stellar.SurfaceContainer.copy(alpha = 0.62f)
                } else {
                    Stellar.SurfaceContainer.copy(alpha = 0.92f)
                },
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.sdp(), bottom = 6.sdp()),
    ) {
        Row(Modifier.fillMaxWidth()) {
            StellarTab(
                label = "首页",
                selected = selected == MainTab.Home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                onClick = { onSelect(MainTab.Home) },
                modifier = Modifier.weight(1f),
            )
            StellarTab(
                label = "生词本",
                selected = selected == MainTab.Notebook,
                selectedIcon = Icons.Filled.MenuBook,
                unselectedIcon = Icons.Outlined.MenuBook,
                onClick = { onSelect(MainTab.Notebook) },
                modifier = Modifier.weight(1f),
            )
            StellarTab(
                label = "我",
                selected = selected == MainTab.Me,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                onClick = { onSelect(MainTab.Me) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StellarTab(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) Stellar.Cyan else Stellar.TabInactive
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.sdp()),
        )
        Text(
            text = label.uppercase(),
            color = color,
            fontSize = 9.ssp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
            modifier = Modifier.padding(top = 2.sdp()),
        )
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) HwColors.AccentBlue else HwColors.TextSecondary
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.sdp()),
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.ssp(),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.sdp()),
        )
    }
}
