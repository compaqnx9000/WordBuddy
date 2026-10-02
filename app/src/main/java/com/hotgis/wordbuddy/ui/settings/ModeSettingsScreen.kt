package com.hotgis.wordbuddy.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hotgis.wordbuddy.data.StudySettings

@Composable
fun ModeSettingsScreen(
    settings: StudySettings,
    onBack: () -> Unit,
    onChange: ((StudySettings) -> StudySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSettingsScreen(
        settings = settings,
        onBack = onBack,
        onChange = onChange,
        modifier = modifier,
        title = "设置",
    )
}
