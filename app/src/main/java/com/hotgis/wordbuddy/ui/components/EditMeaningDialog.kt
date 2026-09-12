package com.hotgis.wordbuddy.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.data.Definition
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.LocalStellar
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.StellarDefinitionRow
import com.hotgis.wordbuddy.ui.theme.HwColors

@Composable
fun EditMeaningDialog(
    word: String,
    definitions: List<Definition>,
    onDismiss: () -> Unit,
    onSave: (List<Definition>) -> Unit,
    stellar: Boolean = false,
) {
    if (stellar) {
        StellarEditMeaningDialog(
            definitions = definitions,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    } else {
        ClassicEditMeaningDialog(
            definitions = definitions,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

@Composable
private fun StellarEditMeaningDialog(
    definitions: List<Definition>,
    onDismiss: () -> Unit,
    onSave: (List<Definition>) -> Unit,
) {
    val originals = remember(definitions) { definitions.filter { !it.isUserAdded } }
    var userText by remember(definitions) { mutableStateOf(userDefinitionsToEditText(definitions)) }
    val shape = RoundedCornerShape(24.sdp())
    val fieldShape = RoundedCornerShape(14.sdp())
    val bodyScroll = rememberScrollState()
    val palette = LocalStellar.current
    val accent = Stellar.Cyan
    val accentSoft = Stellar.CyanSoft
    val saveFill = if (palette.isLight) Stellar.Cyan else Stellar.CyanSoft
    val scrollbarColor = accent.copy(alpha = 0.55f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .imePadding()
                .background(Stellar.Background.copy(alpha = 0.82f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val maxDialogHeight = maxHeight * 0.88f
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.sdp())
                    .heightIn(max = maxDialogHeight)
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.35f),
                        spotColor = accent.copy(alpha = 0.28f),
                    )
                    .clip(shape)
                    .background(Stellar.SurfaceContainer.copy(alpha = 0.97f))
                    .border(1.dp, accent.copy(alpha = 0.45f), shape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(22.sdp()),
            ) {
                Text(
                    text = "补充释义",
                    color = accentSoft,
                    fontSize = 26.ssp(),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.sdp()))
                Text(
                    text = "词典释义不可修改，可在下方补充自己的笔记。每行一条，可加词性，如 n. 我的理解",
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 14.ssp(),
                    lineHeight = 22.ssp(),
                )

                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(top = 16.sdp()),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(bodyScroll)
                            .padding(end = 10.sdp()),
                    ) {
                        if (originals.isNotEmpty()) {
                            Text(
                                text = "词典释义",
                                color = Stellar.OnSurfaceVariant,
                                fontSize = 13.ssp(),
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.sdp()))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(fieldShape)
                                    .background(Stellar.Surface)
                                    .border(1.dp, Stellar.Outline.copy(alpha = 0.55f), fieldShape)
                                    .padding(14.sdp()),
                            ) {
                                originals.forEachIndexed { index, def ->
                                    if (index > 0) Spacer(Modifier.height(12.sdp()))
                                    StellarDefinitionRow(def)
                                }
                            }
                            Spacer(Modifier.height(18.sdp()))
                        }

                        Text(
                            text = "我的补充",
                            color = Stellar.Pink,
                            fontSize = 13.ssp(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.sdp()))
                        BasicTextField(
                            value = userText,
                            onValueChange = { userText = it },
                            textStyle = TextStyle(
                                color = Stellar.OnSurface,
                                fontSize = 15.ssp(),
                                lineHeight = 22.ssp(),
                            ),
                            cursorBrush = SolidColor(accent),
                            decorationBox = { inner ->
                                Box {
                                    if (userText.isEmpty()) {
                                        Text(
                                            text = "输入你的补充笔记...",
                                            color = Stellar.OnSurfaceVariant.copy(alpha = 0.45f),
                                            fontSize = 15.ssp(),
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.sdp(), max = 220.sdp())
                                .clip(fieldShape)
                                .background(Stellar.Surface)
                                .border(1.dp, Stellar.Pink.copy(alpha = 0.35f), fieldShape)
                                .padding(14.sdp()),
                        )
                    }
                    // Scrollbar sits in its own right gutter, outside the field borders.
                    Box(
                        Modifier
                            .width(6.sdp())
                            .fillMaxHeight()
                            .verticalColumnScrollbar(bodyScroll, width = 3.dp, color = scrollbarColor),
                    )
                }

                Spacer(Modifier.height(16.sdp()))
                HorizontalDivider(color = Stellar.Outline.copy(alpha = 0.55f), thickness = 1.dp)
                Spacer(Modifier.height(14.sdp()))
                StellarEditActions(
                    saveFill = saveFill,
                    onDismiss = onDismiss,
                    onSave = { onSave(mergeUserDefinitions(definitions, userText)) },
                )
            }
        }
    }
}

@Composable
private fun StellarEditActions(
    saveFill: Color,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val accent = Stellar.Cyan
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(42.sdp())
                .widthIn(min = 88.sdp())
                .clip(RoundedCornerShape(24.sdp()))
                .border(1.5.dp, Stellar.Outline.copy(alpha = 0.9f), RoundedCornerShape(24.sdp()))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.sdp()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "取消",
                color = Stellar.OnSurface,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(10.sdp()))
        Row(
            Modifier
                .height(42.sdp())
                .widthIn(min = 88.sdp())
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(24.sdp()),
                    ambientColor = accent.copy(alpha = 0.65f),
                    spotColor = accent.copy(alpha = 0.65f),
                )
                .clip(RoundedCornerShape(24.sdp()))
                .background(saveFill)
                .clickable(onClick = onSave)
                .padding(horizontal = 22.sdp()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "保存",
                color = Stellar.OnPrimary,
                fontSize = 15.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun Modifier.verticalColumnScrollbar(
    state: ScrollState,
    width: Dp = 3.dp,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    val viewport = size.height
    val content = state.maxValue.toFloat() + viewport
    if (content <= viewport || state.maxValue <= 0 || size.width <= 0f || viewport <= 0f) {
        return@drawWithContent
    }
    val barWidth = width.toPx().coerceAtMost(size.width)
    val barHeight = ((viewport / content) * viewport).coerceAtLeast(barWidth * 4)
    val scrollRange = (viewport - barHeight).coerceAtLeast(0f)
    val barY = (state.value.toFloat() / state.maxValue) * scrollRange
    // Center the thumb in the gutter track.
    val barX = ((size.width - barWidth) / 2f).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(barX, barY),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth),
    )
}

@Composable
private fun ClassicEditMeaningDialog(
    definitions: List<Definition>,
    onDismiss: () -> Unit,
    onSave: (List<Definition>) -> Unit,
) {
    val originals = remember(definitions) { definitions.filter { !it.isUserAdded } }
    var userText by remember(definitions) { mutableStateOf(userDefinitionsToEditText(definitions)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补充释义") },
        text = {
            Column {
                Text(
                    text = "词典释义不可修改，可在下方补充自己的笔记。每行一条，可加词性，如 n. 我的理解",
                    color = HwColors.TextSecondary,
                    fontSize = 13.ssp(),
                )
                if (originals.isNotEmpty()) {
                    Spacer(Modifier.height(12.sdp()))
                    DefinitionListSectionTitle("词典释义")
                    DefinitionList(
                        definitions = originals,
                        fontSize = 14.ssp(),
                    )
                }
                Spacer(Modifier.height(12.sdp()))
                DefinitionListSectionTitle("我的补充")
                BasicTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    textStyle = TextStyle(
                        color = HwColors.TextPrimary,
                        fontSize = 15.ssp(),
                    ),
                    cursorBrush = SolidColor(HwColors.AccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.sdp(), max = 200.sdp())
                        .clip(RoundedCornerShape(8.sdp()))
                        .background(HwColors.UserNoteHighlight)
                        .padding(12.sdp()),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(mergeUserDefinitions(definitions, userText))
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun MeaningEditLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "编辑释义",
        color = HwColors.AccentBlue,
        fontSize = 13.ssp(),
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.sdp()),
    )
}

fun userDefinitionsToEditText(definitions: List<Definition>): String {
    return definitions
        .filter { it.isUserAdded }
        .joinToString("\n") { it.label.trim() }
        .trim()
}

fun mergeUserDefinitions(existing: List<Definition>, userText: String): List<Definition> {
    val originals = existing.filter { !it.isUserAdded }
    val userLines = parseUserDefinitions(userText).map { it.copy(isUserAdded = true) }
    return originals + userLines
}

fun parseUserDefinitions(text: String): List<Definition> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val posPrefix = Regex("""^([a-z]+\.)\s*(.+)$""", RegexOption.IGNORE_CASE)
    return lines.map { line ->
        val match = posPrefix.find(line)
        if (match != null) {
            Definition(match.groupValues[1].trim(), match.groupValues[2].trim(), isUserAdded = true)
        } else {
            Definition("", line, isUserAdded = true)
        }
    }
}
