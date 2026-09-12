package com.hotgis.wordbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hotgis.wordbuddy.data.LookupCache
import com.hotgis.wordbuddy.data.DictionaryClient
import com.hotgis.wordbuddy.data.VocabEntry
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPosColor
import com.hotgis.wordbuddy.ui.theme.HwColors
import com.hotgis.wordbuddy.ui.theme.hwColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// SurfaceGray aliases SurfaceGray in theme object

private data class RelatedTab(
    val title: String,
    val words: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryRelatedBlocks(
    nearWords: List<String>,
    synonyms: List<String>,
    antonyms: List<String>,
    onSpeakWord: (String) -> Unit,
    onToggleStar: (VocabEntry) -> Unit,
    isWordSaved: (String) -> Boolean = { false },
    compact: Boolean = false,
    stellar: Boolean = false,
) {
    val tabs = remember(nearWords, synonyms, antonyms) {
        buildList {
            if (synonyms.isNotEmpty()) add(RelatedTab("近义词", synonyms))
            if (nearWords.isNotEmpty()) add(RelatedTab("形近词", nearWords))
            if (antonyms.isNotEmpty()) add(RelatedTab("反义词", antonyms))
        }
    }
    if (tabs.isEmpty()) return

    var selectedTab by remember(tabs) { mutableIntStateOf(0) }
    var previewWord by remember { mutableStateOf<String?>(null) }
    val safeTab = selectedTab.coerceIn(0, tabs.lastIndex)
    val currentWords = tabs[safeTab].words
    val colors = hwColors()

    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalArrangement = Arrangement.spacedBy(8.sdp()),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == safeTab
                if (stellar) {
                    Text(
                        text = tab.title,
                        color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                        fontSize = 11.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.sdp()))
                            .background(if (selected) Stellar.Cyan else Color.Transparent)
                            .then(
                                if (selected) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, Stellar.Outline, RoundedCornerShape(20.sdp()))
                                },
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 14.sdp(), vertical = 7.sdp()),
                    )
                } else {
                    Text(
                        text = tab.title,
                        color = if (selected) Color.White else colors.textPrimary,
                        fontSize = if (compact) 12.ssp() else 13.ssp(),
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.sdp()))
                            .background(
                                if (selected) colors.accentBlue else colors.surfaceGray,
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                    )
                }
            }
        }
        Spacer(Modifier.height(if (compact) 10.sdp() else 14.sdp()))
        RelatedWordGrid(
            words = currentWords,
            selectedWord = previewWord,
            compact = compact,
            stellar = stellar,
            onWordClick = { previewWord = it },
        )
    }

    previewWord?.let { word ->
        RelatedWordPreviewDialog(
            word = word,
            saved = isWordSaved(word),
            stellar = stellar,
            onDismiss = { previewWord = null },
            onSpeak = { onSpeakWord(word) },
            onToggleStar = onToggleStar,
        )
    }
}

@Composable
private fun RelatedWordGrid(
    words: List<String>,
    selectedWord: String?,
    compact: Boolean,
    stellar: Boolean = false,
    onWordClick: (String) -> Unit,
) {
    val rows = words.chunked(2)
    val colors = hwColors()
    Column(verticalArrangement = Arrangement.spacedBy(if (stellar) 10.sdp() else if (compact) 8.sdp() else 12.sdp())) {
        rows.forEach { rowWords ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.sdp()),
            ) {
                rowWords.forEach { word ->
                    val selected = word.equals(selectedWord, ignoreCase = true)
                    val cellMod = if (stellar) {
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.sdp()))
                            .background(Stellar.SurfaceHigh.copy(alpha = 0.95f))
                            .border(1.dp, Stellar.Outline.copy(alpha = 0.55f), RoundedCornerShape(14.sdp()))
                            .clickable { onWordClick(word) }
                            .padding(horizontal = 12.sdp(), vertical = 12.sdp())
                    } else {
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.sdp()))
                            .background(if (selected) colors.accentBlue else Color.Transparent)
                            .clickable { onWordClick(word) }
                            .padding(horizontal = 4.sdp(), vertical = 4.sdp())
                    }
                    Text(
                        text = word,
                        color = when {
                            stellar -> Stellar.OnSurface
                            selected -> Color.White
                            else -> colors.textPrimary
                        },
                        fontSize = if (compact) 15.ssp() else 16.ssp(),
                        fontWeight = FontWeight.Medium,
                        modifier = cellMod,
                    )
                }
                if (rowWords.size == 1 && !stellar) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RelatedWordPreviewDialog(
    word: String,
    saved: Boolean,
    stellar: Boolean = false,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onToggleStar: (VocabEntry) -> Unit,
) {
    var loading by remember(word) { mutableStateOf(true) }
    var error by remember(word) { mutableStateOf<String?>(null) }
    var entry by remember(word) { mutableStateOf<VocabEntry?>(null) }
    var starred by remember(word, saved) { mutableStateOf(saved) }

    LaunchedEffect(word) {
        loading = true
        error = null
        entry = null
        runCatching {
            withContext(Dispatchers.IO) {
                LookupCache.get(word) ?: DictionaryClient().lookupCore(word).entry
            }
        }.onSuccess {
            entry = it
            loading = false
        }.onFailure {
            error = "暂时查不到释义"
            loading = false
        }
    }

    if (stellar) {
        StellarRelatedPreview(
            word = word,
            loading = loading,
            error = error,
            entry = entry,
            starred = starred,
            onDismiss = onDismiss,
            onSpeak = onSpeak,
            onToggleStar = {
                val current = entry ?: return@StellarRelatedPreview
                starred = !starred
                onToggleStar(current)
            },
        )
    } else {
        ClassicRelatedPreview(
            word = word,
            loading = loading,
            error = error,
            entry = entry,
            starred = starred,
            onDismiss = onDismiss,
            onSpeak = onSpeak,
            onToggleStar = {
                val current = entry ?: return@ClassicRelatedPreview
                starred = !starred
                onToggleStar(current)
            },
        )
    }
}

@Composable
private fun StellarRelatedPreview(
    word: String,
    loading: Boolean,
    error: String?,
    entry: VocabEntry?,
    starred: Boolean,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val panelShape = RoundedCornerShape(20.sdp())
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (Stellar.isLight) 0.42f else 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.sdp())
                    .shadow(
                        elevation = 24.dp,
                        shape = panelShape,
                        ambientColor = Stellar.Cyan.copy(alpha = 0.22f),
                        spotColor = Stellar.Cyan.copy(alpha = 0.18f),
                    )
                    .clip(panelShape)
                    .background(Stellar.SurfaceContainer.copy(alpha = 0.98f))
                    .border(1.dp, Stellar.Cyan.copy(alpha = 0.35f), panelShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(20.sdp())
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = word,
                        style = TextStyle(
                            color = Stellar.CyanSoft,
                            fontSize = 32.ssp(),
                            fontWeight = FontWeight.ExtraBold,
                            shadow = Shadow(
                                color = Stellar.Cyan.copy(alpha = 0.55f),
                                blurRadius = 18f,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (starred) "移出生词本" else "加入生词本",
                        tint = Stellar.Gold,
                        modifier = Modifier
                            .size(28.sdp())
                            .clickable(enabled = entry != null, onClick = onToggleStar),
                    )
                }
                Spacer(Modifier.height(12.sdp()))
                when {
                    loading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.sdp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.sdp()),
                                color = Stellar.Cyan,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    error != null -> {
                        Text(error, color = Stellar.OnSurfaceVariant, fontSize = 14.ssp())
                    }
                    entry != null -> {
                        val looked = entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.sdp()),
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (!looked.ipaUk.isNullOrBlank()) {
                                    Text(
                                        text = "UK [${looked.ipaUk}]",
                                        color = Stellar.OnSurfaceVariant,
                                        fontSize = 13.ssp(),
                                    )
                                }
                                if (!looked.ipaUs.isNullOrBlank()) {
                                    Text(
                                        text = "US [${looked.ipaUs}]",
                                        color = Stellar.OnSurfaceVariant,
                                        fontSize = 13.ssp(),
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .size(36.sdp())
                                    .clip(CircleShape)
                                    .background(Stellar.CyanBright)
                                    .clickable(onClick = onSpeak),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.VolumeUp,
                                    contentDescription = "发音",
                                    tint = Stellar.OnPrimary,
                                    modifier = Modifier.size(18.sdp()),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.sdp()))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .stellarGlass()
                                .padding(16.sdp()),
                        ) {
                            if (looked.definitions.isEmpty()) {
                                Text("暂无释义", color = Stellar.OnSurfaceVariant, fontSize = 15.ssp())
                            } else {
                                looked.definitions.forEachIndexed { index, def ->
                                    if (index > 0) Spacer(Modifier.height(12.sdp()))
                                    Row(verticalAlignment = Alignment.Top) {
                                        if (def.pos.isNotBlank()) {
                                            Text(
                                                text = if (def.pos.endsWith(".")) def.pos else "${def.pos}.",
                                                color = stellarPosColor(def.pos),
                                                fontSize = 18.ssp(),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.width(48.sdp()),
                                                style = TextStyle(
                                                    shadow = Shadow(
                                                        color = stellarPosColor(def.pos).copy(alpha = 0.45f),
                                                        blurRadius = 10f,
                                                    ),
                                                ),
                                            )
                                        }
                                        Text(
                                            text = def.meaning,
                                            color = Stellar.OnSurface,
                                            fontSize = 15.ssp(),
                                            lineHeight = 24.ssp(),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicRelatedPreview(
    word: String,
    loading: Boolean,
    error: String?,
    entry: VocabEntry?,
    starred: Boolean,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onToggleStar: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 28.sdp()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.sdp(), RoundedCornerShape(12.sdp()))
                    .clip(RoundedCornerShape(12.sdp()))
                    .background(HwColors.Background)
                    .border(0.5.dp, HwColors.Divider, RoundedCornerShape(12.sdp()))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = word,
                        color = HwColors.TextPrimary,
                        fontSize = 22.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (starred) "移出生词本" else "加入生词本",
                        tint = if (starred) hwColors().starFilled else HwColors.IconGray,
                        modifier = Modifier
                            .size(24.sdp())
                            .clickable(enabled = entry != null, onClick = onToggleStar),
                    )
                }
                Spacer(Modifier.height(8.sdp()))
                when {
                    loading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.sdp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.sdp()),
                                color = HwColors.AccentBlue,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    error != null -> {
                        Text(error, color = HwColors.TextSecondary, fontSize = 14.ssp())
                    }
                    entry != null -> {
                        val looked = entry
                        val ipa = looked.ipaUs ?: looked.ipaUk
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!ipa.isNullOrBlank()) {
                                Text(
                                    text = "/$ipa/",
                                    color = HwColors.TextSecondary,
                                    fontSize = 14.ssp(),
                                )
                                Spacer(Modifier.width(8.sdp()))
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "发音",
                                tint = HwColors.AccentBlue,
                                modifier = Modifier
                                    .size(20.sdp())
                                    .clickable(onClick = onSpeak),
                            )
                        }
                        Spacer(Modifier.height(10.sdp()))
                        Text(
                            text = looked.definitionLine.ifBlank { "暂无释义" },
                            color = HwColors.TextPrimary,
                            fontSize = 15.ssp(),
                        )
                    }
                }
            }
        }
    }
}
