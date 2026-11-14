package com.hotgis.wordbuddy

import android.app.Application
import com.hotgis.wordbuddy.data.SettingsStore
import com.hotgis.wordbuddy.reminder.StudyReminder

class WordBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LauncherIcons.restoreDefault(this)
        if (SettingsStore(this).loadSettings().dailyReminder) {
            StudyReminder.sync(this, enabled = true)
        }
    }
}
