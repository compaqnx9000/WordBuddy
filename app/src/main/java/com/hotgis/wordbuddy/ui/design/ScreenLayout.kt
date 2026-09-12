package com.hotgis.wordbuddy.ui.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Shared compact chrome sizes to use screen space efficiently. */
object ScreenLayout {
    @Composable
    fun topBarHeight(): Dp = 44.sdp()

    @Composable
    fun contentTopGap(): Dp = 8.sdp()
}

@Composable
fun Modifier.hotWordsScreen(
    scaffoldPadding: PaddingValues,
    consumeStatusBars: Boolean = true,
): Modifier {
    val base = this.padding(scaffoldPadding)
    return if (consumeStatusBars) {
        base.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
    } else {
        base
    }
}
