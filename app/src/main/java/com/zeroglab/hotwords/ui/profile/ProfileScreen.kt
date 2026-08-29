package com.zeroglab.hotwords.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroglab.hotwords.data.NotebookImportResult
import com.zeroglab.hotwords.ui.components.ProfileHeader
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.hasStellarWallpaperBackground
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    wordCount: Int,
    userName: String,
    exportFileName: String,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onExportContent: () -> String,
    onImportContent: suspend (String) -> NotebookImportResult,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = onExportContent()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取文件")
                }
                val result = onImportContent(json)
                Toast.makeText(
                    context,
                    "导入完成：新增 ${result.added} 个，更新 ${result.updated} 个",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        ProfileHeader(
            userName = userName,
            wordCount = wordCount,
            onProfileClick = null,
            onToggleTheme = onToggleTheme,
            onOpenSettings = onOpenSettings,
        )
        HorizontalDivider(color = Stellar.Outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.sdp()),
        ) {
            SectionTitle("词库")
            ActionRow(
                title = "导出词库",
                subtitle = "不含图片和发音，可备份或迁移",
                onClick = { exportLauncher.launch(exportFileName) },
            )
            ActionRow(
                title = "导入词库",
                subtitle = "合并导入，保留本机图片和发音",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            )

            SectionTitle("关于")
            Text(
                text = "查词、收藏、卡片背诵与 AI 配图，数据保存在本机。",
                color = Stellar.OnSurfaceVariant,
                fontSize = 14.ssp(),
                modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp()),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Stellar.OnSurfaceVariant,
        fontSize = 13.ssp(),
        modifier = Modifier.padding(start = 20.sdp(), top = 16.sdp(), bottom = 4.sdp()),
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.sdp(), vertical = 14.sdp()),
    ) {
        Text(title, color = Stellar.OnSurface, fontSize = 15.ssp())
        Spacer(Modifier.height(4.sdp()))
        Text(subtitle, color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
        Spacer(Modifier.height(12.sdp()))
        HorizontalDivider(color = Stellar.Outline.copy(alpha = 0.45f))
    }
}
