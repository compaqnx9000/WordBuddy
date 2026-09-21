package com.hotgis.wordbuddy.ui.shorts

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hotgis.wordbuddy.ads.DrawFeedController
import com.hotgis.wordbuddy.ads.findActivity
import com.hotgis.wordbuddy.data.HotWordsApi
import com.hotgis.wordbuddy.media.ShortsVideoCache
import com.hotgis.wordbuddy.podcast.AudibleFocus
import com.hotgis.wordbuddy.podcast.AudibleOwner
import com.hotgis.wordbuddy.podcast.PodcastPlayerBridge
import com.hotgis.wordbuddy.ui.design.DesignSpec
import com.hotgis.wordbuddy.ui.design.LocalDesignScale
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private sealed interface ShortFeedItem {
    val key: String
    data class Video(val clip: ShortClip, override val key: String) : ShortFeedItem
    data class Ad(override val key: String) : ShortFeedItem
}

@Composable
fun ShortsScreen(
    modifier: Modifier = Modifier,
    authToken: String? = null,
    metaVisibleDefault: Boolean = true,
    onOpenWord: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onRequireLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val api = remember { HotWordsApi() }
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf<List<ShortClip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var feed by remember { mutableStateOf<List<ShortFeedItem>>(emptyList()) }
    val pagerState = rememberPagerState(pageCount = { feed.size.coerceAtLeast(1) })
    val readyAdKeys by DrawFeedController.readyKeys.collectAsState()
    val tokenState = rememberUpdatedState(authToken)

    fun updateClipFavorite(videoId: String, favorited: Boolean) {
        clips = clips.map { if (it.id == videoId) it.copy(favorited = favorited) else it }
        feed = feed.map { item ->
            if (item is ShortFeedItem.Video && item.clip.id == videoId) {
                item.copy(clip = item.clip.copy(favorited = favorited))
            } else {
                item
            }
        }
    }

    suspend fun loadMoreClips(exclude: List<String> = emptyList()): List<ShortClip> {
        return try {
            api.fetchShortsFeed(token = tokenState.value, limit = 20, excludeIds = exclude)
        } catch (_: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(authToken) {
        loading = true
        val remote = loadMoreClips()
        clips = if (remote.isNotEmpty()) remote else FakeShorts.clips
        feed = initialVideoFeed(clips)
        loading = false
    }

    LaunchedEffect(activity) {
        activity?.let { act ->
            DrawFeedController.start(act)
        }
    }

    LaunchedEffect(pagerState.settledPage, feed.size, readyAdKeys, clips) {
        if (clips.isEmpty()) return@LaunchedEffect
        var next = appendClipsIfNeeded(feed, pagerState.settledPage, clips)
        if (pagerState.settledPage >= feed.lastIndex - 2) {
            val exclude = clips.map { it.id }
            val more = loadMoreClips(exclude)
            if (more.isNotEmpty()) {
                val merged = (clips + more).distinctBy { it.id }
                clips = merged
                next = appendClipsIfNeeded(next, pagerState.settledPage, more)
            }
        }
        next = insertUpcomingAds(
            current = next,
            settledPage = pagerState.settledPage,
            readyKeys = readyAdKeys,
            clips = clips,
        )
        if (next !== feed) feed = next
    }

    LaunchedEffect(pagerState.settledPage, feed, readyAdKeys, activity) {
        val unusedAds = readyAdKeys.size - feed.count { it is ShortFeedItem.Ad }
        val adsAhead = feed.drop(pagerState.settledPage + 1).count { it is ShortFeedItem.Ad }
        if (unusedAds < 2 || adsAhead < 1) {
            activity?.let(DrawFeedController::loadMore)
        }
        Log.i(
            "ShortsFeed",
            "page=${pagerState.settledPage}/${feed.size} " +
                "videos=${feed.count { it is ShortFeedItem.Video }} " +
                "draw=${feed.count { it is ShortFeedItem.Ad }}",
        )
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val fullScale = (maxWidth.value / DesignSpec.WIDTH_DP).coerceIn(0.72f, 1.6f)
        CompositionLocalProvider(LocalDesignScale provides fullScale) {
            if (feed.isEmpty()) {
                Text(
                    text = if (loading) "加载短视频…" else "暂无短视频",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.28f,
                    ),
                    key = { page -> feed.getOrNull(page)?.key ?: page },
                ) { page ->
                    when (val item = feed[page]) {
                        is ShortFeedItem.Video -> ShortVideoPage(
                            clip = item.clip,
                            active = pagerState.settledPage == page,
                            metaVisibleDefault = metaVisibleDefault,
                            onOpenWord = onOpenWord,
                            onShare = onShare,
                            onToggleFavorite = {
                                val token = tokenState.value
                                if (token.isNullOrBlank()) {
                                    onRequireLogin()
                                    return@ShortVideoPage
                                }
                                val next = !item.clip.favorited
                                updateClipFavorite(item.clip.id, next)
                                scope.launch {
                                    val ok = runCatching {
                                        withContext(Dispatchers.IO) {
                                            api.setShortFavorite(token, item.clip.id, next)
                                        }
                                    }.getOrNull()
                                    if (ok == null) {
                                        updateClipFavorite(item.clip.id, !next)
                                        Toast.makeText(context, "收藏失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                    } else if (ok != next) {
                                        updateClipFavorite(item.clip.id, ok)
                                    }
                                }
                            },
                            onWatch = { watchMs, completed ->
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            api.reportShortWatch(
                                                token = tokenState.value,
                                                videoId = item.clip.id,
                                                watchMs = watchMs,
                                                completed = completed,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                        is ShortFeedItem.Ad -> DrawAdPage(
                            activity = activity,
                            adKey = item.key,
                            active = pagerState.settledPage == page,
                        )
                    }
                }
            }
        }
    }
}

private fun initialVideoFeed(clips: List<ShortClip>): List<ShortFeedItem> =
    clips.mapIndexed { index, clip -> ShortFeedItem.Video(clip, "v-0-$index-${clip.id}") }

private fun appendClipsIfNeeded(
    current: List<ShortFeedItem>,
    settledPage: Int,
    clips: List<ShortClip>,
): List<ShortFeedItem> {
    if (clips.isEmpty() || current.isEmpty()) return current
    if (current.size >= MAX_FEED_SIZE) return current
    if (settledPage < current.lastIndex - 1) return current
    val stamp = current.size
    return current + clips.mapIndexed { index, clip ->
        ShortFeedItem.Video(clip, "v-$stamp-$index-${clip.id}")
    }
}

private fun insertUpcomingAds(
    current: List<ShortFeedItem>,
    settledPage: Int,
    readyKeys: List<String>,
    clips: List<ShortClip>,
): List<ShortFeedItem> {
    if (clips.isEmpty()) return current
    val used = current.mapNotNull { item ->
        (item as? ShortFeedItem.Ad)?.key
    }.toSet()
    val pending = readyKeys.filter { it !in used }.take(1)
    if (pending.isEmpty()) return current
    val adsAhead = current.drop((settledPage + 1).coerceAtLeast(0)).count { it is ShortFeedItem.Ad }
    if (adsAhead >= 1) return current

    val result = current.toMutableList()
    var index = (settledPage + 1).coerceAtLeast(1)
    val firstAdInFeed = result.none { it is ShortFeedItem.Ad }
    pending.forEach { key ->
        var videosToSkip = if (firstAdInFeed) 2 else Random.nextInt(2, 4)
        while (videosToSkip > 0) {
            if (result.size >= MAX_FEED_SIZE) return result
            if (index >= result.size) {
                val round = result.size
                clips.forEachIndexed { clipIndex, clip ->
                    result += ShortFeedItem.Video(clip, "v-$round-$clipIndex-${clip.id}")
                }
            }
            when (result.getOrNull(index)) {
                is ShortFeedItem.Video -> {
                    videosToSkip--
                    index++
                }
                is ShortFeedItem.Ad -> index++
                null -> break
            }
        }
        if (result.size >= MAX_FEED_SIZE) return result
        if (index > result.size) index = result.size
        result.add(index, ShortFeedItem.Ad(key))
        Log.i("DrawFeedAd", "inserted ad key=$key at index=$index feedSize=${result.size}")
        index++
    }
    return result
}

private const val MAX_FEED_SIZE = 80

@Composable
private fun DrawAdPage(
    activity: Activity?,
    adKey: String,
    active: Boolean,
) {
    var failed by remember(adKey) { mutableStateOf(false) }
    var attached by remember(adKey) { mutableStateOf(false) }
    var host by remember { mutableStateOf<FrameLayout?>(null) }

    LaunchedEffect(adKey, active) {
        DrawFeedController.setPageActive(adKey, active)
    }
    LaunchedEffect(adKey, active, host, activity) {
        val container = host
        if (!active || activity == null || container == null) return@LaunchedEffect
        DrawFeedController.attachTo(activity, adKey, container) { ok ->
            attached = ok
            failed = !ok
        }
    }
    DisposableEffect(adKey) {
        onDispose { DrawFeedController.setPageActive(adKey, false) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (activity != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        clipChildren = true
                        clipToPadding = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        post { host = this }
                    }
                },
                update = { view ->
                    if (host !== view) host = view
                },
            )
        }
        if (!attached && !failed) {
            Text(
                text = "广告加载中…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.ssp(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (failed) {
            Text(
                text = "本条暂无法播放，继续上滑",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.ssp(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ShortVideoPage(
    clip: ShortClip,
    active: Boolean,
    metaVisibleDefault: Boolean,
    onOpenWord: (String) -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onWatch: (watchMs: Long, completed: Boolean) -> Unit = { _, _ -> },
) {
    var userPaused by remember(clip.id) { mutableStateOf(false) }
    var metaVisible by remember(clip.id, metaVisibleDefault) { mutableStateOf(metaVisibleDefault) }
    val audibleOwner by AudibleFocus.owner.collectAsState()
    val playing = active && !userPaused && audibleOwner != AudibleOwner.Podcast
    val watchStartedAt = remember(clip.id, active) { System.currentTimeMillis() }
    val onWatchState = rememberUpdatedState(onWatch)

    LaunchedEffect(clip.id, active) {
        if (active) {
            AudibleFocus.claimShorts()
            PodcastPlayerBridge.pause()
        }
    }

    DisposableEffect(clip.id, active) {
        onDispose {
            if (active) {
                val ms = (System.currentTimeMillis() - watchStartedAt).coerceAtLeast(0L)
                onWatchState.value(ms, ms >= 12_000L)
            }
        }
    }

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
                        1f to Color.Black.copy(alpha = if (metaVisible) 0.72f else 0.28f),
                    ),
                )
                .clickable {
                    if (AudibleFocus.owner.value == AudibleOwner.Podcast) {
                        userPaused = false
                        AudibleFocus.claimShorts()
                        PodcastPlayerBridge.pause()
                    } else {
                        userPaused = !userPaused
                    }
                },
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
        if (metaVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.sdp(), end = 72.sdp(), bottom = 18.sdp())
                    .clickable { metaVisible = false },
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.sdp()))
                Text(
                    text = clip.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.ssp(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.sdp(), bottom = 28.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.sdp()),
        ) {
            ShortAction(
                icon = if (clip.favorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (clip.favorited) "已收藏" else "收藏",
                tint = if (clip.favorited) Color(0xFFFFD54F) else Color.White,
                onClick = onToggleFavorite,
            )
            ShortAction(
                icon = Icons.Outlined.Share,
                label = "分享",
                onClick = onShare,
            )
            ShortAction(
                icon = if (metaVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                label = if (metaVisible) "藏文案" else "文案",
                onClick = { metaVisible = !metaVisible },
            )
        }
    }
}

@Composable
fun FavoriteClipPlayer(
    clip: ShortClip,
    onBack: () -> Unit,
    authToken: String?,
    onRequireLogin: () -> Unit,
    metaVisibleDefault: Boolean = true,
    onOpenWord: (String) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { HotWordsApi() }
    val scope = rememberCoroutineScope()
    var current by remember(clip.id) { mutableStateOf(clip) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ShortVideoPage(
            clip = current,
            active = true,
            metaVisibleDefault = metaVisibleDefault,
            onOpenWord = onOpenWord,
            onShare = onShare,
            onToggleFavorite = {
                val token = authToken
                if (token.isNullOrBlank()) {
                    onRequireLogin()
                } else {
                    val next = !current.favorited
                    current = current.copy(favorited = next)
                    scope.launch {
                        val ok = runCatching {
                            api.setShortFavorite(token, current.id, next)
                        }.getOrNull()
                        if (ok == null) {
                            current = current.copy(favorited = !next)
                            Toast.makeText(context, "收藏失败，请稍后重试", Toast.LENGTH_SHORT).show()
                        } else if (ok != next) {
                            current = current.copy(favorited = ok)
                        }
                    }
                }
            },
            onWatch = { watchMs, completed ->
                scope.launch {
                    runCatching {
                        api.reportShortWatch(
                            token = authToken,
                            videoId = current.id,
                            watchMs = watchMs,
                            completed = completed,
                        )
                    }
                }
            },
        )
        Icon(
            Icons.Outlined.ArrowBackIosNew,
            contentDescription = "返回收藏",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.sdp())
                .size(40.sdp())
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onBack)
                .padding(10.sdp()),
        )
    }
}

@Composable
private fun ShortAction(
    icon: ImageVector,
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
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ShortsVideoCache.dataSourceFactory(context)),
            )
            .build()
            .apply {
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
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
            view.useController = false
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view.post {
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                (view.parent as? ViewGroup)?.let { parent ->
                    if (parent.width > 0 && parent.height > 0) {
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, parent.width, parent.height)
                    }
                }
            }
        },
    )
}
