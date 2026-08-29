package com.zeroglab.hotwords.ui.design

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design canvas: iPhone 17 Pro Max logical size.
 * 6.9" · 440 × 956 pt @3x (native 1320 × 2868).
 * 1 design point maps to 1 dp when the window width is 440 dp.
 */
object DesignSpec {
    const val WIDTH_DP = 440f
    const val HEIGHT_DP = 956f
}

val LocalDesignScale = compositionLocalOf { 1f }
val LocalFontScale = compositionLocalOf { 1f }

@Composable
fun DesignScaleProvider(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = (maxWidth.value / DesignSpec.WIDTH_DP).coerceIn(0.72f, 1.35f)
        CompositionLocalProvider(
            LocalDesignScale provides scale,
            LocalFontScale provides fontScale.coerceIn(0.8f, 1.4f),
        ) {
            content()
        }
    }
}

@Composable
fun Int.sdp(): Dp = (this * LocalDesignScale.current).dp

@Composable
fun Float.sdp(): Dp = (this * LocalDesignScale.current).dp

@Composable
fun Int.ssp(): TextUnit = (this * LocalDesignScale.current * LocalFontScale.current).sp
