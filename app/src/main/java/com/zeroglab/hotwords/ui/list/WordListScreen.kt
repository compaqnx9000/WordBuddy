package com.zeroglab.hotwords.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zeroglab.hotwords.data.Definition
import com.zeroglab.hotwords.data.Notebook
import com.zeroglab.hotwords.data.SortMode
import com.zeroglab.hotwords.data.VocabEntry
import com.zeroglab.hotwords.ui.VocabUiState
import com.zeroglab.hotwords.ui.components.StellarConfirmDialog
import com.zeroglab.hotwords.ui.components.StellarInputDialog
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.LocalStellar
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.StellarPalette
import com.zeroglab.hotwords.ui.lookup.hasStellarWallpaperBackground
import com.zeroglab.hotwords.ui.lookup.stellarPanelBackgroundColor
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground
import com.zeroglab.hotwords.ui.lookup.stellarPosColor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Keep list meaning / mask height stable across short and long definitions. */
private const val MeaningMaxLines = 3

/** Long enough to always wrap past [MeaningMaxLines] when measuring the row height. */
private const val MeaningProbe =
    "n. 字，词，单词；（某人说的）话，言语（words）；简短的交谈，谈话；命令，指示，密码；" +
        "消息，信息；诺言，保证；歌词；台词；剧本；争论，口角；一句话，简短的谈话"

private val MeaningPadding = 12.dp
private val SpeakIconSize = 15.dp
private val SpeakIconGap = 5.dp

@Composable
private fun rememberMeaningStyle(): TextStyle {
    val fontSize = 15.ssp()
    val lineHeight = 25.ssp()
    return remember(fontSize, lineHeight) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }
}

@Composable
fun WordListScreen(
    entries: List<VocabEntry>,
    totalCount: Int,
    ui: VocabUiState,
    notebooks: List<Notebook>,
    activeNotebookName: String,
    onSelectNotebook: (Long) -> Unit,
    onCreateNotebookClick: () -> Boolean = { true },
    onCreateNotebook: (String) -> Unit,
    onDeleteNotebook: (Long) -> Unit,
    onMoveEntries: (List<Long>, Long) -> Unit,
    wordCountInNotebook: (Long) -> Int,
    onToggleHide: () -> Unit,
    onReveal: (Long) -> Unit,
    onSpeak: (VocabEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteEntries: (List<Long>) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRecite: (startEntryId: Long?) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    alphabetLetterIndex: Map<Char, Int> = emptyMap(),
    onSeekAlphabetLetter: (Char) -> Unit = {},
    pendingScrollEntryId: Long? = null,
    onPendingScrollConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var openSwipeId by remember { mutableStateOf<Long?>(null) }
    var lastClickedEntryId by remember { mutableStateOf<Long?>(null) }
    val catalogLocked = notebooks.firstOrNull { it.id == ui.activeNotebookId }?.isSystem == true
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    var pendingAlphabetLetter by remember { mutableStateOf<Char?>(null) }
    val listComplete = entries.size >= totalCount && totalCount > 0
    // Absolute letter offsets match only the notebook's natural order (manual / alpha).
    val seekLetterIndex = when (ui.sortMode) {
        SortMode.MANUAL, SortMode.ALPHA -> alphabetLetterIndex
        else -> emptyMap()
    }
    val entryTexts = remember(entries) { entries.map { it.text } }
    fun jumpToAlphabetLetter(letter: Char) {
        pendingAlphabetLetter = letter
        onSeekAlphabetLetter(letter)
        val target = seekLetterIndex[letter]?.takeIf { it < entries.size }
            ?: resolveAlphabetScrollIndex(
                letter = letter,
                texts = entryTexts,
                absoluteIndex = seekLetterIndex,
                listComplete = listComplete,
            )
        if (target != null) {
            listScope.launch { listState.scrollToItem(target) }
        }
    }
    LaunchedEffect(pendingAlphabetLetter, entries.size, seekLetterIndex, listComplete) {
        val letter = pendingAlphabetLetter ?: return@LaunchedEffect
        val target = seekLetterIndex[letter]?.takeIf { it < entries.size }
            ?: resolveAlphabetScrollIndex(
                letter = letter,
                texts = entryTexts,
                absoluteIndex = seekLetterIndex,
                listComplete = listComplete,
            )
            ?: return@LaunchedEffect
        listState.scrollToItem(target)
        pendingAlphabetLetter = null
    }
    LaunchedEffect(ui.activeNotebookId) {
        pendingAlphabetLetter = null
        lastClickedEntryId = null
        selectionMode = false
        selectedIds = emptySet()
        openSwipeId = null
        // Only jump to top when switching notebooks — not when returning from card.
        if (pendingScrollEntryId == null) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(pendingScrollEntryId, entries.size, totalCount) {
        val id = pendingScrollEntryId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            lastClickedEntryId = id
            onPendingScrollConsumed()
            return@LaunchedEffect
        }
        if (entries.size < totalCount) {
            onLoadMore()
        } else {
            onPendingScrollConsumed()
        }
    }
    LaunchedEffect(ui.activeNotebookId, entries.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (ui.listLoading || entries.isEmpty() || last < 0) return@collect
                if (last >= (entries.lastIndex - 8).coerceAtLeast(0)) onLoadMore()
            }
    }
    val dragDropState = rememberDragDropState(listState, onMove = onReorder)
    val reordering = dragDropState.draggingItemIndex != null
    val wordStyle = TextStyle(
        fontSize = 17.ssp(),
        fontWeight = FontWeight.Bold,
    )
    val ipaStyle = TextStyle(fontSize = 13.ssp())
    val textMeasurer = rememberTextMeasurer()
    val measureSample = remember(
        entries.size.coerceAtMost(80),
        entries.firstOrNull()?.id,
        entries.getOrNull(79)?.id,
    ) { entries.take(80) }
    val longestWordPx = remember(measureSample, wordStyle, textMeasurer) {
        measureSample.maxOfOrNull { entry ->
            textMeasurer.measure(
                text = AnnotatedString(entry.text),
                style = wordStyle,
                maxLines = 1,
            ).size.width
        } ?: 0
    }
    // The ipa line carries the speaker icon, so it can be wider than the word itself.
    val longestIpaPx = remember(measureSample, ipaStyle, textMeasurer) {
        measureSample.maxOfOrNull { entry ->
            val ipa = buildSlashIpa(entry) ?: return@maxOfOrNull 0
            textMeasurer.measure(
                text = AnnotatedString(ipa),
                style = ipaStyle,
                maxLines = 1,
            ).size.width
        } ?: 0
    }
    val meaningStyle = rememberMeaningStyle()
    val density = LocalDensity.current
    val wordColumnWidth = with(density) {
        maxOf(longestWordPx.toDp(), longestIpaPx.toDp() + SpeakIconSize + SpeakIconGap)
    } + MeaningPadding * 2 + 2.dp

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        ListTopBar(
            title = activeNotebookName,
            selectionMode = selectionMode,
            selectedCount = selectedIds.size,
            onBack = {
                if (selectionMode) {
                    selectionMode = false
                    selectedIds = emptySet()
                } else {
                    onBack()
                }
            },
            onOpenMore = { showMoreMenu = true },
            onCancelSelection = {
                selectionMode = false
                selectedIds = emptySet()
            },
        )
        if (!ui.listError.isNullOrBlank()) {
            Text(
                text = ui.listError,
                color = Stellar.Pink,
                fontSize = 13.ssp(),
                modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()),
            )
        }
        NotebookSwitcher(
            notebooks = notebooks,
            activeNotebookId = ui.activeNotebookId,
            onSelect = {
                if (!selectionMode) onSelectNotebook(it)
            },
            onCreate = {
                if (onCreateNotebookClick()) showCreateDialog = true
            },
            onDeleteRequest = { notebook ->
                if (!notebook.isSystem) {
                    notebookToDelete = notebook
                }
            },
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // A single very long word or ipa must not squeeze the meaning column.
            val cappedWordColumn = minOf(wordColumnWidth, maxWidth * 0.38f)
            // Measure a real 3-line paragraph: arithmetic on lineHeight lost the third line to rounding.
            val meaningWidthPx = with(density) {
                val chrome = 3.sdp() + cappedWordColumn + MeaningPadding * 2
                (maxWidth - chrome).coerceAtLeast(80.dp).roundToPx()
            }
            val meaningBlockHeight = remember(meaningWidthPx, meaningStyle, textMeasurer, density) {
                val measured = textMeasurer.measure(
                    text = AnnotatedString(MeaningProbe),
                    style = meaningStyle,
                    maxLines = MeaningMaxLines,
                    constraints = Constraints(maxWidth = meaningWidthPx),
                )
                with(density) { measured.size.height.toDp() } + 2.dp
            }
            if (entries.isEmpty() && !ui.listLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有生词，去首页查词并点星星收藏",
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 15.ssp(),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 22.sdp())
                            .then(
                                if (selectionMode || catalogLocked) Modifier
                                else Modifier.dragContainer(dragDropState),
                            ),
                        contentPadding = PaddingValues(bottom = 16.sdp()),
                        verticalArrangement = Arrangement.spacedBy(8.sdp()),
                    ) {
                        itemsIndexed(entries, key = { _, item -> "${item.notebookId}:${item.id}:${item.text}" }) { index, entry ->
                            val selected = entry.id in selectedIds
                            if (selectionMode) {
                                WordRowBody(
                                    entry = entry,
                                    showMeaning = ui.hideDefinitions == (entry.id in ui.revealedIds),
                                    wordColumnWidth = cappedWordColumn,
                                    meaningStyle = meaningStyle,
                                    meaningBlockHeight = meaningBlockHeight,
                                    selectionMode = true,
                                    selected = selected,
                                    onSelectToggle = {
                                        selectedIds = if (selected) {
                                            selectedIds - entry.id
                                        } else {
                                            selectedIds + entry.id
                                        }
                                    },
                                    onToggleMeaning = {},
                                    onSpeak = {},
                                )
                            } else if (catalogLocked) {
                                WordRowBody(
                                    entry = entry,
                                    showMeaning = ui.hideDefinitions == (entry.id in ui.revealedIds),
                                    wordColumnWidth = cappedWordColumn,
                                    meaningStyle = meaningStyle,
                                    meaningBlockHeight = meaningBlockHeight,
                                    selectionMode = false,
                                    selected = false,
                                    onSelectToggle = {},
                                    onToggleMeaning = {
                                        lastClickedEntryId = entry.id
                                        onReveal(entry.id)
                                    },
                                    onSpeak = {
                                        lastClickedEntryId = entry.id
                                        onSpeak(entry)
                                    },
                                )
                            } else {
                            DraggableItem(dragDropState = dragDropState, index = index) { isDragging ->
                                SwipeRevealDelete(
                                    revealed = openSwipeId == entry.id,
                                    enabled = !reordering,
                                    onRevealChange = { open ->
                                        openSwipeId = when {
                                            open -> entry.id
                                            openSwipeId == entry.id -> null
                                            else -> openSwipeId
                                        }
                                    },
                                    onDelete = {
                                        openSwipeId = null
                                        onDelete(entry.id)
                                    },
                                ) {
                                    WordRowBody(
                                        entry = entry,
                                        showMeaning = ui.hideDefinitions == (entry.id in ui.revealedIds),
                                        wordColumnWidth = cappedWordColumn,
                                        meaningStyle = meaningStyle,
                                        meaningBlockHeight = meaningBlockHeight,
                                        selectionMode = false,
                                        selected = false,
                                        onSelectToggle = {},
                                        modifier = if (isDragging) Modifier.shadow(8.dp) else Modifier,
                                        onToggleMeaning = {
                                            lastClickedEntryId = entry.id
                                            onReveal(entry.id)
                                        },
                                        onSpeak = {
                                            lastClickedEntryId = entry.id
                                            onSpeak(entry)
                                        },
                                    )
                                }
                            }
                            }
                        }
                    }
                    AlphabetIndexBar(
                        onSelect = { letter -> jumpToAlphabetLetter(letter) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
        if (selectionMode) {
            SelectionActionBar(
                enabled = selectedIds.isNotEmpty(),
                selectAllEnabled = entries.isNotEmpty(),
                allSelected = entries.isNotEmpty() && selectedIds.size == entries.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.sdp(), vertical = 10.sdp())
                    .windowInsetsPadding(WindowInsets.navigationBars),
                onSelectAll = {
                    selectedIds = if (entries.isNotEmpty() && selectedIds.size == entries.size) {
                        emptySet()
                    } else {
                        entries.map { it.id }.toSet()
                    }
                },
                onMove = { showMoveDialog = true },
                onDelete = { showDeleteConfirm = true },
            )
        } else {
            NotebookBottomBar(
                hideDefinitions = ui.hideDefinitions,
                cardEnabled = totalCount > 0,
                onToggleHide = onToggleHide,
                onRecite = {
                    val clicked = lastClickedEntryId
                        ?.takeIf { id -> entries.any { it.id == id } }
                    val startId = clicked
                        ?: listState.firstVisibleItemIndex
                            .coerceIn(0, entries.lastIndex.coerceAtLeast(0))
                            .let { entries.getOrNull(it)?.id }
                    onRecite(startId)
                },
            )
        }
    }

    if (showCreateDialog) {
        StellarInputDialog(
            title = "新建生词本",
            placeholder = "例如：动物、人体器官",
            confirmText = "创建",
            error = createError,
            onDismiss = {
                showCreateDialog = false
                createError = null
            },
            onConfirm = { name ->
                if (name.isBlank()) {
                    createError = "请输入名称"
                } else {
                    createError = null
                    onCreateNotebook(name)
                    showCreateDialog = false
                }
            },
        )
    }

    notebookToDelete?.let { notebook ->
        val count = wordCountInNotebook(notebook.id)
        StellarConfirmDialog(
            title = "删除生词本",
            message = if (count > 0) {
                "确定删除「${notebook.name}」吗？其中的 $count 个词条将一并删除，此操作不可撤销。"
            } else {
                "确定删除「${notebook.name}」吗？此操作不可撤销。"
            },
            confirmText = "删除",
            destructive = true,
            onDismiss = { notebookToDelete = null },
            onConfirm = {
                onDeleteNotebook(notebook.id)
                notebookToDelete = null
                if (selectionMode) {
                    selectionMode = false
                    selectedIds = emptySet()
                }
            },
        )
    }

    if (showDeleteConfirm) {
        StellarConfirmDialog(
            title = "删除词条",
            message = "确定删除选中的 ${selectedIds.size} 个词条吗？此操作不可撤销。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                onDeleteEntries(selectedIds.toList())
                showDeleteConfirm = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }

    if (showMoveDialog) {
        MoveToNotebookDialog(
            notebooks = notebooks.filter { !it.isSystem && it.id != ui.activeNotebookId },
            selectedCount = selectedIds.size,
            onDismiss = { showMoveDialog = false },
            onSelect = { targetId ->
                onMoveEntries(selectedIds.toList(), targetId)
                showMoveDialog = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }

    if (showMoreMenu) {
        NotebookMoreSheet(
            canEdit = !catalogLocked && totalCount > 0,
            onEdit = {
                showMoreMenu = false
                selectionMode = true
                selectedIds = emptySet()
                openSwipeId = null
            },
            onStats = {
                showMoreMenu = false
                showStatsDialog = true
            },
            onDismiss = { showMoreMenu = false },
        )
    }

    if (showStatsDialog) {
        StellarConfirmDialog(
            title = "统计",
            message = "「$activeNotebookName」共 $totalCount 个单词",
            confirmText = "确定",
            dismissText = "关闭",
            onDismiss = { showStatsDialog = false },
            onConfirm = { showStatsDialog = false },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NotebookSwitcher(
    notebooks: List<Notebook>,
    activeNotebookId: Long,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onDeleteRequest: (Notebook) -> Unit,
) {
    val chipHeight = 36.sdp()
    val chipRadius = 18.sdp()
    val chipShape = RoundedCornerShape(chipRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.sdp(), end = 12.sdp(), top = 10.sdp(), bottom = 10.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.sdp()),
    ) {
        Row(
            Modifier
                .height(chipHeight)
                .clip(chipShape)
                .border(1.dp, Stellar.Outline.copy(alpha = 0.45f), chipShape)
                .clickable(onClick = onCreate)
                .padding(horizontal = 12.sdp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.sdp()),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "新建生词本",
                tint = Stellar.Cyan,
                modifier = Modifier.size(16.sdp()),
            )
            Text(
                text = "新建",
                color = Stellar.Cyan,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            notebooks.forEach { notebook ->
                val selected = notebook.id == activeNotebookId
                val canDelete = !notebook.isSystem
                val base = catalogChipStyle(notebook) ?: CatalogChipStyle(
                    background = Stellar.SurfaceHigh,
                    foreground = Stellar.OnSurfaceVariant,
                )
                val style = if (selected) {
                    CatalogChipStyle(
                        background = Stellar.Cyan,
                        foreground = Stellar.OnPrimary,
                        dashedBorder = null,
                    )
                } else {
                    base
                }
                Row(
                    modifier = Modifier
                        .height(chipHeight)
                        .clip(chipShape)
                        .background(style.background)
                        .then(
                            if (style.dashedBorder != null) {
                                Modifier.drawBehind {
                                    val stroke = 1.dp.toPx()
                                    val inset = stroke / 2f
                                    drawRoundRect(
                                        color = style.dashedBorder,
                                        topLeft = Offset(inset, inset),
                                        size = size.copy(
                                            width = size.width - stroke,
                                            height = size.height - stroke,
                                        ),
                                        cornerRadius = CornerRadius(chipRadius.toPx()),
                                        style = Stroke(
                                            width = stroke,
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                            ),
                                        ),
                                    )
                                }
                            } else if (!selected && base.dashedBorder == null) {
                                Modifier.border(1.dp, Stellar.Outline.copy(alpha = 0.35f), chipShape)
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(
                            onClick = { onSelect(notebook.id) },
                            onLongClick = {
                                if (canDelete) onDeleteRequest(notebook)
                            },
                        )
                        .padding(horizontal = 12.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.sdp()),
                ) {
                    Text(
                        text = notebook.name,
                        color = style.foreground,
                        fontSize = 13.ssp(),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (notebook.isSystem) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "系统词书不可修改",
                            tint = style.foreground,
                            modifier = Modifier.size(12.sdp()),
                        )
                    }
                }
            }
        }
    }
}

private data class CatalogChipStyle(
    val background: Color,
    val foreground: Color,
    val dashedBorder: Color? = null,
)

private fun catalogChipStyle(notebook: Notebook): CatalogChipStyle? {
    val slug = notebook.slug.orEmpty()
    return when {
        slug == Notebook.ZHONGKAO_SLUG || notebook.name.contains("中考") -> CatalogChipStyle(
            background = Color(0xFF2C3348),
            foreground = Color(0xFFD4D8E8),
            dashedBorder = Color(0xB396A0C0),
        )
        slug == Notebook.GAOKAO_SLUG || notebook.name.contains("高考") -> CatalogChipStyle(
            background = Color(0xFF3A2C3A),
            foreground = Color(0xFFE4D0DE),
            dashedBorder = Color(0xB3B894AD),
        )
        slug == Notebook.CET4_SLUG || notebook.name.contains("四级") -> CatalogChipStyle(
            background = Color(0xFF243F3F),
            foreground = Color(0xFFD5E3E2),
            dashedBorder = Color(0xB38FAEAD),
        )
        slug == Notebook.CET6_SLUG || notebook.name.contains("六级") -> CatalogChipStyle(
            background = Color(0xFF3A3529),
            foreground = Color(0xFFD8CEB6),
            dashedBorder = Color(0xB3B0A584),
        )
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveToNotebookDialog(
    notebooks: List<Notebook>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(24.sdp())
    val accent = Stellar.Cyan
    val accentSoft = Stellar.CyanSoft
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.sdp())
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.35f),
                    spotColor = accent.copy(alpha = 0.28f),
                )
                .clip(shape)
                .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                .border(1.dp, accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 22.sdp(), vertical = 20.sdp()),
        ) {
            Text(
                text = "移动到生词本",
                color = accentSoft,
                fontSize = 22.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.sdp()))
            Text(
                text = "已选择 $selectedCount 个词条",
                color = Stellar.OnSurfaceVariant,
                fontSize = 14.ssp(),
            )
            Spacer(Modifier.height(14.sdp()))
            if (notebooks.isEmpty()) {
                Text("没有其他生词本", color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.sdp()),
                    verticalArrangement = Arrangement.spacedBy(8.sdp()),
                ) {
                    notebooks.forEach { notebook ->
                        Box(
                            modifier = Modifier
                                .height(36.sdp())
                                .clip(RoundedCornerShape(18.sdp()))
                                .background(Stellar.CyanSoft)
                                .clickable { onSelect(notebook.id) }
                                .padding(horizontal = 14.sdp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = notebook.name,
                                color = Stellar.OnPrimary,
                                fontSize = 13.ssp(),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.sdp()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "取消",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.sdp(), vertical = 10.sdp()),
                )
            }
        }
    }
}

@Composable
private fun NotebookMoreSheet(
    canEdit: Boolean,
    onEdit: () -> Unit,
    onStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 22.sdp(), topEnd = 22.sdp())
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .shadow(
                        elevation = 20.dp,
                        shape = shape,
                        ambientColor = Stellar.Cyan.copy(alpha = 0.28f),
                        spotColor = Stellar.Cyan.copy(alpha = 0.22f),
                    )
                    .clip(shape)
                    .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                    .border(1.dp, Stellar.Cyan.copy(alpha = 0.35f), shape)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.sdp(), vertical = 16.sdp()),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.sdp())
                        .height(4.sdp())
                        .clip(RoundedCornerShape(999.dp))
                        .background(Stellar.Outline.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.height(16.sdp()))
                NotebookMoreAction(
                    label = "编辑",
                    enabled = canEdit,
                    onClick = onEdit,
                )
                Spacer(Modifier.height(8.sdp()))
                NotebookMoreAction(
                    label = "统计",
                    enabled = true,
                    onClick = onStats,
                )
                Spacer(Modifier.height(12.sdp()))
                Text(
                    text = "取消",
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp()))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.sdp()),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NotebookMoreAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (enabled) Stellar.SurfaceHigh else Stellar.SurfaceHigh.copy(alpha = 0.55f)
    Text(
        text = label,
        color = if (enabled) Stellar.CyanSoft else Stellar.OnSurfaceVariant.copy(alpha = 0.45f),
        fontSize = 17.ssp(),
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp()))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.sdp()),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SelectionActionBar(
    enabled: Boolean,
    selectAllEnabled: Boolean,
    allSelected: Boolean,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.sdp()),
    ) {
        SelectionActionButton(
            label = if (allSelected) "取消全选" else "全选",
            enabled = selectAllEnabled,
            destructive = false,
            modifier = Modifier.weight(1f),
            onClick = onSelectAll,
        )
        SelectionActionButton(
            label = "移动到…",
            enabled = enabled,
            destructive = false,
            modifier = Modifier.weight(1f),
            onClick = onMove,
        )
        SelectionActionButton(
            label = "删除",
            enabled = enabled,
            destructive = true,
            modifier = Modifier.weight(1f),
            onClick = onDelete,
        )
    }
}

@Composable
private fun SelectionActionButton(
    label: String,
    enabled: Boolean,
    destructive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fill = when {
        !enabled -> Stellar.SurfaceHigh
        destructive -> Stellar.Pink
        else -> Stellar.CyanSoft
    }
    val textColor = if (enabled) Stellar.OnPrimary else Stellar.OnSurfaceVariant
    Box(
        modifier
            .shadow(
                elevation = if (enabled) 10.dp else 4.dp,
                shape = RoundedCornerShape(22.sdp()),
                ambientColor = fill.copy(alpha = if (enabled) 0.35f else 0.1f),
                spotColor = fill.copy(alpha = if (enabled) 0.35f else 0.1f),
            )
            .clip(RoundedCornerShape(22.sdp()))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.sdp()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 13.ssp(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SwipeRevealDelete(
    revealed: Boolean,
    enabled: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteWidth = 76.sdp()
    val deletePx = with(density) { deleteWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val showDelete = offsetX.value < -0.5f

    LaunchedEffect(revealed, deletePx) {
        offsetX.animateTo(if (revealed) -deletePx else 0f, tween(180))
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            offsetX.snapTo((offsetX.value + delta).coerceIn(-deletePx, 0f))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        if (showDelete) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(deleteWidth)
                        .background(LocalStellar.current.Pink.copy(alpha = 0.82f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("删除", color = Color.White, fontSize = 15.ssp(), fontWeight = FontWeight.Medium)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(Stellar.Surface)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        val open = offsetX.value <= -deletePx / 2f
                        onRevealChange(open)
                        scope.launch {
                            offsetX.animateTo(if (open) -deletePx else 0f, tween(180))
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun ListTopBar(
    title: String,
    selectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onOpenMore: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Box(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .drawBehind {
                drawLine(
                    color = line,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 8.sdp()),
    ) {
        if (selectionMode) {
            Text(
                text = "取消",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onCancelSelection)
                    .padding(vertical = 8.sdp(), horizontal = 4.sdp()),
            )
            Text(
                text = "已选 $selectedCount",
                modifier = Modifier.align(Alignment.Center),
                color = Stellar.CyanSoft,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
            )
        } else {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBackIosNew,
                        contentDescription = "返回",
                        tint = Stellar.OnSurfaceVariant,
                        modifier = Modifier.size(18.sdp()),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title.ifBlank { "生词本" },
                        color = Stellar.CyanSoft,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "列表模式",
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 11.ssp(),
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(onClick = onOpenMore) {
                    Text(
                        text = "···",
                        color = Stellar.Cyan,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordRowBody(
    entry: VocabEntry,
    showMeaning: Boolean,
    wordColumnWidth: Dp,
    meaningStyle: TextStyle,
    meaningBlockHeight: Dp,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleMeaning: () -> Unit,
    onSpeak: () -> Unit,
) {
    val ipa = buildSlashIpa(entry)
    val palette = LocalStellar.current
    val meaningText = remember(entry.definitions, palette) {
        if (entry.definitions.isEmpty()) null else compactMeaningAnnotated(entry.definitions, palette)
    }
    // Explicit height: intrinsic sizing would shrink rows whose meaning is shorter.
    val rowHeight = meaningBlockHeight + MeaningPadding * 2

    // Own gesture loop: clickable() inside a scrollable list delays the press visual.
    var speakPressed by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .height(rowHeight)
            .then(
                if (selectionMode) {
                    Modifier
                        .clickable(onClick = onSelectToggle)
                        .background(
                            if (selected) Stellar.Cyan.copy(alpha = 0.12f) else Stellar.Surface,
                        )
                } else {
                    Modifier.background(Stellar.Surface)
                },
            ),
    ) {
        if (selectionMode) {
            Box(
                Modifier
                    .width(44.sdp())
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (selected) Stellar.CyanSoft else Stellar.OnSurfaceVariant,
                    modifier = Modifier.size(22.sdp()),
                )
            }
        }
        Box(
            Modifier
                .width(3.sdp())
                .fillMaxHeight()
                .background(if (speakPressed && !selectionMode) Stellar.CyanBright else Stellar.Cyan),
        )
        Column(
            Modifier
                .width(wordColumnWidth)
                .fillMaxHeight()
                .background(
                    when {
                        selectionMode && selected -> Stellar.Cyan.copy(alpha = 0.10f)
                        speakPressed && !selectionMode -> Stellar.Cyan.copy(alpha = 0.18f)
                        else -> Stellar.SurfaceContainer
                    },
                )
                .then(
                    if (selectionMode) {
                        Modifier
                    } else {
                        Modifier.pointerInput(entry.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                speakPressed = true
                                val up = waitForUpOrCancellation()
                                speakPressed = false
                                if (up != null) onSpeak()
                            }
                        }
                    },
                )
                .padding(horizontal = MeaningPadding, vertical = MeaningPadding),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = entry.text,
                color = Stellar.CyanSoft,
                fontSize = 17.ssp(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.sdp()))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpeakIconGap),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "朗读",
                    tint = if (speakPressed) Stellar.CyanBright else Stellar.Cyan,
                    modifier = Modifier.size(SpeakIconSize),
                )
                if (ipa != null) {
                    Text(
                        text = ipa,
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 13.ssp(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectionMode) Modifier else Modifier.clickable(onClick = onToggleMeaning),
                )
                .padding(horizontal = MeaningPadding, vertical = MeaningPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = meaningText ?: AnnotatedString("—"),
                    color = if (meaningText == null) Stellar.OnSurfaceVariant else Color.Unspecified,
                    style = meaningStyle,
                    maxLines = MeaningMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!showMeaning) {
                    DefinitionMask(Modifier.matchParentSize())
                }
            }
        }
    }
}

private fun formatPos(pos: String): String = when {
    pos.isBlank() -> ""
    pos.endsWith(".") || pos.startsWith("【") -> pos
    else -> "$pos."
}

/** One flowing block so long meanings wrap into all reserved lines. */
private fun compactMeaningAnnotated(
    definitions: List<Definition>,
    palette: StellarPalette,
): AnnotatedString =
    buildAnnotatedString {
        definitions.forEachIndexed { index, def ->
            if (index > 0) append("  ")
            val posText = formatPos(def.pos)
            val posColor = if (def.isUserAdded) palette.Pink else stellarPosColor(def.pos, palette)
            val meaningColor =
                if (def.isUserAdded) palette.Pink.copy(alpha = 0.92f) else palette.OnSurface
            if (posText.isNotBlank()) {
                withStyle(
                    SpanStyle(
                        color = posColor,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(color = posColor.copy(alpha = 0.45f), blurRadius = 8f),
                    ),
                ) {
                    append(posText)
                    append(" ")
                }
            }
            withStyle(SpanStyle(color = meaningColor)) {
                append(def.meaning.trim())
            }
        }
    }

@Composable
private fun DefinitionMask(modifier: Modifier = Modifier) {
    val palette = LocalStellar.current
    // Tint with theme accent (same family as list word column / card accents) — not flat gray.
    val fill = lerp(
        palette.SurfaceContainer.copy(alpha = 1f),
        palette.Cyan,
        0.22f,
    )
    val hatch = palette.Cyan.copy(alpha = 0.38f)
    Canvas(modifier.clip(RoundedCornerShape(4.sdp()))) {
        drawRect(fill)
        val step = 14.dp.toPx()
        val stroke = 7.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = hatch,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stroke,
            )
            x += step
        }
    }
}

@Composable
private fun NotebookBottomBar(
    hideDefinitions: Boolean,
    cardEnabled: Boolean,
    onToggleHide: () -> Unit,
    onRecite: () -> Unit,
) {
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
            NotebookBottomAction(
                icon = if (hideDefinitions) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                label = if (hideDefinitions) "显示释义" else "隐藏释义",
                accent = true,
                enabled = true,
                onClick = onToggleHide,
                modifier = Modifier.weight(1f),
            )
            NotebookBottomAction(
                icon = Icons.Outlined.Style,
                label = "卡片模式",
                accent = true,
                enabled = cardEnabled,
                onClick = onRecite,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotebookBottomAction(
    icon: ImageVector,
    label: String,
    accent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> Stellar.OnSurfaceVariant.copy(alpha = 0.4f)
        accent -> Stellar.Cyan
        else -> Stellar.OnSurfaceVariant
    }
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.sdp()),
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.ssp(),
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 3.sdp()),
        )
    }
}

private fun buildSlashIpa(entry: VocabEntry): String? {
    val raw = entry.ipaUk?.takeIf { it.isNotBlank() }
        ?: entry.ipaUs?.takeIf { it.isNotBlank() }
        ?: return null
    // Dictionaries often list several variants; one is enough here and keeps the column narrow.
    val core = raw.trim().substringBefore(';').substringBefore(',').trim().trim('/').trim()
    if (core.isEmpty()) return null
    return "/$core/"
}
