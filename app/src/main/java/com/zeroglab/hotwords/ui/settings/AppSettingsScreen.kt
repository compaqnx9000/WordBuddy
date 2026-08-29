package com.zeroglab.hotwords.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zeroglab.hotwords.data.AccentStyle
import com.zeroglab.hotwords.data.AppTheme
import com.zeroglab.hotwords.data.Notebook
import com.zeroglab.hotwords.data.StudySettings
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.HomeProfileHeader
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.StellarPalettes
import com.zeroglab.hotwords.ui.lookup.hasStellarWallpaperBackground
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackgroundColor
import com.zeroglab.hotwords.ui.lookup.stellarGlass

@Composable
fun AppSettingsScreen(
    settings: StudySettings,
    notebooks: List<Notebook>,
    onBack: () -> Unit,
    onChange: ((StudySettings) -> StudySettings) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "设置",
    onOpenAccount: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        if (!hasStellarWallpaperBackground()) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-40).sdp(), y = (-60).sdp())
                    .size(220.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Pink.copy(alpha = 0.12f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 50.sdp(), y = 40.sdp())
                    .size(260.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Cyan.copy(alpha = 0.10f)),
            )
        }

        Column(Modifier.fillMaxSize()) {
            HomeProfileHeader(
                onOpenMenu = onBack,
                onOpenAccount = onOpenAccount ?: onBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.sdp())
                    .padding(bottom = 28.sdp()),
                verticalArrangement = Arrangement.spacedBy(16.sdp()),
            ) {
                Column(Modifier.padding(top = 8.sdp(), bottom = 8.sdp())) {
                    Text(
                        text = title,
                        color = Stellar.CyanSoft,
                        fontSize = 32.ssp(),
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Stellar.CyanSoft.copy(alpha = 0.35f),
                                blurRadius = 18f,
                            ),
                        ),
                    )
                    Spacer(Modifier.height(8.sdp()))
                    Text(
                        text = "配置你的沉浸式学习体验。",
                        color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 16.ssp(),
                    )
                }

                AestheticsCard(
                    selected = settings.accentStyle,
                    onSelect = { style ->
                        onChange {
                            it.copy(
                                accentStyle = style,
                                appTheme = if (style == AccentStyle.Frost) AppTheme.Light else AppTheme.Dark,
                            )
                        }
                    },
                )

                PreferencesCard(
                    autoPronounce = settings.speakOnPageChange,
                    dailyReminder = settings.dailyReminder,
                    aiImageAutoGen = settings.aiImageAutoGen,
                    notebooks = notebooks,
                    defaultNotebookId = settings.defaultNotebookId,
                    onAutoPronounce = { enabled -> onChange { it.copy(speakOnPageChange = enabled) } },
                    onDailyReminder = { enabled -> onChange { it.copy(dailyReminder = enabled) } },
                    onAiImageAutoGen = { enabled -> onChange { it.copy(aiImageAutoGen = enabled) } },
                    onDefaultNotebook = { id -> onChange { it.copy(defaultNotebookId = id) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AestheticsCard(
    selected: AccentStyle,
    onSelect: (AccentStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                tint = Stellar.Pink,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "外观主题",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(14.sdp()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前主题：",
                color = Stellar.OnSurfaceVariant,
                fontSize = 15.ssp(),
            )
            Spacer(Modifier.width(8.sdp()))
            Text(
                text = selected.label,
                color = Color(selected.swatchArgb),
                fontSize = 15.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.sdp()))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
        ) {
            AccentStyle.entries.forEach { style ->
                ThemeSwatch(
                    color = Color(style.swatchArgb),
                    thumbnailRes = StellarPalettes.forStyle(style).backgroundImageRes,
                    selected = style == selected,
                    onClick = { onSelect(style) },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailRes: Int? = null,
) {
    val shape = CircleShape
    Box(
        Modifier
            .size(if (selected) 42.sdp() else 38.sdp())
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = color.copy(alpha = 0.55f),
                        spotColor = color.copy(alpha = 0.55f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (thumbnailRes != null) {
                    Modifier
                } else {
                    Modifier.background(color)
                },
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, stellarScreenBackgroundColor(), shape)
                        .border(3.dp, color, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailRes != null) {
            Image(
                painter = painterResource(thumbnailRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PreferencesCard(
    autoPronounce: Boolean,
    dailyReminder: Boolean,
    aiImageAutoGen: Boolean,
    notebooks: List<Notebook>,
    defaultNotebookId: Long,
    onAutoPronounce: (Boolean) -> Unit,
    onDailyReminder: (Boolean) -> Unit,
    onAiImageAutoGen: (Boolean) -> Unit,
    onDefaultNotebook: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.sdp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.sdp()),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(22.sdp()),
            )
            Text(
                text = "偏好设置",
                color = Stellar.OnSurface,
                fontSize = 20.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.sdp()))
        PreferenceToggle(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "自动朗读",
            subtitle = "翻到生词时自动播放发音",
            checked = autoPronounce,
            accentOnHover = Stellar.Cyan,
            onChecked = onAutoPronounce,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.Notifications,
            title = "每日提醒",
            subtitle = "提醒你坚持背单词",
            checked = dailyReminder,
            accentOnHover = Stellar.Cyan,
            onChecked = onDailyReminder,
        )
        PreferenceDivider()
        PreferenceToggle(
            icon = Icons.Filled.AutoAwesome,
            title = "AI 自动生成配图",
            subtitle = "收藏生词时自动生成助记图",
            checked = aiImageAutoGen,
            accentOnHover = Stellar.Pink,
            onChecked = onAiImageAutoGen,
        )
        PreferenceDivider()
        DefaultNotebookPicker(
            notebooks = notebooks,
            selectedId = defaultNotebookId,
            onSelect = onDefaultNotebook,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultNotebookPicker(
    notebooks: List<Notebook>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "默认收藏生词本",
            color = Stellar.OnSurface,
            fontSize = 16.ssp(),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.sdp()))
        Text(
            text = "首页查词点星星时，词条会保存到所选生词本",
            color = Stellar.OnSurfaceVariant,
            fontSize = 13.ssp(),
        )
        Spacer(Modifier.height(12.sdp()))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalArrangement = Arrangement.spacedBy(8.sdp()),
        ) {
            notebooks.forEach { notebook ->
                val selected = notebook.id == selectedId
                Text(
                    text = notebook.name,
                    color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.sdp()))
                        .background(if (selected) Stellar.CyanSoft else Stellar.SurfaceHigh)
                        .clickable { onSelect(notebook.id) }
                        .padding(horizontal = 14.sdp(), vertical = 8.sdp()),
                )
            }
        }
    }
}

@Composable
private fun PreferenceDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.sdp())
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f)),
    )
}

@Composable
private fun PreferenceToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentOnHover: Color,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(22.sdp()),
        )
        Spacer(Modifier.width(12.sdp()))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = subtitle,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 13.ssp(),
            )
        }
        StellarToggle(checked = checked, glow = accentOnHover, onChecked = onChecked)
    }
}

@Composable
private fun StellarToggle(
    checked: Boolean,
    glow: Color,
    onChecked: (Boolean) -> Unit,
) {
    val trackColor = if (checked) Stellar.Cyan else Stellar.SurfaceHigh
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .width(48.sdp())
            .height(26.sdp())
            .then(
                if (checked) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = glow.copy(alpha = 0.40f),
                        spotColor = glow.copy(alpha = 0.40f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(trackColor)
            .clickable { onChecked(!checked) }
            .padding(2.sdp()),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.sdp())
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
