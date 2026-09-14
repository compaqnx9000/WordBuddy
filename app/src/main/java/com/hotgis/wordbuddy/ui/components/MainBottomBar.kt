package com.hotgis.wordbuddy.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.theme.HwColors

enum class MainTab {
    Home,
    Notebook,
    Shorts,
    Me,
}

private data class MainTabSpec(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val mainTabs = listOf(
    MainTabSpec(MainTab.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    MainTabSpec(MainTab.Notebook, "生词本", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    MainTabSpec(MainTab.Shorts, "短视频", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    MainTabSpec(MainTab.Me, "我", Icons.Filled.Person, Icons.Outlined.Person),
)

private val stellarTabs = listOf(
    MainTabSpec(MainTab.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    MainTabSpec(MainTab.Notebook, "生词本", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    MainTabSpec(MainTab.Shorts, "短视频", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    MainTabSpec(MainTab.Me, "我", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
)


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
            mainTabs.forEach { spec ->
                BottomTabItem(
                    label = spec.label,
                    selected = selected == spec.tab,
                    selectedIcon = spec.selectedIcon,
                    unselectedIcon = spec.unselectedIcon,
                    onClick = { onSelect(spec.tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StellarBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    // Match NotebookBottomBar exactly: one rectangular panel over wallpaper (no rounded “floating” strip).
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .drawBehind {
                drawLine(
                    color = line,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.sdp(), bottom = 8.sdp()),
    ) {
        Row(Modifier.fillMaxWidth()) {
            stellarTabs.forEach { spec ->
                StellarTab(
                    label = spec.label,
                    selected = selected == spec.tab,
                    selectedIcon = spec.selectedIcon,
                    unselectedIcon = spec.unselectedIcon,
                    onClick = { onSelect(spec.tab) },
                    modifier = Modifier.weight(1f),
                )
            }
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
            text = label,
            color = color,
            fontSize = 12.ssp(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 3.sdp()),
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
