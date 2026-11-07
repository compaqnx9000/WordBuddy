package com.hotgis.wordbuddy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon via activity-alias components.
 *
 * User levels are 1–8:
 * - Lv.1 → default app icon ([.LauncherLevel0] / ic_launcher)
 * - Lv.2 → first alternate ([.LauncherLevel1] / ic_level_1)
 * - Lv.3+ → higher aliases (mostly reuse ic_level_2 until more assets exist)
 *
 * Only one alias should be enabled. Some OEM launchers still keep a stale second icon after a
 * switch until reboot; both entries belong to the same package, so uninstalling either removes
 * the app.
 */
object LauncherIcons {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 8

    /** Manifest aliases LauncherLevel0…7; slot 0 is the default icon. */
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

    /** Map Lv.1→slot0 (default), Lv.2→slot1, … Lv.8→slot7. */
    fun iconSlotForLevel(level: Int): Int = clamp(level) - MIN_LEVEL

    fun applyDefault(context: Context) = apply(context, MIN_LEVEL)

    fun apply(context: Context, level: Int) {
        val slot = iconSlotForLevel(level)
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
            ComponentName(pkg, pkg + aliases[slot]),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
