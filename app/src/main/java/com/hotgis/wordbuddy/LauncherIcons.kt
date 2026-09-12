package com.hotgis.wordbuddy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon via activity-alias components.
 * Level 0 = original app icon; 1–2 have dedicated art; 3–7 reuse level-2 until more assets exist.
 *
 * Only one alias should be enabled. Some OEM launchers still keep a stale second icon after a
 * switch until reboot; both entries belong to the same package, so uninstalling either removes
 * the app.
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
        // Disable every alias first so OEMs never briefly see two LAUNCHER components.
        aliases.forEach { suffix ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + suffix),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + aliases[target]),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
