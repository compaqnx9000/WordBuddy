package com.zeroglab.hotwords.ui.card

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zeroglab.hotwords.data.Accent
import com.zeroglab.hotwords.data.AppTheme
import com.zeroglab.hotwords.data.Definition
import com.zeroglab.hotwords.data.ExampleSentence
import com.zeroglab.hotwords.data.NaturalPhonics
import com.zeroglab.hotwords.data.VocabEntry
import com.zeroglab.hotwords.ui.components.EditMeaningDialog
import com.zeroglab.hotwords.ui.components.EntryRelatedBlocks
import com.zeroglab.hotwords.ui.components.ImageSourceDialog
import com.zeroglab.hotwords.ui.components.MnemonicImage
import com.zeroglab.hotwords.ui.components.highlightHeadword
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.LocalStellar
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.StellarDefinitionRow
import com.zeroglab.hotwords.ui.lookup.StellarPalette
import com.zeroglab.hotwords.ui.lookup.hasStellarWallpaperBackground
import com.zeroglab.hotwords.ui.lookup.stellarGlass
import com.zeroglab.hotwords.ui.lookup.stellarPanelBackgroundColor
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun CardModeScreen(
    entries: List<VocabEntry>,
    index: Int,
    shuffled: Boolean,
    playing: Boolean,
    speakOnPageChange: Boolean,
    accent: Accent,
    appTheme: AppTheme,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPlayToggle: () -> Unit,
    onShuffle: () -> Unit,
    onSpeak: () -> Unit,
    onToggleSpeak: () -> Unit,
    onSpeakAccent: (uk: Boolean) -> Unit,
    imageBusy: Boolean,
    imageError: String?,
    onPickImage: (Long, Uri) -> Unit,
    onGenerateAi: (Long, String) -> Unit,
    onClearImageError: () -> Unit,
    onUpdateDefinitions: (Long, List<Definition>) -> Unit,
    onSpeakText: (String) -> Unit,
    onSpeakTextSlow: (String) -> Unit,
    onSpeakSyllables: (List<String>) -> Unit,
    onToggleRelatedStar: (VocabEntry) -> Unit,
    isRelatedWordSaved: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var showImageDialog by remember { mutableStateOf(false) }
    var imageTargetId by remember { mutableStateOf<Long?>(null) }
    var meaningEditEntry by remember { mutableStateOf<VocabEntry?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = imageTargetId
        if (uri != null && id != null) {
            showImageDialog = false
            onPickImage(id, uri)
        }
    }
    val total = entries.size
    val safeIndex = if (total == 0) 0 else index.coerceIn(0, total - 1)
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { total.coerceAtLeast(1) },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(safeIndex, total) {
        if (total == 0) return@LaunchedEffect
        if (pagerState.currentPage != safeIndex) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(pagerState, total) {
        if (total == 0) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageSelected(page)
            }
    }

    fun goPrev() {
        if (total == 0) return
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onPrev()
        }
    }

    fun goNext() {
        if (total == 0) return
        if (pagerState.currentPage < total - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else {
            onNext()
        }
    }

    val glow = Stellar.Cyan.copy(alpha = 0.14f)
    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground()
            .then(
                if (!hasStellarWallpaperBackground()) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glow, Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.minDimension * 0.85f,
                            ),
                            radius = size.minDimension * 0.85f,
                            center = Offset(size.width / 2f, 0f),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        CardTopBar(onBack = onBack)
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("词库是空的", color = Stellar.OnSurfaceVariant, fontSize = 16.ssp())
            }
        } else {
            var lockWordPager by remember { mutableStateOf(false) }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !lockWordPager,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.72f,
                ),
            ) { page ->
                CardPage(
                    entry = entries[page],
                    imageBusy = imageBusy && entries[page].id == imageTargetId,
                    accent = accent,
                    onSpeakWord = onSpeak,
                    onSpeakAccent = onSpeakAccent,
                    onOpenImageChooser = {
                        imageTargetId = entries[page].id
                        showImageDialog = true
                    },
                    onEditMeaning = { meaningEditEntry = entries[page] },
                    onSpeakText = onSpeakText,
                    onSpeakTextSlow = onSpeakTextSlow,
                    onSpeakSyllables = onSpeakSyllables,
                    onToggleRelatedStar = onToggleRelatedStar,
                    isRelatedWordSaved = isRelatedWordSaved,
                    onExampleTouchChange = { touching -> lockWordPager = touching },
                )
            }
        }
        CardControlBar(
            pageLabel = if (total == 0) "0 / 0" else "${pagerState.currentPage + 1} / $total",
            shuffled = shuffled,
            playing = playing,
            speakOnPageChange = speakOnPageChange,
            onList = onBack,
            onShuffle = onShuffle,
            onPrev = ::goPrev,
            onPlayToggle = onPlayToggle,
            onNext = ::goNext,
            onToggleSpeak = onToggleSpeak,
        )
    }

    if (showImageDialog) {
        val target = entries.firstOrNull { it.id == imageTargetId }
        ImageSourceDialog(
            definitions = target?.definitions.orEmpty(),
            busy = imageBusy,
            error = imageError,
            stellar = true,
            onPickGallery = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onGenerateAi = { meaning ->
                imageTargetId?.let { onGenerateAi(it, meaning) }
            },
            onDismiss = {
                showImageDialog = false
                onClearImageError()
            },
        )
    }

    meaningEditEntry?.let { entry ->
        EditMeaningDialog(
            word = entry.text,
            definitions = entry.definitions,
            stellar = true,
            onDismiss = { meaningEditEntry = null },
            onSave = { definitions ->
                onUpdateDefinitions(entry.id, definitions)
                meaningEditEntry = null
            },
        )
    }
}

@Composable
private fun CardPage(
    entry: VocabEntry,
    imageBusy: Boolean,
    accent: Accent,
    onSpeakWord: () -> Unit,
    onSpeakAccent: (uk: Boolean) -> Unit,
    onOpenImageChooser: () -> Unit,
    onEditMeaning: () -> Unit,
    onSpeakText: (String) -> Unit,
    onSpeakTextSlow: (String) -> Unit,
    onSpeakSyllables: (List<String>) -> Unit,
    onToggleRelatedStar: (VocabEntry) -> Unit,
    isRelatedWordSaved: (String) -> Boolean,
    onExampleTouchChange: (Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    var phonicsOn by remember(entry.id) { mutableStateOf(false) }
    val phonics = remember(entry.text) { NaturalPhonics.analyze(entry.text) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.sdp()),
        verticalArrangement = Arrangement.spacedBy(16.sdp()),
    ) {
        Spacer(Modifier.height(4.sdp()))
        CardWordHeader(
            entry = entry,
            phonics = phonics,
            phonicsOn = phonicsOn,
            imageBusy = imageBusy,
            accent = accent,
            onSpeakWord = onSpeakWord,
            onSpeakAccent = onSpeakAccent,
            onOpenImageChooser = onOpenImageChooser,
            onNormalMode = { phonicsOn = false },
            onPhonicsMode = {
                phonicsOn = true
                onSpeakSyllables(phonics.syllables)
            },
        )
        CardDefinitionCard(
            definitions = entry.definitions,
            onEditMeaning = onEditMeaning,
        )
        if (entry.nearWords.isNotEmpty() || entry.synonyms.isNotEmpty() || entry.antonyms.isNotEmpty()) {
            EntryRelatedBlocks(
                nearWords = entry.nearWords,
                synonyms = entry.synonyms,
                antonyms = entry.antonyms,
                onSpeakWord = onSpeakText,
                onToggleStar = onToggleRelatedStar,
                isWordSaved = isRelatedWordSaved,
                stellar = true,
            )
        }
        if (entry.examples.isNotEmpty()) {
            CardExamplePanel(
                word = entry.text,
                examples = entry.examples,
                onSpeakExample = onSpeakText,
                onSpeakExampleSlow = onSpeakTextSlow,
                onTouchChange = onExampleTouchChange,
            )
        }
        Spacer(Modifier.height(12.sdp()))
    }
}

@Composable
private fun CardWordHeader(
    entry: VocabEntry,
    phonics: NaturalPhonics.PhonicsBreakdown,
    phonicsOn: Boolean,
    imageBusy: Boolean,
    accent: Accent,
    onSpeakWord: () -> Unit,
    onSpeakAccent: (uk: Boolean) -> Unit,
    onOpenImageChooser: () -> Unit,
    onNormalMode: () -> Unit,
    onPhonicsMode: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.sdp())) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.sdp()),
        ) {
            Column(Modifier.weight(1f)) {
                CardWordDisplay(
                    word = entry.text,
                    phonics = phonics,
                    phonicsOn = phonicsOn,
                )
                Spacer(Modifier.height(6.sdp()))
                Row(horizontalArrangement = Arrangement.spacedBy(12.sdp())) {
                    if (!entry.ipaUk.isNullOrBlank()) {
                        Text(
                            text = "UK [${entry.ipaUk}]",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 13.ssp(),
                        )
                    }
                    if (!entry.ipaUs.isNullOrBlank()) {
                        Text(
                            text = "US [${entry.ipaUs}]",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 13.ssp(),
                        )
                    }
                }
                Spacer(Modifier.height(8.sdp()))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.sdp()))
                        .background(Stellar.SurfaceHigh)
                        .border(1.dp, Stellar.Outline.copy(alpha = 0.4f), RoundedCornerShape(20.sdp()))
                        .padding(3.sdp()),
                ) {
                    CardAccentChip("正常", selected = !phonicsOn, onClick = onNormalMode)
                    CardAccentChip("自然拼读", selected = phonicsOn, onClick = onPhonicsMode)
                }
            }
            WordMnemonicThumb(
                imageBlob = entry.imageBlob,
                hasImage = entry.hasImage,
                busy = imageBusy,
                onChangeImage = onOpenImageChooser,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.sdp()),
        ) {
            Box(
                Modifier
                    .size(40.sdp())
                    .clip(CircleShape)
                    .background(Stellar.CyanBright)
                    .clickable(onClick = onSpeakWord),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "发音",
                    tint = Stellar.OnPrimary,
                    modifier = Modifier.size(20.sdp()),
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.sdp()))
                    .background(Stellar.SurfaceHigh)
                    .border(1.dp, Stellar.Outline.copy(alpha = 0.4f), RoundedCornerShape(20.sdp()))
                    .padding(3.sdp()),
            ) {
                CardAccentChip("US", selected = accent == Accent.US) { onSpeakAccent(false) }
                CardAccentChip("UK", selected = accent == Accent.UK) { onSpeakAccent(true) }
            }
        }
    }
}

@Composable
private fun CardWordDisplay(
    word: String,
    phonics: NaturalPhonics.PhonicsBreakdown,
    phonicsOn: Boolean,
) {
    val fontSize = when {
        word.length >= 14 -> 28.ssp()
        word.length >= 10 -> 32.ssp()
        else -> 36.ssp()
    }
    val wordStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = fontSize * 1.2f,
    )
    val palette = LocalStellar.current
    val phonicsText = remember(word, phonics, palette) { phonicsDottedWord(word, phonics, palette) }
    val guideLineColor = Stellar.Outline.copy(alpha = 0.28f)
    val guidePaddingH = 10.sdp()
    val guidePaddingV = 8.sdp()
    val density = LocalDensity.current
    val slotHeight = with(density) { wordStyle.lineHeight.toDp() + guidePaddingV * 2 }
    Box(
        Modifier
            .height(slotHeight)
            .wrapContentWidth(Alignment.Start)
            .drawBehind {
                if (!phonicsOn) return@drawBehind
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                val stroke = 1.dp.toPx()
                drawLine(
                    color = guideLineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke,
                    pathEffect = dash,
                )
                drawLine(
                    color = guideLineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = stroke,
                    pathEffect = dash,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = guidePaddingH)) {
            Text(
                text = word,
                style = wordStyle.copy(
                    color = Stellar.CyanSoft,
                    shadow = Shadow(
                        color = Stellar.Cyan.copy(alpha = 0.55f),
                        blurRadius = 22f,
                    ),
                ),
                modifier = Modifier.alpha(if (phonicsOn) 0f else 1f),
            )
            Text(
                text = phonicsText,
                style = wordStyle,
                modifier = Modifier.alpha(if (phonicsOn) 1f else 0f),
            )
        }
    }
}

private fun phonicsDottedWord(
    word: String,
    phonics: NaturalPhonics.PhonicsBreakdown,
    palette: StellarPalette,
) = buildAnnotatedString {
    val syllables = phonics.syllables.ifEmpty { listOf(word) }
    val colors = listOf(
        palette.CyanSoft to palette.Cyan,
        palette.Pink to palette.Pink,
        palette.Gold to palette.Gold,
        palette.CyanBright to palette.CyanBright,
    )
    syllables.forEachIndexed { index, syl ->
        if (index > 0) {
            withStyle(
                SpanStyle(
                    color = palette.OnSurfaceVariant.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append("·")
            }
        }
        val (fill, glow) = colors[index % colors.size]
        withStyle(
            SpanStyle(
                color = fill,
                shadow = Shadow(color = glow.copy(alpha = 0.65f), blurRadius = 18f),
            ),
        ) {
            append(syl)
        }
    }
}

@Composable
private fun CardAccentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Stellar.OnSurface else Stellar.OnSurfaceVariant,
        fontSize = 11.ssp(),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,
        modifier = Modifier
            .clip(RoundedCornerShape(16.sdp()))
            .background(if (selected) Color(0xFF3A4344) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.sdp(), vertical = 6.sdp()),
    )
}

@Composable
private fun CardDefinitionCard(
    definitions: List<Definition>,
    onEditMeaning: () -> Unit,
) {
    val originals = remember(definitions) { definitions.filter { !it.isUserAdded } }
    val userNotes = remember(definitions) { definitions.filter { it.isUserAdded } }

    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(18.sdp()),
    ) {
        if (definitions.isEmpty()) {
            Text("暂无释义", color = Stellar.OnSurfaceVariant, fontSize = 16.ssp())
        } else {
            originals.forEachIndexed { index, def ->
                if (index > 0) Spacer(Modifier.height(12.sdp()))
                StellarDefinitionRow(def)
            }
            if (userNotes.isNotEmpty()) {
                if (originals.isNotEmpty()) {
                    Spacer(Modifier.height(14.sdp()))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Stellar.Pink.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(10.sdp()))
                    Text(
                        text = "我的补充",
                        color = Stellar.Pink,
                        fontSize = 11.ssp(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.em,
                    )
                    Spacer(Modifier.height(10.sdp()))
                }
                userNotes.forEachIndexed { index, def ->
                    if (index > 0) Spacer(Modifier.height(10.sdp()))
                    StellarDefinitionRow(def, userNote = true)
                }
            }
        }
        Spacer(Modifier.height(14.sdp()))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Stellar.Outline.copy(alpha = 0.35f)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.sdp())
                .clickable(onClick = onEditMeaning),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = Stellar.Cyan,
                modifier = Modifier.size(14.sdp()),
            )
            Spacer(Modifier.width(4.sdp()))
            Text(
                text = "编辑释义",
                color = Stellar.Cyan,
                fontSize = 11.ssp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
            )
        }
    }
}

@Composable
private fun WordMnemonicThumb(
    imageBlob: ByteArray?,
    hasImage: Boolean,
    busy: Boolean,
    onChangeImage: () -> Unit,
) {
    Box(
        Modifier
            .size(108.sdp())
            .clip(RoundedCornerShape(16.sdp()))
            .stellarGlass()
            .clickable(enabled = !busy, onClick = onChangeImage)
            .padding(6.sdp()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(24.sdp()),
                color = Stellar.Cyan,
                strokeWidth = 2.dp,
            )
            hasImage -> MnemonicImage(
                imageBlob = imageBlob,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.sdp())),
            )
            else -> {
                val dashColor = Stellar.Cyan.copy(alpha = 0.28f)
                Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.sdp()))
                    .drawBehind {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        drawRoundRect(
                            color = dashColor,
                            style = Stroke(width = 2f, pathEffect = dash),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = Stellar.CyanSoft.copy(alpha = 0.85f),
                    fontSize = 36.ssp(),
                    fontWeight = FontWeight.Light,
                )
            }
            }
        }
    }
}

@Composable
private fun CardExamplePanel(
    word: String,
    examples: List<ExampleSentence>,
    onSpeakExample: (String) -> Unit,
    onSpeakExampleSlow: (String) -> Unit,
    onTouchChange: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { examples.size })
    val blockOuterPagerScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(x = available.x, y = 0f)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTouchChange(true)
                    try {
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                    } finally {
                        onTouchChange(false)
                    }
                }
            }
            .nestedScroll(blockOuterPagerScroll)
            .stellarGlass()
            .padding(horizontal = 20.sdp(), vertical = 22.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.sdp()),
    ) {
        Text(
            text = "例句",
            color = Stellar.OnSurfaceVariant,
            fontSize = 11.ssp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = examples.size > 1,
        ) { page ->
            val example = examples[page]
            val highlight = Stellar.CyanSoft
            val highlighted = remember(word, example.english, highlight) {
                highlightHeadword(example.english, word, highlight)
            }
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = highlighted,
                    color = Stellar.OnSurface,
                    fontSize = 22.ssp(),
                    lineHeight = 30.ssp(),
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.sdp()))
                Text(
                    text = example.chinese,
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 16.ssp(),
                    lineHeight = 24.ssp(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.sdp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExampleActionCircle(
                onClick = {
                    val example = examples.getOrNull(pagerState.currentPage) ?: return@ExampleActionCircle
                    onSpeakExample(example.english)
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "朗读例句",
                    tint = Stellar.Cyan,
                    modifier = Modifier.size(20.sdp()),
                )
            }
            ExampleActionCircle(
                onClick = {
                    val example = examples.getOrNull(pagerState.currentPage) ?: return@ExampleActionCircle
                    onSpeakExampleSlow(example.english)
                },
            ) {
                Text(
                    text = "SLOW",
                    color = Stellar.Cyan,
                    fontSize = 10.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.em,
                )
            }
        }
        if (examples.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp())) {
                repeat(examples.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 7.sdp() else 6.sdp())
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Stellar.Cyan else Stellar.Outline,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleActionCircle(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(40.sdp())
            .clip(CircleShape)
            .background(Stellar.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CardTopBar(onBack: () -> Unit) {
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Row(
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
        Text(
            text = "Vocabulary",
            modifier = Modifier.weight(1f),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(48.sdp()))
    }
}

@Composable
private fun CardControlBar(
    pageLabel: String,
    shuffled: Boolean,
    playing: Boolean,
    speakOnPageChange: Boolean,
    onList: () -> Unit,
    onShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleSpeak: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Stellar.SurfaceContainer.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.sdp(), vertical = 8.sdp()),
        verticalArrangement = Arrangement.spacedBy(6.sdp()),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "上一个",
                    tint = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .size(18.sdp())
                        .clickable(onClick = onPrev),
                )
                Text(
                    text = pageLabel,
                    color = Stellar.OnSurfaceVariant,
                    fontSize = 11.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.em,
                    modifier = Modifier.padding(horizontal = 12.sdp()),
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "下一个",
                    tint = Stellar.OnSurfaceVariant,
                    modifier = Modifier
                        .size(18.sdp())
                        .clickable(onClick = onNext),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.List,
                contentDescription = "列表",
                tint = Stellar.OnSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.sdp())
                    .clickable(onClick = onList),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.Outlined.Shuffle,
                contentDescription = "随机",
                tint = if (shuffled) Stellar.Cyan else Stellar.OnSurfaceVariant,
                modifier = Modifier
                    .size(24.sdp())
                    .clickable(onClick = onShuffle),
            )
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "上一个",
                tint = Stellar.OnSurface,
                modifier = Modifier
                    .size(32.sdp())
                    .clickable(onClick = onPrev),
            )
            Box(
                Modifier
                    .size(52.sdp())
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = Stellar.CyanSoft.copy(alpha = 0.40f),
                        spotColor = Stellar.CyanSoft.copy(alpha = 0.40f),
                    )
                    .clip(CircleShape)
                    .background(Stellar.CyanSoft)
                    .clickable(onClick = onPlayToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Stellar.OnPrimary,
                    modifier = Modifier.size(28.sdp()),
                )
            }
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "下一个",
                tint = Stellar.OnSurface,
                modifier = Modifier
                    .size(32.sdp())
                    .clickable(onClick = onNext),
            )
            Icon(
                imageVector = if (speakOnPageChange) {
                    Icons.AutoMirrored.Outlined.VolumeUp
                } else {
                    Icons.AutoMirrored.Outlined.VolumeOff
                },
                contentDescription = if (speakOnPageChange) "关闭朗读" else "开启朗读",
                tint = if (speakOnPageChange) Stellar.Cyan else Stellar.OnSurfaceVariant,
                modifier = Modifier
                    .size(24.sdp())
                    .clickable(onClick = onToggleSpeak),
            )
        }
    }
}
