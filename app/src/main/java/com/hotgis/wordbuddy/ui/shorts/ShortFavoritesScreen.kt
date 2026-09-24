package com.hotgis.wordbuddy.ui.shorts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ShortFavoritesScreen(
    authToken: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenVideo: (ShortClip) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { WordBuddyApi() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ShortClip>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var removing by remember { mutableStateOf(setOf<String>()) }

    fun reload() {
        val token = authToken
        if (token.isNullOrBlank()) {
            loading = false
            items = emptyList()
            selected = emptySet()
            onRequireLogin()
            return
        }
        scope.launch {
            loading = true
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    val all = mutableListOf<ShortClip>()
                    var page = 1
                    while (page <= 20) {
                        val batch = api.fetchShortFavorites(token, page, 60)
                        all += batch
                        if (batch.size < 60) break
                        page++
                    }
                    all
                }
            }.getOrElse { emptyList() }
            items = loaded
            selected = selected.intersect(loaded.map { it.id }.toSet())
            loading = false
        }
    }

    fun unfavoriteIds(ids: List<String>) {
        val token = authToken
        if (token.isNullOrBlank()) {
            onRequireLogin()
            return
        }
        val targets = ids.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty() || removing.isNotEmpty()) return
        removing = targets.toSet()
        scope.launch {
            val removed = mutableListOf<String>()
            var failed = 0
            coroutineScope {
                targets.chunked(4).forEach { chunk ->
                    val results = chunk.map { id ->
                        async(Dispatchers.IO) {
                            id to runCatching { api.setShortFavorite(token, id, false) }.getOrNull()
                        }
                    }.awaitAll()
                    results.forEach { (id, favorited) ->
                        if (favorited == false) removed += id else failed++
                    }
                }
            }
            items = items.filterNot { it.id in removed }
            selected = selected - removed.toSet()
            removing = emptySet()
            if (failed > 0) {
                Toast.makeText(context, "有 $failed 个取消失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(authToken) { reload() }

    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.sdp(), vertical = 10.sdp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = Stellar.OnSurface,
                    modifier = Modifier
                        .size(40.sdp())
                        .clip(RoundedCornerShape(12.sdp()))
                        .clickable(onClick = onBack)
                        .padding(10.sdp()),
                )
                Text(
                    text = "短视频收藏",
                    color = Stellar.OnSurface,
                    fontSize = 20.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.sdp()),
                )
                Spacer(Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    val allSelected = selected.size == items.size
                    Text(
                        text = if (allSelected) "取消全选" else "全选",
                        color = Stellar.Cyan,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.sdp()))
                            .clickable(enabled = removing.isEmpty()) {
                                selected = if (allSelected) emptySet() else items.map { it.id }.toSet()
                            }
                            .padding(horizontal = 8.sdp(), vertical = 8.sdp()),
                    )
                    if (selected.isNotEmpty()) {
                        Text(
                            text = if (removing.isNotEmpty()) "取消中" else "全部取消",
                            color = Stellar.Pink,
                            fontSize = 14.ssp(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.sdp()))
                                .clickable(enabled = removing.isEmpty()) { unfavoriteIds(selected.toList()) }
                                .padding(horizontal = 8.sdp(), vertical = 8.sdp()),
                        )
                    }
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Stellar.Cyan)
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (authToken.isNullOrBlank()) "登录后可查看收藏" else "还没有收藏的短视频",
                            color = Stellar.OnSurfaceVariant,
                            fontSize = 14.ssp(),
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.sdp()),
                        horizontalArrangement = Arrangement.spacedBy(12.sdp()),
                        verticalArrangement = Arrangement.spacedBy(12.sdp()),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { clip ->
                            FavoriteCard(
                                clip = clip,
                                busy = clip.id in removing,
                                selected = clip.id in selected,
                                onToggleSelect = {
                                    if (removing.isNotEmpty()) return@FavoriteCard
                                    selected = if (clip.id in selected) {
                                        selected - clip.id
                                    } else {
                                        selected + clip.id
                                    }
                                },
                                onOpen = { onOpenVideo(clip) },
                                onUnfavorite = { unfavoriteIds(listOf(clip.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    clip: ShortClip,
    busy: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onUnfavorite: () -> Unit,
) {
    var thumb by remember(clip.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(clip.id, clip.coverUrl, clip.videoUrl) {
        thumb = withContext(Dispatchers.IO) {
            loadPreviewFrame(coverUrl = clip.coverUrl, videoUrl = clip.videoUrl)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .stellarGlass()
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(Color.Black),
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = clip.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A2332), Color(0xFF0B1020)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PlayCircleOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(36.sdp()),
                    )
                }
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) "取消选择" else "选择",
                tint = if (selected) Stellar.Cyan else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.sdp())
                    .size(32.sdp())
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = !busy, onClick = onToggleSelect)
                    .padding(4.sdp()),
            )
            Icon(
                Icons.Outlined.BookmarkRemove,
                contentDescription = "取消收藏",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.sdp())
                    .size(32.sdp())
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = !busy, onClick = onUnfavorite)
                    .padding(6.sdp()),
            )
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.sdp(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.sdp()),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.sdp(), vertical = 8.sdp())) {
            Text(
                text = clip.title.ifBlank { "短视频" },
                color = Stellar.OnSurface,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.sdp()))
            Text(
                text = "@${clip.author}",
                color = Stellar.OnSurfaceVariant,
                fontSize = 11.ssp(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun loadPreviewFrame(coverUrl: String?, videoUrl: String): Bitmap? {
    val cover = coverUrl?.trim().orEmpty()
    if (cover.isNotEmpty()) {
        runCatching {
            val conn = (URL(cover).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { stream ->
                return BitmapFactory.decodeStream(stream)
            }
        }
    }
    val video = videoUrl.trim()
    if (video.isEmpty()) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(video, HashMap())
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
