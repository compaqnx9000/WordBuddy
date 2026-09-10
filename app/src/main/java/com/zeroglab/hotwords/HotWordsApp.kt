package com.zeroglab.hotwords

import android.app.Application
import com.zeroglab.hotwords.data.SessionStore

class HotWordsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val level = SessionStore(this).load()?.level ?: 0
        LauncherIcons.apply(this, level)
    }
}
