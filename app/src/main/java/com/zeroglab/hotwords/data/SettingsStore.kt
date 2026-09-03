package com.zeroglab.hotwords.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): StudySettings {
        val accentName = prefs.getString(KEY_ACCENT, Accent.US.name) ?: Accent.US.name
        val themeName = prefs.getString(KEY_APP_THEME, AppTheme.Dark.name) ?: AppTheme.Dark.name
        val accentStyleName = prefs.getString(KEY_ACCENT_STYLE, AccentStyle.CyberNeon.name)
            ?: AccentStyle.CyberNeon.name
        val savedName = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME) ?: DEFAULT_DISPLAY_NAME
        val displayName = if (savedName == LEGACY_DISPLAY_NAME || savedName == "热词") {
            prefs.edit().putString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME).apply()
            DEFAULT_DISPLAY_NAME
        } else {
            savedName
        }
        return StudySettings(
            displayName = displayName,
            accent = runCatching { Accent.valueOf(accentName) }.getOrDefault(Accent.US),
            autoPlayIntervalMs = prefs.getLong(KEY_AUTO_PLAY_MS, 2500L),
            loop = prefs.getBoolean(KEY_LOOP, true),
            speakOnPageChange = prefs.getBoolean(KEY_SPEAK_ON_PAGE, true),
            dailyReminder = prefs.getBoolean(KEY_DAILY_REMINDER, true),
            aiImageAutoGen = prefs.getBoolean(KEY_AI_IMAGE, false),
            fontScale = prefs.getFloat(KEY_FONT_SCALE, FontSizeOption.Normal.scale),
            appTheme = runCatching { AppTheme.valueOf(themeName) }.getOrDefault(AppTheme.Dark),
            accentStyle = runCatching { AccentStyle.valueOf(accentStyleName) }.getOrDefault(AccentStyle.CyberNeon),
            defaultNotebookId = prefs.getLong(KEY_DEFAULT_NOTEBOOK_ID, Notebook.DEFAULT_ID),
        )
    }

    fun saveSettings(settings: StudySettings) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, settings.displayName)
            .putString(KEY_ACCENT, settings.accent.name)
            .putLong(KEY_AUTO_PLAY_MS, settings.autoPlayIntervalMs)
            .putBoolean(KEY_LOOP, settings.loop)
            .putBoolean(KEY_SPEAK_ON_PAGE, settings.speakOnPageChange)
            .putBoolean(KEY_DAILY_REMINDER, settings.dailyReminder)
            .putBoolean(KEY_AI_IMAGE, settings.aiImageAutoGen)
            .putFloat(KEY_FONT_SCALE, settings.fontScale)
            .putString(KEY_APP_THEME, settings.appTheme.name)
            .putString(KEY_ACCENT_STYLE, settings.accentStyle.name)
            .putLong(KEY_DEFAULT_NOTEBOOK_ID, settings.defaultNotebookId)
            .apply()
    }

    fun loadActiveNotebookId(): Long = prefs.getLong(KEY_ACTIVE_NOTEBOOK_ID, Notebook.DEFAULT_ID)

    fun saveActiveNotebookId(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_NOTEBOOK_ID, id).apply()
    }

    private companion object {
        const val PREFS_NAME = "hotwords_settings"
        const val DEFAULT_DISPLAY_NAME = "词搭子"
        const val LEGACY_DISPLAY_NAME = "热词学习者"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ACCENT = "accent"
        const val KEY_AUTO_PLAY_MS = "auto_play_ms"
        const val KEY_LOOP = "loop"
        const val KEY_SPEAK_ON_PAGE = "speak_on_page"
        const val KEY_DAILY_REMINDER = "daily_reminder"
        const val KEY_AI_IMAGE = "ai_image_auto_gen"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_ACCENT_STYLE = "accent_style"
        const val KEY_DEFAULT_NOTEBOOK_ID = "default_notebook_id"
        const val KEY_ACTIVE_NOTEBOOK_ID = "active_notebook_id"
    }
}
