package com.hotgis.wordbuddy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Older builds swapped the launcher icon by user level. Those aliases stay in the manifest so an
 * already-switched install can be put back on the default icon. Nothing selects them anymore.
 */
object LauncherIcons {
    private const val DEFAULT = ".LauncherLevel0"
    private val retired = listOf(
        ".LauncherLevel1",
        ".LauncherLevel2",
        ".LauncherLevel3",
        ".LauncherLevel4",
        ".LauncherLevel5",
        ".LauncherLevel6",
        ".LauncherLevel7",
    )

    fun restoreDefault(context: Context) {
        val pm = context.packageManager
        val pkg = context.packageName
        retired.forEach { suffix ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + suffix),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + DEFAULT),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
