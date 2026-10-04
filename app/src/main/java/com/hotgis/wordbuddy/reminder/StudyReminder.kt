package com.hotgis.wordbuddy.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hotgis.wordbuddy.MainActivity
import com.hotgis.wordbuddy.R
import com.hotgis.wordbuddy.data.SettingsStore
import java.util.Calendar

/**
 * Local daily study reminder at 20:00, matching the iOS StudyReminder.
 * One exact alarm; the receiver shows the notification and schedules the next day.
 */
object StudyReminder {
    const val ACTION_FIRE = "com.hotgis.wordbuddy.action.DAILY_REMINDER"
    private const val CHANNEL_ID = "wordbuddy.daily.reminder"
    private const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 2001
    private const val HOUR = 20
    private const val MINUTE = 0

    fun sync(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        if (!enabled) {
            cancel(app)
            return
        }
        ensureChannel(app)
        schedule(app)
    }

    fun showAndReschedule(context: Context) {
        val app = context.applicationContext
        val enabled = SettingsStore(app).loadSettings().dailyReminder
        if (!enabled) {
            cancel(app)
            return
        }
        show(app)
        schedule(app)
    }

    private fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = firePendingIntent(context)
        val triggerAt = nextTriggerMillis()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(firePendingIntent(context))
    }

    private fun firePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, StudyReminderReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTriggerMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    private fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val content = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("词搭子")
            .setContentText("提醒你坚持背单词")
            .setAutoCancel(true)
            .setContentIntent(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "每日提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每天 20:00 提醒你坚持背单词"
        }
        manager.createNotificationChannel(channel)
    }
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val enabled = SettingsStore(context).loadSettings().dailyReminder
                StudyReminder.sync(context, enabled)
            }
            StudyReminder.ACTION_FIRE -> StudyReminder.showAndReschedule(context)
        }
    }
}
