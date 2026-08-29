package com.zeroglab.hotwords.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroglab.hotwords.data.Notebook
import com.zeroglab.hotwords.data.StudySettings

@Composable
fun ModeSettingsScreen(
    settings: StudySettings,
    notebooks: List<Notebook> = emptyList(),
    onBack: () -> Unit,
    onChange: ((StudySettings) -> StudySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSettingsScreen(
        settings = settings,
        notebooks = notebooks,
        onBack = onBack,
        onChange = onChange,
        modifier = modifier,
        title = "设置",
    )
}
