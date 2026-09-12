package com.hotgis.wordbuddy.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hotgis.wordbuddy.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUpdater {
    suspend fun fetchLatest(api: HotWordsApi = HotWordsApi()): AppUpdateInfo =
        api.checkAppUpdate()

    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val raw = info.apkPath.ifBlank { "/app/WordBuddy-release.apk" }
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> base + (if (raw.startsWith("/")) raw else "/$raw")
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "WordBuddy-${info.versionName}.apk")
        if (target.exists()) target.delete()
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code !in 200..299) error("下载失败 ($code)")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        if (total > 0) onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!target.exists() || target.length() < 1024L) {
            target.delete()
            error("下载的安装包无效")
        }
        onProgress(1f)
        target
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val uri = Uri.parse("package:${activity.packageName}")
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
