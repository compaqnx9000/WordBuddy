package com.zeroglab.hotwords.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.theme.HwColors

@Composable
fun MnemonicImage(
    imageBlob: ByteArray?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "记忆图",
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (imageBlob == null || imageBlob.isEmpty()) return
    val bitmap = remember(imageBlob) {
        BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.size)
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .clip(RoundedCornerShape(12.sdp()))
            .background(HwColors.SurfaceGray),
    )
}
