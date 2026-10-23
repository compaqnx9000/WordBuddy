package com.hotgis.wordbuddy.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hotgis.wordbuddy.MainActivity
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.data.AppUpdateInfo

/**
 * Tells the user a newer build is on the server. The update itself stays in
 * 我 → 关于你的词搭子 → 检测更新.
 */
object AppUpdateNotifier {
    private const val CHANNEL_ID = "wordbuddy.app.update"
    private const val NOTIFICATION_ID = 2002

    fun show(context: Context, info: AppUpdateInfo) {
        val app = context.applicationContext
        if (!canPost(app)) return
        ensureChannel(app)
        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val content = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message =
            "发现新版本 ${info.versionName}。请打开「我 - 关于你的词搭子 - 检测更新」更新 App。"
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("词搭子可以更新了")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "版本更新",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "发现新版本时提醒去关于页检测更新"
        }
        manager.createNotificationChannel(channel)
    }
}
