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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.hotgis.wordbuddy.data.HomophoneLikersPage
import com.hotgis.wordbuddy.data.WordHomophone
import com.hotgis.wordbuddy.data.WordHomophoneLiker
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.LocalStellar
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.StellarDefinitionRow
import com.hotgis.wordbuddy.ui.theme.HwColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Common English POS tags shown in the supplement editor (default [DEFAULT_POS]). */
val SUPPLEMENT_POS_OPTIONS = listOf(
    "n.", "v.", "vt.", "vi.", "adj.", "adv.", "prep.", "conj.",
    "pron.", "num.", "art.", "aux.", "interj.", "abbr.", "phr.",
)

const val DEFAULT_POS = "n."

@Composable
fun EditMeaningDialog(
    word: String,
    definitions: List<Definition>,
    onDismiss: () -> Unit,
    onSave: (List<Definition>, homophoneDraft: String?) -> Unit,
    homophones: List<WordHomophone> = emptyList(),
    onToggleHomophoneLike: ((Long) -> Unit)? = null,
    onLoadHomophoneLikers: (suspend (id: Long, offset: Int) -> HomophoneLikersPage?)? = null,
    stellar: Boolean = false,
) {
    if (stellar) {
        StellarEditMeaningDialog(
            word = word,
            definitions = definitions,
            homophones = homophones,
            onToggleHomophoneLike = onToggleHomophoneLike,
            onLoadHomophoneLikers = onLoadHomophoneLikers,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    } else {
        ClassicEditMeaningDialog(
            definitions = definitions,
            onDismiss = onDismiss,
            onSave = { defs -> onSave(defs, null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StellarEditMeaningDialog(
    word: String,
    definitions: List<Definition>,
    homophones: List<WordHomophone>,
    onToggleHomophoneLike: ((Long) -> Unit)?,
    onLoadHomophoneLikers: (suspend (id: Long, offset: Int) -> HomophoneLikersPage?)?,
    onDismiss: () -> Unit,
    onSave: (List<Definition>, homophoneDraft: String?) -> Unit,
) {
    val originals = remember(definitions) { definitions.filter { !it.isUserAdded } }
    val draftNotes = remember(definitions) {
        mutableStateListOf<Definition>().also { list ->
            list.addAll(definitions.filter { it.isUserAdded }.map { it.copy(isUserAdded = true) })
        }
    }
    var selectedPos by remember { mutableStateOf(DEFAULT_POS) }
    var noteText by remember { mutableStateOf("") }
    var homophoneText by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(24.sdp())
    val fieldShape = RoundedCornerShape(14.sdp())
    val chipShape = RoundedCornerShape(16.sdp())
    val bodyScroll = rememberScrollState()
    val palette = LocalStellar.current
    val accent = Stellar.Cyan
    val accentSoft = Stellar.CyanSoft
    val saveFill = if (palette.isLight) Stellar.Cyan else Stellar.CyanSoft
    val scrollbarColor = accent.copy(alpha = 0.55f)

    fun commitComposeNote() {
        val meaning = noteText.trim()
        if (meaning.isEmpty()) return
        draftNotes.add(Definition(selectedPos, meaning, isUserAdded = true))
        noteText = ""
    }

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
                    text = "词典释义不可修改。补充笔记仅自己可见；谐音助记全网共享，按点赞展示前 3 条。",
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
                        Text(
                            text = "词性",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 12.ssp(),
                        )
                        Spacer(Modifier.height(8.sdp()))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
                            verticalArrangement = Arrangement.spacedBy(8.sdp()),
                        ) {
                            SUPPLEMENT_POS_OPTIONS.forEach { pos ->
                                val selected = pos == selectedPos
                                Text(
                                    text = pos,
                                    color = if (selected) Stellar.OnPrimary else Stellar.OnSurface,
                                    fontSize = 13.ssp(),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .background(if (selected) accent else Stellar.SurfaceHigh)
                                        .border(
                                            1.dp,
                                            if (selected) accent else Stellar.Outline.copy(alpha = 0.55f),
                                            chipShape,
                                        )
                                        .clickable { selectedPos = pos }
                                        .padding(horizontal = 12.sdp(), vertical = 7.sdp()),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.sdp()))
                        BasicTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            textStyle = TextStyle(
                                color = Stellar.OnSurface,
                                fontSize = 15.ssp(),
                                lineHeight = 22.ssp(),
                            ),
                            cursorBrush = SolidColor(accent),
                            decorationBox = { inner ->
                                Box {
                                    if (noteText.isEmpty()) {
                                        Text(
                                            text = "输入补充释义…",
                                            color = Stellar.OnSurfaceVariant.copy(alpha = 0.45f),
                                            fontSize = 15.ssp(),
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.sdp(), max = 140.sdp())
                                .clip(fieldShape)
                                .background(Stellar.Surface)
                                .border(1.dp, Stellar.Pink.copy(alpha = 0.35f), fieldShape)
                                .padding(14.sdp()),
                        )
                        Spacer(Modifier.height(10.sdp()))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = "添加本条",
                                color = accent,
                                fontSize = 13.ssp(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(chipShape)
                                    .clickable(onClick = ::commitComposeNote)
                                    .padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                            )
                        }
                        if (draftNotes.isNotEmpty()) {
                            Spacer(Modifier.height(12.sdp()))
                            draftNotes.forEachIndexed { index, def ->
                                if (index > 0) Spacer(Modifier.height(8.sdp()))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(fieldShape)
                                        .background(Stellar.Pink.copy(alpha = 0.12f))
                                        .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = def.label,
                                        color = Stellar.OnSurface,
                                        fontSize = 14.ssp(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除",
                                        tint = Stellar.OnSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.sdp())
                                            .clickable { draftNotes.removeAt(index) },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.sdp()))
                        Text(
                            text = "谐音助记",
                            color = Stellar.Gold,
                            fontSize = 13.ssp(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.sdp()))
                        Text(
                            text = "所有用户可见，点赞最多的前 3 条会显示在释义下方。",
                            color = Stellar.OnSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.ssp(),
                            lineHeight = 18.ssp(),
                        )
                        Spacer(Modifier.height(8.sdp()))
                        if (homophones.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(fieldShape)
                                    .background(Stellar.Surface)
                                    .border(1.dp, Stellar.Gold.copy(alpha = 0.35f), fieldShape)
                                    .padding(12.sdp()),
                                verticalArrangement = Arrangement.spacedBy(10.sdp()),
                            ) {
                                homophones.take(3).forEach { tip ->
                                    HomophoneTipRow(
                                        tip = tip,
                                        onToggleLike = onToggleHomophoneLike?.let { cb -> { cb(tip.id) } },
                                        onLoadLikers = onLoadHomophoneLikers?.let { load ->
                                            { offset -> load(tip.id, offset) }
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.sdp()))
                        }
                        BasicTextField(
                            value = homophoneText,
                            onValueChange = { if (it.length <= 120) homophoneText = it },
                            textStyle = TextStyle(
                                color = Stellar.OnSurface,
                                fontSize = 15.ssp(),
                                lineHeight = 22.ssp(),
                            ),
                            cursorBrush = SolidColor(Stellar.Gold),
                            decorationBox = { inner ->
                                Box {
                                    if (homophoneText.isEmpty()) {
                                        Text(
                                            text = "输入谐音帮助记忆，如「about ≈ 额抱他」…",
                                            color = Stellar.OnSurfaceVariant.copy(alpha = 0.45f),
                                            fontSize = 14.ssp(),
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.sdp(), max = 120.sdp())
                                .clip(fieldShape)
                                .background(Stellar.Surface)
                                .border(1.dp, Stellar.Gold.copy(alpha = 0.4f), fieldShape)
                                .padding(14.sdp()),
                        )
                        if (word.isNotBlank()) {
                            Spacer(Modifier.height(6.sdp()))
                            Text(
                                text = "针对单词：$word",
                                color = Stellar.OnSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 11.ssp(),
                            )
                        }
                    }
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
                    onSave = {
                        commitComposeNote()
                        val merged = mergeUserDefinitionList(definitions, draftNotes.toList())
                        val tip = homophoneText.trim().ifBlank { null }
                        onSave(merged, tip)
                    },
                )
            }
        }
    }
}

@Composable
fun HomophoneTipRow(
    tip: WordHomophone,
    onToggleLike: (() -> Unit)?,
    onLoadLikers: (suspend (offset: Int) -> HomophoneLikersPage?)? = null,
    modifier: Modifier = Modifier,
) {
    var showLikers by remember(tip.id) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tip.body,
                color = Stellar.OnSurface,
                fontSize = 14.ssp(),
                lineHeight = 20.ssp(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.sdp()))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.sdp()))
                    .clickable(enabled = onToggleLike != null, onClick = { onToggleLike?.invoke() })
                    .padding(horizontal = 8.sdp(), vertical = 4.sdp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.sdp()),
            ) {
                Icon(
                    Icons.Outlined.ThumbUp,
                    contentDescription = "点赞",
                    tint = if (tip.likedByMe) Stellar.Gold else Stellar.OnSurfaceVariant,
                    modifier = Modifier.size(14.sdp()),
                )
                Text(
                    text = formatCompactCount(tip.likeCount),
                    color = if (tip.likedByMe) Stellar.Gold else Stellar.OnSurfaceVariant,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (tip.isMine && tip.likeCount > 0) {
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = formatLikerSummary(tip),
                color = Stellar.Gold.copy(alpha = 0.9f),
                fontSize = 12.ssp(),
                lineHeight = 16.ssp(),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.sdp()))
                    .clickable(enabled = onLoadLikers != null) { showLikers = true }
                    .padding(vertical = 2.sdp()),
            )
        }
    }
    if (showLikers && onLoadLikers != null) {
        HomophoneLikersDialog(
            tipBody = tip.body,
            total = tip.likeCount,
            onLoadPage = onLoadLikers,
            onDismiss = { showLikers = false },
        )
    }
}

/** Card summary never enumerates everyone — at most one recent name + total. */
fun formatLikerSummary(tip: WordHomophone): String {
    val total = tip.likeCount.coerceAtLeast(0)
    val people = formatPeopleCount(total)
    val recent = tip.likers.firstOrNull()?.label
    return when {
        total <= 0 -> ""
        recent == null -> "收到 $people 赞 · 点查看"
        total == 1 -> "$recent 赞了你"
        else -> "$recent 等 $people 赞了你 · 点查看"
    }
}

fun formatCompactCount(n: Int): String = when {
    n < 10_000 -> n.toString()
    n < 100_000_000 -> {
        val wan = n / 10_000.0
        if (n % 10_000 == 0) "${n / 10_000}万"
        else String.format("%.1f万", wan).trimEnd('0').trimEnd('.')
    }
    else -> "${n / 100_000_000}亿+"
}

fun formatPeopleCount(n: Int): String = when {
    n < 10_000 -> "${n}人"
    else -> "${formatCompactCount(n)}人"
}

@Composable
private fun HomophoneLikersDialog(
    tipBody: String,
    total: Int,
    onLoadPage: suspend (offset: Int) -> HomophoneLikersPage?,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf<List<WordHomophoneLiker>>(emptyList()) }
    var nextOffset by remember { mutableStateOf<Int?>(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        val offset = nextOffset ?: return
        loading = true
        error = null
        scope.launch {
            val page = onLoadPage(offset)
            loading = false
            if (page == null) {
                error = "加载失败"
                return@launch
            }
            items = if (offset == 0) page.items else items + page.items
            nextOffset = page.nextOffset
        }
    }

    LaunchedEffect(tipBody, total) { loadMore() }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(20.sdp())
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .clip(shape)
                .background(Stellar.SurfaceContainer)
                .border(1.dp, Stellar.Gold.copy(alpha = 0.35f), shape)
                .padding(18.sdp()),
        ) {
            Text(
                text = "点赞名单",
                color = Stellar.Gold,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = "「$tipBody」· 共 ${formatPeopleCount(total)}",
                color = Stellar.OnSurfaceVariant,
                fontSize = 12.ssp(),
            )
            Spacer(Modifier.height(12.sdp()))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.sdp())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.sdp()),
            ) {
                items.forEach { liker ->
                    Text(
                        text = liker.label,
                        color = Stellar.OnSurface,
                        fontSize = 14.ssp(),
                    )
                }
                if (error != null) {
                    Text(error!!, color = Stellar.Pink, fontSize = 13.ssp())
                }
                if (loading) {
                    Text("加载中…", color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                } else if (nextOffset != null) {
                    Text(
                        text = "加载更多",
                        color = Stellar.Cyan,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { loadMore() }
                            .padding(vertical = 4.sdp()),
                    )
                }
            }
            Spacer(Modifier.height(14.sdp()))
            Text(
                text = "关闭",
                color = Stellar.OnSurface,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.sdp(), vertical = 6.sdp()),
            )
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

fun mergeUserDefinitionList(existing: List<Definition>, userNotes: List<Definition>): List<Definition> {
    val originals = existing.filter { !it.isUserAdded }
    return originals + userNotes.map { it.copy(isUserAdded = true) }
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
