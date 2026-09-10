package com.zeroglab.hotwords

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon via activity-alias components.
 * Level 0 = original app icon; 1–2 have dedicated art; 3–7 reuse level-2 until more assets exist.
 */
object LauncherIcons {
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 7

    private val aliases = listOf(
        ".LauncherLevel0",
        ".LauncherLevel1",
        ".LauncherLevel2",
        ".LauncherLevel3",
        ".LauncherLevel4",
        ".LauncherLevel5",
        ".LauncherLevel6",
        ".LauncherLevel7",
    )

    fun clamp(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    fun apply(context: Context, level: Int) {
        val target = clamp(level)
        val pm = context.packageManager
        val pkg = context.packageName
        aliases.forEachIndexed { index, suffix ->
            val enabled = index == target
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + suffix),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
