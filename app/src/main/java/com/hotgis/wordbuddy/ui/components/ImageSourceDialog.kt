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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.data.Definition
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.theme.HwColors

@Composable
fun ImageSourceDialog(
    definitions: List<Definition>,
    busy: Boolean,
    error: String?,
    onPickGallery: () -> Unit,
    onGenerateAi: (meaningHint: String) -> Unit,
    onDismiss: () -> Unit,
    stellar: Boolean = false,
) {
    if (stellar) {
        StellarImageSourceDialog(
            definitions = definitions,
            busy = busy,
            error = error,
            onPickGallery = onPickGallery,
            onGenerateAi = onGenerateAi,
            onDismiss = onDismiss,
        )
    } else {
        ClassicImageSourceDialog(
            definitions = definitions,
            busy = busy,
            error = error,
            onPickGallery = onPickGallery,
            onGenerateAi = onGenerateAi,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun StellarImageSourceDialog(
    definitions: List<Definition>,
    busy: Boolean,
    error: String?,
    onPickGallery: () -> Unit,
    onGenerateAi: (meaningHint: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember(definitions) { mutableIntStateOf(0) }
    val needPickMeaning = definitions.size > 1
    val shape = RoundedCornerShape(24.sdp())

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (Stellar.isLight) 0.42f else 0.55f))
                .clickable(enabled = !busy, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.sdp())
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        ambientColor = Stellar.Cyan.copy(alpha = 0.25f),
                        spotColor = Stellar.Cyan.copy(alpha = 0.2f),
                    )
                    .clip(shape)
                    .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                    .border(1.dp, Stellar.Cyan.copy(alpha = 0.35f), shape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(22.sdp()),
            ) {
                Text(
                    text = "记忆图",
                    color = Stellar.CyanSoft,
                    fontSize = 26.ssp(),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.sdp()))
                Text(
                    text = "可以从相册选图，或用 AI 生成。AI 生图前请选择要表达的词义。",
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 14.ssp(),
                    lineHeight = 22.ssp(),
                )
                if (definitions.isNotEmpty()) {
                    Spacer(Modifier.height(20.sdp()))
                    Text(
                        text = if (needPickMeaning) "选择词义" else "词义",
                        color = Stellar.OnSurface,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.sdp()))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.sdp())
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.sdp()),
                    ) {
                        definitions.forEachIndexed { index, def ->
                            StellarMeaningOption(
                                label = def.label,
                                selected = selectedIndex == index,
                                enabled = !busy,
                                onClick = { selectedIndex = index },
                            )
                        }
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(16.sdp()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.sdp()),
                            color = Stellar.Cyan,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.sdp()))
                        Text("AI 生图中…", color = Stellar.OnSurface, fontSize = 14.ssp())
                    }
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(10.sdp()))
                    Text(error, color = Stellar.Pink, fontSize = 13.ssp())
                }
                Spacer(Modifier.height(20.sdp()))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(46.sdp())
                            .clip(RoundedCornerShape(24.sdp()))
                            .border(1.5.dp, Stellar.Cyan.copy(alpha = 0.55f), RoundedCornerShape(24.sdp()))
                            .background(Stellar.Cyan.copy(alpha = 0.08f))
                            .clickable(enabled = !busy, onClick = onPickGallery),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "相册选图",
                            color = Stellar.CyanSoft,
                            fontSize = 15.ssp(),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(46.sdp())
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.sdp()),
                                ambientColor = Stellar.Cyan.copy(alpha = 0.55f),
                                spotColor = Stellar.Cyan.copy(alpha = 0.55f),
                            )
                            .clip(RoundedCornerShape(24.sdp()))
                            .background(Stellar.CyanSoft)
                            .clickable(
                                enabled = !busy && definitions.isNotEmpty(),
                                onClick = {
                                    val meaning = definitions.getOrNull(selectedIndex)?.label
                                        ?: definitions.firstOrNull()?.label
                                        ?: ""
                                    onGenerateAi(meaning)
                                },
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Stellar.OnPrimary,
                            modifier = Modifier.size(16.sdp()),
                        )
                        Spacer(Modifier.width(6.sdp()))
                        Text(
                            text = "AI 生图",
                            color = Stellar.OnPrimary,
                            fontSize = 15.ssp(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StellarMeaningOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.sdp())
    val borderColor = if (selected) Stellar.Cyan else Stellar.Outline.copy(alpha = 0.55f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Stellar.Cyan.copy(alpha = 0.45f),
                        spotColor = Stellar.Cyan.copy(alpha = 0.45f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                if (selected) Stellar.Cyan.copy(alpha = 0.12f) else Stellar.SurfaceHigh.copy(alpha = 0.85f),
            )
            .border(1.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.sdp())
                .size(22.sdp())
                .border(
                    width = 2.dp,
                    color = if (selected) Stellar.Cyan else Stellar.OnSurfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.sdp())
                        .clip(CircleShape)
                        .background(Stellar.Cyan),
                )
            }
        }
        Spacer(Modifier.width(12.sdp()))
        Text(
            text = label,
            color = Stellar.OnSurface,
            fontSize = 15.ssp(),
            lineHeight = 22.ssp(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClassicImageSourceDialog(
    definitions: List<Definition>,
    busy: Boolean,
    error: String?,
    onPickGallery: () -> Unit,
    onGenerateAi: (meaningHint: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember(definitions) { mutableIntStateOf(0) }
    val needPickMeaning = definitions.size > 1
    val shape = RoundedCornerShape(16.sdp())

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .clip(shape)
                .background(HwColors.Background)
                .border(1.dp, HwColors.Divider, shape)
                .padding(20.sdp()),
        ) {
            Text("记忆图", color = HwColors.TextPrimary, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.sdp()))
            Text(
                "可以从相册选图，或用 AI 生成。AI 生图前请选择要表达的词义。",
                color = HwColors.TextSecondary,
                fontSize = 13.ssp(),
            )
            if (definitions.isNotEmpty()) {
                Spacer(Modifier.height(14.sdp()))
                Text(
                    text = if (needPickMeaning) "选择词义" else "词义",
                    color = HwColors.TextPrimary,
                    fontSize = 14.ssp(),
                )
                Spacer(Modifier.height(8.sdp()))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.sdp())
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.sdp()),
                ) {
                    definitions.forEachIndexed { index, def ->
                        ClassicMeaningOption(
                            label = def.label,
                            selected = selectedIndex == index,
                            enabled = !busy,
                            onClick = { selectedIndex = index },
                        )
                    }
                }
            }
            if (busy) {
                Spacer(Modifier.height(16.sdp()))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.sdp()),
                        color = HwColors.AccentBlue,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.sdp()))
                    Text("AI 生图中…", color = HwColors.TextPrimary, fontSize = 14.ssp())
                }
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.sdp()))
                Text(error, color = HwColors.TextSecondary, fontSize = 13.ssp())
            }
            Spacer(Modifier.height(16.sdp()))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "相册选图",
                    color = HwColors.AccentBlue,
                    fontSize = 15.ssp(),
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onPickGallery)
                        .padding(8.sdp()),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "取消",
                    color = HwColors.TextSecondary,
                    fontSize = 15.ssp(),
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onDismiss)
                        .padding(8.sdp()),
                )
                Text(
                    text = "AI 生图",
                    color = if (!busy && definitions.isNotEmpty()) HwColors.AccentBlue else HwColors.TextTertiary,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(
                            enabled = !busy && definitions.isNotEmpty(),
                            onClick = {
                                val meaning = definitions.getOrNull(selectedIndex)?.label
                                    ?: definitions.firstOrNull()?.label
                                    ?: ""
                                onGenerateAi(meaning)
                            },
                        )
                        .padding(8.sdp()),
                )
            }
        }
    }
}

@Composable
private fun ClassicMeaningOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.sdp()))
            .border(
                width = 1.dp,
                color = if (selected) HwColors.AccentBlue else HwColors.Divider,
                shape = RoundedCornerShape(8.sdp()),
            )
            .background(if (selected) HwColors.MaskBlue.copy(alpha = 0.35f) else HwColors.Background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.sdp(), vertical = 10.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.sdp())
                .border(
                    2.dp,
                    if (selected) HwColors.AccentBlue else HwColors.TextTertiary,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.sdp())
                        .clip(CircleShape)
                        .background(HwColors.AccentBlue),
                )
            }
        }
        Spacer(Modifier.width(10.sdp()))
        Text(
            text = label,
            color = HwColors.TextPrimary,
            fontSize = 13.ssp(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ImageActionRow(
    hasImage: Boolean,
    busy: Boolean,
    onOpenChooser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                busy -> "生图中…"
                hasImage -> "更换图片"
                else -> "配图 / AI 生图"
            },
            color = HwColors.AccentBlue,
            fontSize = 13.ssp(),
            modifier = Modifier
                .clickable(enabled = !busy, onClick = onOpenChooser)
                .padding(vertical = 4.sdp()),
        )
    }
}
