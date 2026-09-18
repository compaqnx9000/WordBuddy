package com.hotgis.wordbuddy.ui.podcast

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.hotgis.wordbuddy.podcast.PlaybackUiState
import com.hotgis.wordbuddy.podcast.PodcastCatalog
import com.hotgis.wordbuddy.podcast.PodcastEpisode
import com.hotgis.wordbuddy.podcast.PodcastPlayerBridge
import com.hotgis.wordbuddy.podcast.PodcastPrefetcher
import com.hotgis.wordbuddy.podcast.PodcastRssFetcher
import com.hotgis.wordbuddy.podcast.PodcastShow
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlin.math.min

private val ShelfWood = Color(0xFF2A1F18)
private val ShelfWoodLight = Color(0xFF3A2A20)
private val VinylGroove = Color(0xFF0A0A0C)

@OptIn(UnstableApi::class)
@Composable
fun PodcastScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playback by PodcastPlayerBridge.playback.collectAsStateWithLifecycle()
    var selectedShow by remember { mutableStateOf<PodcastShow?>(null) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* media notification is best-effort */ }

    DisposableEffect(Unit) {
        onDispose { PodcastPrefetcher.stop() }
    }

    LaunchedEffect(Unit) {
        PodcastPlayerBridge.ensureStarted(context)
        PodcastPrefetcher.startIdleWarmup(context)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(selectedShow?.id) {
        val show = selectedShow ?: return@LaunchedEffect
        loadingEpisodes = true
        episodeError = null
        val loaded = runCatching { PodcastRssFetcher.loadEpisodes(show) }
            .getOrElse { emptyList() }
        episodes = loaded
        loadingEpisodes = false
        if (loaded.isEmpty()) {
            episodeError = "暂时无法获取节目，请检查网络后重试"
        } else {
            PodcastPrefetcher.prefetchShow(context, show, loaded)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PodcastTopBar()
            Text(
                text = "英文电台直播 + 播客唱片，沉浸听力",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 4.sdp()),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.sdp()),
                contentPadding = PaddingValues(16.sdp()),
                verticalArrangement = Arrangement.spacedBy(18.sdp()),
                horizontalArrangement = Arrangement.spacedBy(14.sdp()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(PodcastCatalog.shows, key = { it.id }) { show ->
                    VinylShelfItem(
                        show = show,
                        selected = selectedShow?.id == show.id || playback.showId == show.id,
                        spinning = playback.showId == show.id && playback.isPlaying,
                        onClick = { selectedShow = show },
                    )
                }
            }
        }

        selectedShow?.let { show ->
            VinylPlayerSheet(
                show = show,
                episodes = episodes,
                loading = loadingEpisodes,
                error = episodeError,
                playback = playback,
                onDismiss = { selectedShow = null },
                onPlayEpisode = { episode ->
                    PodcastPlayerBridge.play(context, show, episode, episodes.ifEmpty { listOf(episode) })
                },
                onToggle = { PodcastPlayerBridge.togglePlayPause() },
                onSeek = { PodcastPlayerBridge.seekTo(it) },
                onNext = { PodcastPlayerBridge.next() },
                onPrevious = { PodcastPlayerBridge.previous() },
                onSleep = { PodcastPlayerBridge.setSleepMinutes(it) },
            )
        }
    }
}

@Composable
private fun PodcastTopBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 20.sdp()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "播客唱片架",
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VinylShelfItem(
    show: PodcastShow,
    selected: Boolean,
    spinning: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.sdp()))
            .background(ShelfWood.copy(alpha = 0.55f))
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) Stellar.Cyan else Color.Transparent,
                shape = RoundedCornerShape(16.sdp()),
            )
            .clickable(onClick = onClick)
            .padding(12.sdp()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(ShelfWoodLight),
            contentAlignment = Alignment.Center,
        ) {
            VinylDisc(
                vinylColor = show.vinylColor,
                labelColor = show.labelColor,
                labelText = show.title.take(10),
                spinning = spinning,
                modifier = Modifier.fillMaxSize(0.92f),
            )
        }
        Spacer(Modifier.height(10.sdp()))
        Text(
            text = show.title,
            color = Stellar.OnSurface,
            fontSize = 13.ssp(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = show.host,
            color = Stellar.OnSurfaceVariant,
            fontSize = 11.ssp(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VinylDisc(
    vinylColor: Color,
    labelColor: Color,
    labelText: String,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    showTonearm: Boolean = false,
    tonearmDown: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
        ),
        label = "spin",
    )
    val rotation = if (spinning) angle else 0f

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val sizePx = min(maxWidth.value, maxHeight.value)
        Canvas(
            Modifier
                .size(sizePx.dp)
                .rotate(rotation),
        ) {
            val r = this.size.minDimension / 2f
            val c = this.size.center
            drawCircle(color = vinylColor, radius = r)
            // Grooves
            for (i in 1..14) {
                val grooveR = r * (0.28f + i * 0.045f)
                drawCircle(
                    color = VinylGroove.copy(alpha = 0.35f),
                    radius = grooveR,
                    center = c,
                    style = Stroke(width = 1.2f),
                )
            }
            // Outer rim shine
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.08f)),
                    center = Offset(c.x - r * 0.25f, c.y - r * 0.25f),
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            // Label
            val labelR = r * 0.26f
            drawCircle(color = labelColor, radius = labelR, center = c)
            drawCircle(color = Color.Black, radius = r * 0.04f, center = c)
        }
        Text(
            text = labelText,
            color = Color.White,
            fontSize = 11.ssp(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width((sizePx * 0.38f).dp)
                .rotate(rotation),
        )
        if (showTonearm) {
            TonearmOverlay(
                lowered = tonearmDown,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TonearmOverlay(
    lowered: Boolean,
    modifier: Modifier = Modifier,
) {
    val armAngle by animateFloatAsState(
        targetValue = if (lowered) 28f else -18f,
        animationSpec = tween(durationMillis = 700, easing = LinearEasing),
        label = "tonearm",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w * 0.86f, h * 0.14f)
        rotate(degrees = armAngle, pivot = pivot) {
            val path = Path().apply {
                moveTo(pivot.x, pivot.y)
                lineTo(w * 0.55f, h * 0.52f)
            }
            drawCircle(color = Color(0xFFC0C4CC), radius = w * 0.035f, center = pivot)
            drawPath(
                path = path,
                color = Color(0xFFB8BCC4),
                style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
            )
            val tip = Offset(w * 0.55f, h * 0.52f)
            drawCircle(color = Color(0xFFE8EAED), radius = w * 0.028f, center = tip)
            drawLine(
                color = Color(0xFFFFC107),
                start = tip,
                end = Offset(tip.x - w * 0.04f, tip.y + h * 0.03f),
                strokeWidth = w * 0.01f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun VinylPlayerSheet(
    show: PodcastShow,
    episodes: List<PodcastEpisode>,
    loading: Boolean,
    error: String?,
    playback: PlaybackUiState,
    onDismiss: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleep: (Int) -> Unit,
) {
    val playingThisShow = playback.showId == show.id
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.sdp(), topEnd = 24.sdp()))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1410), Color(0xFF0E0C0B)),
                    ),
                )
                .clickable(enabled = false) {}
                .padding(horizontal = 18.sdp(), vertical = 14.sdp())
                .padding(bottom = 12.sdp()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = show.title,
                        color = Color.White,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = show.blurb,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.ssp(),
                        maxLines = 2,
                    )
                }
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(28.sdp())
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(Modifier.height(12.sdp()))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f),
                contentAlignment = Alignment.Center,
            ) {
                VinylDisc(
                    vinylColor = show.vinylColor,
                    labelColor = show.labelColor,
                    labelText = show.title.take(12),
                    spinning = playingThisShow && playback.isPlaying,
                    showTonearm = true,
                    tonearmDown = playingThisShow && playback.isPlaying,
                    modifier = Modifier.fillMaxWidth(0.78f),
                )
            }

            if (playingThisShow && playback.title.isNotBlank()) {
                Text(
                    text = playback.title,
                    color = Color.White,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlaybackProgress(
                    playback = playback,
                    onSeek = onSeek,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一集",
                        tint = Color.White,
                        modifier = Modifier
                            .size(34.sdp())
                            .clickable(onClick = onPrevious),
                    )
                    Box(
                        Modifier
                            .size(56.sdp())
                            .clip(CircleShape)
                            .background(Stellar.Cyan)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playback.buffering && !playback.isPlaying) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(26.sdp()),
                            )
                        } else {
                            Icon(
                                if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(32.sdp()),
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一集",
                        tint = Color.White,
                        modifier = Modifier
                            .size(34.sdp())
                            .clickable(onClick = onNext),
                    )
                }
                Spacer(Modifier.height(8.sdp()))
                SleepTimerRow(
                    selectedMinutes = playback.sleepMinutes,
                    remainingMs = playback.sleepRemainingMs,
                    onSelect = onSleep,
                )
            }

            Spacer(Modifier.height(10.sdp()))
            Text(
                text = "曲目",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.ssp(),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.sdp()))
            when {
                loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.sdp()),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Stellar.Cyan, strokeWidth = 2.dp)
                    }
                }
                error != null && episodes.isEmpty() -> {
                    Text(
                        text = error,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.ssp(),
                        modifier = Modifier.padding(vertical = 16.sdp()),
                    )
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(180.sdp())
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.sdp()),
                    ) {
                        episodes.forEach { ep ->
                            val active = playback.episodeId == ep.id
                            val rowBusy = active && playback.buffering && !playback.isPlaying
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.sdp()))
                                    .background(
                                        if (active) Stellar.Cyan.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.06f),
                                    )
                                    .clickable { onPlayEpisode(ep) }
                                    .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (rowBusy) {
                                    CircularProgressIndicator(
                                        color = Stellar.Cyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.sdp()),
                                    )
                                } else {
                                    Icon(
                                        if (active && playback.isPlaying) Icons.Filled.Pause
                                        else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = if (active) Stellar.Cyan else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.sdp()),
                                    )
                                }
                                Spacer(Modifier.width(10.sdp()))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = ep.title,
                                        color = Color.White,
                                        fontSize = 13.ssp(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (ep.durationLabel.isNotBlank()) {
                                        Text(
                                            text = ep.durationLabel,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.ssp(),
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
private fun PlaybackProgress(
    playback: PlaybackUiState,
    onSeek: (Long) -> Unit,
) {
    val duration = playback.durationMs.coerceAtLeast(0L)
    val isLive = playback.isLive || duration <= 0L
    val pos = playback.positionMs.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val value = if (dragging) dragValue else if (duration > 0) pos.toFloat() / duration else 0f

    Column(Modifier.fillMaxWidth().padding(vertical = 4.sdp())) {
        if (isLive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.sdp()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "● LIVE",
                    color = Color(0xFFFF5252),
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "已收听 ${formatMs(pos)}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.ssp(),
                )
            }
        } else {
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    if (duration > 0) onSeek((dragValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Stellar.Cyan,
                    activeTrackColor = Stellar.Cyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatMs(if (dragging && duration > 0) (dragValue * duration).toLong() else pos),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.ssp(),
                )
                Text(formatMs(duration), color = Color.White.copy(alpha = 0.55f), fontSize = 11.ssp())
            }
        }
    }
}

@Composable
private fun SleepTimerRow(
    selectedMinutes: Int,
    remainingMs: Long,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(0 to "关", 15 to "15分", 30 to "30分", 45 to "45分", 60 to "60分")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = Stellar.CyanSoft, modifier = Modifier.size(16.sdp()))
            Spacer(Modifier.width(6.sdp()))
            Text(
                text = if (remainingMs > 0) {
                    "定时关闭 · 剩余 ${formatMs(remainingMs)}"
                } else {
                    "定时关闭"
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.ssp(),
            )
        }
        Spacer(Modifier.height(8.sdp()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp())) {
            options.forEach { (minutes, label) ->
                val selected = selectedMinutes == minutes || (minutes == 0 && selectedMinutes == 0 && remainingMs <= 0)
                Text(
                    text = label,
                    color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.sdp()))
                        .background(if (selected) Stellar.Cyan else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(minutes) }
                        .padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
