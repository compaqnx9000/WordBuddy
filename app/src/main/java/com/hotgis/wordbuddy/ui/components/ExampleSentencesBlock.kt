package com.hotgis.wordbuddy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.hotgis.wordbuddy.data.ExampleSentence
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.theme.HwColors
import com.hotgis.wordbuddy.ui.theme.hwColors

@Composable
fun ExampleSentencesBlock(
    word: String,
    examples: List<ExampleSentence>,
    compact: Boolean = false,
) {
    if (examples.isEmpty()) return
    val accent = hwColors().accentBlue
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "日常例句",
            color = HwColors.TextSecondary,
            fontSize = if (compact) 11.ssp() else 12.ssp(),
        )
        Spacer(Modifier.height(8.sdp()))
        examples.forEachIndexed { index, example ->
            val engSize = if (compact) 13.ssp() else 15.ssp()
            val zhSize = if (compact) 12.ssp() else 14.ssp()
            // 行高明显大于字号
            val engLine = if (compact) 24.ssp() else 28.ssp()
            val zhLine = if (compact) 22.ssp() else 26.ssp()
            val highlighted = remember(word, example.english, accent) {
                highlightHeadword(example.english, word, accent)
            }
            Text(
                text = highlighted,
                color = HwColors.TextPrimary,
                fontSize = engSize,
                lineHeight = engLine,
            )
            Spacer(Modifier.height(if (compact) 6.sdp() else 8.sdp()))
            Text(
                text = example.chinese,
                color = HwColors.TextSecondary,
                fontSize = zhSize,
                lineHeight = zhLine,
            )
            if (index < examples.lastIndex) {
                // 段间距大于行间距，例句之间更分明
                Spacer(Modifier.height(if (compact) 20.sdp() else 28.sdp()))
            }
        }
    }
}

fun highlightHeadword(
    sentence: String,
    word: String,
    accent: Color,
): androidx.compose.ui.text.AnnotatedString {
    val target = word.trim()
    if (target.isEmpty()) {
        return buildAnnotatedString { append(sentence) }
    }
    val lowerSentence = sentence.lowercase()
    val lowerWord = target.lowercase()
    var start = lowerSentence.indexOf(lowerWord)
    while (start >= 0) {
        val end = start + lowerWord.length
        val leftOk = start == 0 || !sentence[start - 1].isLetter()
        val rightOk = end >= sentence.length || !sentence[end].isLetter()
        if (leftOk && rightOk) break
        start = lowerSentence.indexOf(lowerWord, start + 1)
    }
    if (start < 0) {
        start = lowerSentence.indexOf(lowerWord)
    }
    return buildAnnotatedString {
        if (start < 0) {
            append(sentence)
            return@buildAnnotatedString
        }
        val end = start + lowerWord.length
        append(sentence.substring(0, start))
        withStyle(
            SpanStyle(
                color = accent,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(sentence.substring(start, end))
        }
        append(sentence.substring(end))
    }
}
