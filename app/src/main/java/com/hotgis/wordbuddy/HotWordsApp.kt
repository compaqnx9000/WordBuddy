package com.hotgis.wordbuddy

import android.app.Application
import com.hotgis.wordbuddy.data.SessionStore

class HotWordsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val level = SessionStore(this).load()?.level ?: 0
        LauncherIcons.apply(this, level)
    }
}
