package com.hotgis.wordbuddy.ui.design

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

/** Fraction of the extra width (beyond the phone canvas) that turns into scale. */
private const val WIDE_GROWTH = 0.3f

/** Hard cap for windows wider than the phone canvas (an unfolded 800 dp screen lands here). */
private const val WIDE_MAX_SCALE = 1.15f

val LocalDesignScale = compositionLocalOf { 1f }
val LocalFontScale = compositionLocalOf { 1f }

@Composable
fun DesignScaleProvider(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    /** When set, ignore ambient window heuristics and scale to this width (dp). */
    forceReferenceWidthDp: Float? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Always scale to the actual constraints of this provider.
        // Dual-pane panes nest their own provider so each half scales like a phone;
        // full-bleed pages (e.g. Shorts) see the real window width and fill it.
        val referenceWidth = forceReferenceWidthDp?.takeIf { it > 0f } ?: maxWidth.value
        val ratio = referenceWidth / DesignSpec.WIDTH_DP
        val scale = if (ratio > 1f) {
            // Unfolded foldables / tablets: grow gently and let the layout use the extra width,
            // instead of stretching every title, bar and button 1:1 with the window.
            (1f + (ratio - 1f) * WIDE_GROWTH).coerceAtMost(WIDE_MAX_SCALE)
        } else {
            ratio.coerceAtLeast(0.72f)
        }
        CompositionLocalProvider(
            LocalDesignScale provides scale,
            LocalFontScale provides fontScale.coerceIn(0.8f, 1.4f),
        ) {
            content()
        }
    }
}

/**
 * Bottom tabs stay at the phone design height. The root scale grows with the
 * unfolded window (up to 1.6), which stretches these buttons; wide windows keep scale 1.
 */
@Composable
fun PhoneSizedChrome(content: @Composable () -> Unit) {
    val scale = if (LocalFoldableLayout.current.windowWidthDp > DesignSpec.WIDTH_DP) {
        1f
    } else {
        LocalDesignScale.current
    }
    CompositionLocalProvider(LocalDesignScale provides scale, content = content)
}

@Composable
fun Int.sdp(): Dp = (this * LocalDesignScale.current).dp

@Composable
fun Float.sdp(): Dp = (this * LocalDesignScale.current).dp

@Composable
fun Int.ssp(): TextUnit = (this * LocalDesignScale.current * LocalFontScale.current).sp
