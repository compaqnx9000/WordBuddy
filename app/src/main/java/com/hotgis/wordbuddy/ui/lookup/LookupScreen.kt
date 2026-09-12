package com.hotgis.wordbuddy.ui.lookup

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.hotgis.wordbuddy.data.Accent
import com.hotgis.wordbuddy.data.Definition
import com.hotgis.wordbuddy.data.ExampleSentence
import com.hotgis.wordbuddy.data.VocabEntry
import com.hotgis.wordbuddy.ui.VocabUiState
import com.hotgis.wordbuddy.ui.components.EditMeaningDialog
import com.hotgis.wordbuddy.ui.components.EntryRelatedBlocks
import com.hotgis.wordbuddy.ui.components.ImageSourceDialog
import com.hotgis.wordbuddy.ui.components.MnemonicImage
import com.hotgis.wordbuddy.ui.components.highlightHeadword
import com.hotgis.wordbuddy.ui.components.rememberImagePickerLauncher
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp

@Composable
fun LookupScreen(
    ui: VocabUiState,
    wordCount: Int,
    userName: String,
    onToggleTheme: () -> Unit,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleStar: () -> Unit,
    onSpeak: (VocabEntry) -> Unit,
    onSpeakText: (String) -> Unit,
    onChangeAccent: (Accent) -> Unit,
    onToggleRelatedStar: (VocabEntry) -> Unit,
    isRelatedWordSaved: (String) -> Boolean,
    onPickLookupImage: (Uri) -> Unit,
    onGenerateAiForLookup: (String) -> Unit,
    onClearImageError: () -> Unit,
    onUpdateDefinitions: (Long, List<Definition>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showImageDialog by remember { mutableStateOf(false) }
    var showMeaningDialog by remember { mutableStateOf(false) }
    val launchGallery = rememberImagePickerLauncher(
        onImagePicked = { uri ->
            showImageDialog = false
            onPickLookupImage(uri)
        },
    )

    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        if (!hasStellarWallpaperBackground()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.sdp(), y = (-20).sdp())
                    .size(180.sdp())
                    .clip(CircleShape)
                    .background(Stellar.Pink.copy(alpha = 0.10f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-30).sdp(), y = 40.sdp())
                    .size(220.sdp())
                    .clip(CircleShape)
                    .background(Stellar.CyanBright.copy(alpha = 0.10f)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            HomeProfileHeader()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.sdp()),
                verticalArrangement = Arrangement.spacedBy(22.sdp()),
            ) {
                LookupInputBox(
                    value = ui.lookupQuery,
                    onValueChange = onQuery,
                    onSubmit = onSubmit,
                )
                LookupContent(
                    ui = ui,
                    accent = ui.settings.accent,
                    onToggleStar = onToggleStar,
                    onSpeak = onSpeak,
                    onSpeakText = onSpeakText,
                    onChangeAccent = onChangeAccent,
                    onToggleRelatedStar = onToggleRelatedStar,
                    isRelatedWordSaved = isRelatedWordSaved,
                    onOpenImageChooser = { showImageDialog = true },
                    onEditMeaning = { showMeaningDialog = true },
                    imageBusy = ui.imageBusy,
                )
                Spacer(Modifier.height(8.sdp()))
            }
        }
    }

    if (showImageDialog) {
        val result = ui.lookupResult
        ImageSourceDialog(
            definitions = result?.entry?.definitions.orEmpty(),
            busy = ui.imageBusy,
            error = ui.imageError,
            stellar = true,
            onPickGallery = launchGallery,
            onGenerateAi = { meaning ->
                onGenerateAiForLookup(meaning)
            },
            onDismiss = {
                showImageDialog = false
                onClearImageError()
            },
        )
    }

    val lookupEntry = ui.lookupResult?.entry
    if (showMeaningDialog && lookupEntry != null) {
        EditMeaningDialog(
            word = lookupEntry.text,
            definitions = lookupEntry.definitions,
            stellar = true,
            onDismiss = { showMeaningDialog = false },
            onSave = { definitions ->
                onUpdateDefinitions(lookupEntry.id, definitions)
                showMeaningDialog = false
            },
        )
    }
}

@Composable
private fun LookupContent(
    ui: VocabUiState,
    accent: Accent,
    onToggleStar: () -> Unit,
    onSpeak: (VocabEntry) -> Unit,
    onSpeakText: (String) -> Unit,
    onChangeAccent: (Accent) -> Unit,
    onToggleRelatedStar: (VocabEntry) -> Unit,
    isRelatedWordSaved: (String) -> Boolean,
    onOpenImageChooser: () -> Unit,
    onEditMeaning: () -> Unit,
    imageBusy: Boolean,
) {
    when {
        ui.lookupLoading -> {
            Box(Modifier.fillMaxWidth().padding(top = 24.sdp()), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.sdp()),
                    color = Stellar.Cyan,
                    strokeWidth = 2.dp,
                )
            }
        }
        ui.lookupError != null -> {
            Text(ui.lookupError, color = Stellar.OnSurfaceVariant, fontSize = 15.ssp())
        }
        ui.lookupResult != null -> {
            val result = ui.lookupResult
            LookupResultBlock(
                entry = result.entry,
                saved = result.saved,
                relatedLoading = ui.lookupRelatedLoading,
                accent = accent,
                imageBusy = imageBusy,
                onToggleStar = onToggleStar,
                onSpeak = { onSpeak(result.entry) },
                onSpeakText = onSpeakText,
                onChangeAccent = onChangeAccent,
                onToggleRelatedStar = onToggleRelatedStar,
                isRelatedWordSaved = isRelatedWordSaved,
                onOpenImageChooser = onOpenImageChooser,
                onEditMeaning = onEditMeaning,
            )
        }
        else -> {
            Text(
                text = "搜索单词，查看音标、释义和例句。",
                color = Stellar.OnSurfaceVariant,
                fontSize = 15.ssp(),
            )
        }
    }
}

@Composable
private fun LookupResultBlock(
    entry: VocabEntry,
    saved: Boolean,
    relatedLoading: Boolean,
    accent: Accent,
    imageBusy: Boolean,
    onToggleStar: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakText: (String) -> Unit,
    onChangeAccent: (Accent) -> Unit,
    onToggleRelatedStar: (VocabEntry) -> Unit,
    isRelatedWordSaved: (String) -> Boolean,
    onOpenImageChooser: () -> Unit,
    onEditMeaning: () -> Unit,
) {
    WordHeader(
        entry = entry,
        saved = saved,
        accent = accent,
        onToggleStar = onToggleStar,
        onSpeak = onSpeak,
        onChangeAccent = onChangeAccent,
    )
    DefinitionCard(
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
    } else if (relatedLoading) {
        Text(
            text = "关联词加载中…",
            color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
            fontSize = 13.ssp(),
            modifier = Modifier.padding(top = 8.sdp()),
        )
    }
    if (entry.examples.isNotEmpty()) {
        ExampleCard(
            word = entry.text,
            example = entry.examples.first(),
            onListen = { onSpeakText(entry.examples.first().english) },
        )
    }
    if (entry.hasImage) {
        MnemonicImage(
            imageBlob = entry.imageBlob,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.sdp())
                .clip(RoundedCornerShape(16.sdp())),
        )
    }
    AiMnemonicButton(
        busy = imageBusy,
        hasImage = entry.hasImage,
        enabled = !imageBusy,
        onClick = onOpenImageChooser,
    )
}

@Composable
private fun WordHeader(
    entry: VocabEntry,
    saved: Boolean,
    accent: Accent,
    onToggleStar: () -> Unit,
    onSpeak: () -> Unit,
    onChangeAccent: (Accent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp())) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.text,
                    style = TextStyle(
                        color = Stellar.CyanSoft,
                        fontSize = 40.ssp(),
                        fontWeight = FontWeight.ExtraBold,
                        shadow = Shadow(
                            color = Stellar.Cyan.copy(alpha = 0.55f),
                            blurRadius = 22f,
                        ),
                    ),
                )
                Spacer(Modifier.height(6.sdp()))
                Row(horizontalArrangement = Arrangement.spacedBy(16.sdp())) {
                    if (!entry.ipaUk.isNullOrBlank()) {
                        Text(
                            text = "UK [${entry.ipaUk}]",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 14.ssp(),
                        )
                    }
                    if (!entry.ipaUs.isNullOrBlank()) {
                        Text(
                            text = "US [${entry.ipaUs}]",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 14.ssp(),
                        )
                    }
                }
            }
            Icon(
                if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (saved) "移出生词本" else "加入生词本",
                tint = Stellar.Gold,
                modifier = Modifier
                    .size(32.sdp())
                    .clickable(onClick = onToggleStar),
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
                    .clickable(onClick = onSpeak),
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
                AccentChip("US", selected = accent == Accent.US) { onChangeAccent(Accent.US) }
                AccentChip("UK", selected = accent == Accent.UK) { onChangeAccent(Accent.UK) }
            }
        }
    }
}

@Composable
private fun AccentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
        fontSize = 11.ssp(),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.em,
        modifier = Modifier
            .clip(RoundedCornerShape(16.sdp()))
            .background(if (selected) Stellar.Cyan else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.sdp(), vertical = 6.sdp()),
    )
}

@Composable
private fun DefinitionCard(
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
private fun ExampleCard(
    word: String,
    example: ExampleSentence,
    onListen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.sdp())) {
        Text(
            text = "例句",
            color = Stellar.OnSurfaceVariant,
            fontSize = 11.ssp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .stellarGlass()
                .padding(18.sdp()),
        ) {
            Text(
                text = highlightHeadword(example.english, word, Stellar.CyanSoft),
                color = Stellar.OnSurface,
                fontSize = 16.ssp(),
                lineHeight = 26.ssp(),
            )
            Spacer(Modifier.height(10.sdp()))
            Text(
                text = example.chinese,
                color = Stellar.OnSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 14.ssp(),
                lineHeight = 22.ssp(),
            )
            Spacer(Modifier.height(14.sdp()))
            Row(
                Modifier.clickable(onClick = onListen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Stellar.Cyan,
                    modifier = Modifier.size(18.sdp()),
                )
                Spacer(Modifier.width(6.sdp()))
                Text(
                    text = "朗读",
                    color = Stellar.Cyan,
                    fontSize = 11.ssp(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AiMnemonicButton(
    busy: Boolean,
    hasImage: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        busy -> "生成中…"
        hasImage -> "AI 助记配图"
        else -> "AI 助记配图"
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
    val dashColor = Stellar.Cyan.copy(alpha = 0.55f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp()))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dash),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                )
            }
            .background(Stellar.Glass)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.sdp()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Stellar.Pink,
            modifier = Modifier.size(20.sdp()),
        )
        Spacer(Modifier.width(10.sdp()))
        Text(
            text = label,
            color = Stellar.OnSurface,
            fontSize = 18.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}
