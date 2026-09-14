package com.hotgis.wordbuddy.ui.shorts

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar

@Composable
fun ShortsScreen(
    modifier: Modifier = Modifier,
    onOpenWord: (String) -> Unit = {},
    onShare: () -> Unit = {},
) {
    val clips = FakeShorts.clips
    val pagerState = rememberPagerState(pageCount = { clips.size })
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ShortVideoPage(
                clip = clips[page],
                active = pagerState.settledPage == page,
                onOpenWord = onOpenWord,
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun ShortVideoPage(
    clip: ShortClip,
    active: Boolean,
    onOpenWord: (String) -> Unit,
    onShare: () -> Unit,
) {
    var liked by remember(clip.id) { mutableStateOf(false) }
    var userPaused by remember(clip.id) { mutableStateOf(false) }
    val playing = active && !userPaused

    Box(Modifier.fillMaxSize()) {
        ShortVideoPlayer(
            url = clip.videoUrl,
            playWhenReady = playing,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                )
                .clickable { userPaused = !userPaused },
        )
        if (!playing && active) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = "已暂停",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.sdp()),
            )
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.sdp(), top = 8.sdp()),
        ) {
            Text(
                text = "本地演示",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.ssp(),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.sdp()))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.sdp(), vertical = 3.sdp()),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.sdp(), end = 72.sdp(), bottom = 18.sdp()),
        ) {
            Text(
                text = "@${clip.author}",
                color = Color.White,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.sdp()))
            Text(
                text = clip.title,
                color = Color.White,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.sdp()))
            Text(
                text = clip.caption,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.ssp(),
            )
            Spacer(Modifier.height(10.sdp()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp())) {
                clip.relatedWords.forEach { word ->
                    Text(
                        text = word,
                        color = Stellar.Cyan,
                        fontSize = 12.ssp(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.sdp()))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onOpenWord(word) }
                            .padding(horizontal = 10.sdp(), vertical = 5.sdp()),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.sdp(), bottom = 28.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.sdp()),
        ) {
            ShortAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (liked) "已赞" else "点赞",
                tint = if (liked) Color(0xFFFF5A7A) else Color.White,
                onClick = { liked = !liked },
            )
            ShortAction(
                icon = Icons.Outlined.StarBorder,
                label = "查词",
                onClick = { onOpenWord(clip.title) },
            )
            ShortAction(
                icon = Icons.Outlined.Share,
                label = "分享",
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun ShortAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.sdp())
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.sdp()),
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.ssp(),
            modifier = Modifier.padding(top = 4.sdp()),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortVideoPlayer(
    url: String,
    playWhenReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val play = rememberUpdatedState(playWhenReady)
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
            prepare()
        }
    }
    LaunchedEffect(playWhenReady) {
        player.playWhenReady = playWhenReady
        if (playWhenReady && player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (play.value) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
        },
    )
}
