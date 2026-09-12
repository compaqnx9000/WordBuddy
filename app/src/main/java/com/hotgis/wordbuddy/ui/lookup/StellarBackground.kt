package com.hotgis.wordbuddy.ui.lookup

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

fun DrawScope.drawStellarBackgroundImage(bitmap: ImageBitmap) {
    val scale = max(size.width / bitmap.width, size.height / bitmap.height)
    val dstW = (bitmap.width * scale).roundToInt()
    val dstH = (bitmap.height * scale).roundToInt()
    val offsetX = ((size.width - dstW) / 2f).roundToInt()
    val offsetY = ((size.height - dstH) / 2f).roundToInt()
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(offsetX, offsetY),
        dstSize = IntSize(dstW, dstH),
    )
}

fun DrawScope.drawStellarScreenBackground(palette: StellarPalette, wallpaper: ImageBitmap? = null) {
    if (wallpaper != null) {
        drawStellarBackgroundImage(wallpaper)
        return
    }
    drawRect(palette.Background)
}

fun Modifier.stellarScreenBackground(): Modifier = composed {
    val palette = LocalStellar.current
    val resId = palette.backgroundImageRes
    if (resId != null) {
        val context = LocalContext.current
        val bitmap = remember(resId) {
            BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
        }
        drawBehind { drawStellarBackgroundImage(bitmap) }
    } else {
        background(palette.Background)
    }
}

@Composable
@ReadOnlyComposable
fun stellarScreenBackgroundColor(): Color = LocalStellar.current.Background

@Composable
@ReadOnlyComposable
fun stellarPanelBackgroundColor(): Color {
    val palette = LocalStellar.current
    return if (palette.backgroundImageRes != null) {
        palette.SurfaceContainer.copy(alpha = 0.72f)
    } else {
        palette.Background.copy(alpha = 0.80f)
    }
}

@Composable
@ReadOnlyComposable
fun hasStellarWallpaperBackground(): Boolean = LocalStellar.current.backgroundImageRes != null
