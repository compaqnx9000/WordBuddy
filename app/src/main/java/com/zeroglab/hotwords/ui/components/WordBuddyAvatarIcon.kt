package com.zeroglab.hotwords.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 「词搭子」头像：后方词卡 + 前方搭子卡，叠放表示「一起学词」。
 */
@Composable
fun WordBuddyAvatarIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    detailTint: Color = Color(0xFF2B8CFF),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.11f

        // 后方词卡（略透明，带词条横线）
        drawRoundRect(
            color = tint.copy(alpha = 0.82f),
            topLeft = Offset(w * 0.06f, h * 0.30f),
            size = Size(w * 0.50f, h * 0.56f),
            cornerRadius = CornerRadius(corner),
        )
        val lineStroke = w * 0.055f
        listOf(0.44f, 0.54f, 0.64f).forEachIndexed { index, yRatio ->
            val lineW = if (index == 2) w * 0.28f else w * 0.34f
            drawRoundRect(
                color = detailTint,
                topLeft = Offset(w * 0.14f, h * yRatio),
                size = Size(lineW, lineStroke),
                cornerRadius = CornerRadius(lineStroke / 2f),
            )
        }

        // 前方搭子卡
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.14f),
            size = Size(w * 0.50f, h * 0.56f),
            cornerRadius = CornerRadius(corner),
        )

        // 衔接圆点（两张卡「搭」在一起）
        drawCircle(
            color = tint,
            radius = w * 0.055f,
            center = Offset(w * 0.44f, h * 0.52f),
        )

        // 搭子小表情：两点 + 微笑弧
        val eyeR = w * 0.038f
        drawCircle(color = detailTint, radius = eyeR, center = Offset(w * 0.58f, h * 0.46f))
        drawCircle(color = detailTint, radius = eyeR, center = Offset(w * 0.72f, h * 0.46f))
        drawArc(
            color = detailTint,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.56f, h * 0.50f),
            size = Size(w * 0.20f, h * 0.14f),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round),
        )
    }
}
