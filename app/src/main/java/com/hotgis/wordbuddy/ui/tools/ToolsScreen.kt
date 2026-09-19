package com.hotgis.wordbuddy.ui.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hotgis.wordbuddy.ads.RewardVideoController
import com.hotgis.wordbuddy.ads.findActivity
import com.hotgis.wordbuddy.data.DouyinDownloader
import com.hotgis.wordbuddy.data.KuaishouDownloader
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenDouyin: () -> Unit,
    onOpenKuaishou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.findActivity()?.let(RewardVideoController::preload)
    }
    Column(modifier.fillMaxSize()) {
        ToolsTopBar(title = "工具", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.sdp(), vertical = 16.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
        ) {
            ToolRow(
                icon = Icons.Outlined.FileDownload,
                title = "抖音视频下载",
                subtitle = "看完广告后保存到相册",
                onClick = onOpenDouyin,
            )
            ToolRow(
                icon = Icons.Outlined.FileDownload,
                title = "快手视频下载",
                subtitle = "看完广告后保存到相册",
                onClick = onOpenKuaishou,
            )
        }
    }
}

@Composable
fun DouyinDownloadScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShareVideoDownloadScreen(
        title = "抖音视频下载",
        emptyHint = "请先粘贴抖音分享文案",
        placeholder = "粘贴抖音分享文案",
        onBack = onBack,
        modifier = modifier,
        download = { context, share, onProgress ->
            DouyinDownloader(context).download(share, onProgress).uri
        },
    )
}

@Composable
fun KuaishouDownloadScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShareVideoDownloadScreen(
        title = "快手视频下载",
        emptyHint = "请先粘贴快手分享文案",
        placeholder = "粘贴快手分享文案",
        onBack = onBack,
        modifier = modifier,
        download = { context, share, onProgress ->
            KuaishouDownloader(context).download(share, onProgress).uri
        },
    )
}

@Composable
private fun ShareVideoDownloadScreen(
    title: String,
    emptyHint: String,
    placeholder: String,
    onBack: () -> Unit,
    download: (android.content.Context, String, (Long, Long) -> Unit) -> Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var watchingAd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var done by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(-1L) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(activity) {
        activity?.let(RewardVideoController::preload)
    }

    fun startDownload() {
        if (busy) return
        val share = text.trim()
        if (share.isEmpty()) {
            status = emptyHint
            return
        }
        val act = activity
        if (act == null) {
            status = "无法播放广告，请稍后重试"
            return
        }
        busy = true
        watchingAd = true
        status = "正在准备广告…"
        done = 0L
        total = -1L
        savedUri = null
        var rewarded = false
        fun startFileDownload() {
            watchingAd = false
            status = "正在解析链接…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val main = android.os.Handler(android.os.Looper.getMainLooper())
                        download(context, share) { downloaded, expected ->
                            main.post {
                                done = downloaded
                                total = expected
                                status = "正在下载…"
                            }
                        }
                    }
                }
                busy = false
                result.onSuccess { saved ->
                    savedUri = saved
                    status = "已保存到相册 Movies/WordBuddy"
                    Toast.makeText(context, "视频已保存", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    status = error.message?.takeIf { it.isNotBlank() } ?: "下载失败，请稍后再试"
                }
            }
        }
        RewardVideoController.preload(act)
        scope.launch {
            var waits = 0
            while (!RewardVideoController.hasReadyAd() && waits < 25) {
                delay(200)
                waits++
            }
            if (!RewardVideoController.hasReadyAd()) {
                busy = false
                watchingAd = false
                status = "广告暂未填充，请稍后再试"
                Toast.makeText(context, "广告暂未填充，请稍后再试", Toast.LENGTH_LONG).show()
                return@launch
            }
            withContext(Dispatchers.Main) {
                RewardVideoController.show(
                    act,
                    object : RewardVideoController.Callbacks {
                        override fun onShown() {
                            status = "请看完广告后开始下载"
                        }
                        override fun onRewarded() {
                            rewarded = true
                            startFileDownload()
                        }
                        override fun onClosed() {
                            if (!rewarded) {
                                busy = false
                                watchingAd = false
                                status = "需看完广告才能下载"
                                Toast.makeText(context, "需看完广告才能下载", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailed(reason: String) {
                            busy = false
                            watchingAd = false
                            status = "广告播放失败，请稍后再试"
                            Toast.makeText(context, "广告播放失败，请稍后再试", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDownload()
        } else {
            status = "需要存储权限才能保存视频"
        }
    }

    Column(modifier.fillMaxSize()) {
        ToolsTopBar(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.sdp(), vertical = 16.sdp()),
        ) {
            Text(
                text = "有的作品不能直接下载，只能分享链接。把整段分享文案贴在这里，看完一条视频广告后即可保存到相册。",
                color = Stellar.OnSurfaceVariant,
                fontSize = 14.ssp(),
            )
            Spacer(Modifier.height(14.sdp()))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.sdp()),
                placeholder = {
                    Text(placeholder, color = Stellar.OnSurfaceVariant.copy(alpha = 0.7f))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Stellar.Cyan,
                    unfocusedBorderColor = Stellar.Outline,
                    focusedTextColor = Stellar.OnSurface,
                    unfocusedTextColor = Stellar.OnSurface,
                    cursorColor = Stellar.Cyan,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val pasted = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        if (pasted.isBlank()) {
                            Toast.makeText(context, "剪贴板是空的", Toast.LENGTH_SHORT).show()
                        } else {
                            text = pasted
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("粘贴", color = Stellar.Cyan)
                }
                TextButton(
                    onClick = {
                        text = ""
                        status = ""
                        savedUri = null
                    },
                    enabled = !busy && text.isNotEmpty(),
                ) {
                    Text("清空", color = Stellar.OnSurfaceVariant)
                }
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT <= 28 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        startDownload()
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.sdp()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Stellar.Cyan,
                    contentColor = Stellar.OnPrimary,
                    disabledContainerColor = Stellar.Cyan.copy(alpha = 0.45f),
                    disabledContentColor = Stellar.OnPrimary.copy(alpha = 0.8f),
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.sdp()),
                        strokeWidth = 2.dp,
                        color = Stellar.OnPrimary,
                    )
                    Spacer(Modifier.width(8.sdp()))
                }
                Text(
                    when {
                        watchingAd -> "观看广告"
                        busy -> "下载中"
                        else -> "看广告后下载"
                    },
                    fontSize = 16.ssp(),
                )
            }
            if (busy && total > 0L && done > 0L) {
                Spacer(Modifier.height(12.sdp()))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Stellar.Cyan,
                    trackColor = Stellar.Outline.copy(alpha = 0.35f),
                )
            } else if (busy && status.startsWith("正在下载")) {
                Spacer(Modifier.height(12.sdp()))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Stellar.Cyan,
                    trackColor = Stellar.Outline.copy(alpha = 0.35f),
                )
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.sdp()))
                Text(text = status, color = Stellar.OnSurface, fontSize = 14.ssp())
            }
            if (savedUri != null) {
                Spacer(Modifier.height(8.sdp()))
                TextButton(
                    onClick = {
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(savedUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newRawUri("video", savedUri)
                        }
                        runCatching { context.startActivity(view) }
                            .onFailure {
                                Toast.makeText(context, "没有可以打开视频的应用", Toast.LENGTH_SHORT).show()
                            }
                    },
                ) {
                    Text("打开视频", color = Stellar.Cyan)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.sdp())
                .clip(RoundedCornerShape(10.sdp()))
                .background(Stellar.Cyan.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Stellar.Cyan, modifier = Modifier.size(20.sdp()))
        }
        Spacer(Modifier.width(12.sdp()))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = Stellar.OnSurface, fontSize = 16.ssp())
            Spacer(Modifier.height(2.sdp()))
            Text(text = subtitle, color = Stellar.OnSurfaceVariant, fontSize = 12.ssp())
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Stellar.OnSurfaceVariant,
            modifier = Modifier.size(18.sdp()),
        )
    }
}

@Composable
private fun ToolsTopBar(title: String, onBack: () -> Unit) {
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
            .padding(horizontal = 12.sdp()),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.sdp())
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.CyanSoft,
                modifier = Modifier.size(18.sdp()),
            )
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = Stellar.CyanSoft,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}
