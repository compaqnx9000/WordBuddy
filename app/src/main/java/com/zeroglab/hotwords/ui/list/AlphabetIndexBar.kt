package com.zeroglab.hotwords.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.lookup.Stellar
import kotlin.math.roundToInt

internal val AlphabetIndexLetters: List<Char> =
    ('A'..'Z').toList() + '#'

internal fun wordInitialLetter(text: String): Char {
    val first = text.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

internal fun letterSortKey(letter: Char): Int =
    if (letter == '#') 26 else (letter - 'A').coerceIn(0, 25)

/** First list index for each initial letter present in [texts] (current list order). */
internal fun buildAlphabetIndexMap(texts: List<String>): Map<Char, Int> {
    val map = linkedMapOf<Char, Int>()
    texts.forEachIndexed { index, text ->
        val letter = wordInitialLetter(text)
        if (letter !in map) map[letter] = index
    }
    return map
}

/**
 * Prefer the first word starting with [letter].
 * If that letter is absent from the loaded slice, use the first word whose initial is after it.
 * Returns null when the letter is not in [texts] and no later letter is present either
 * (typical while earlier pages are still loading — caller must not scroll to lastIndex).
 */
internal fun indexForAlphabetLetter(letter: Char, texts: List<String>): Int? {
    if (texts.isEmpty()) return null
    val exact = texts.indexOfFirst { wordInitialLetter(it) == letter }
    if (exact >= 0) return exact
    val key = letterSortKey(letter)
    val next = texts.indexOfFirst { letterSortKey(wordInitialLetter(it)) > key }
    if (next >= 0) return next
    return null
}

/**
 * Resolve scroll target. Prefer a letter already present in the loaded [texts]
 * (works for both contiguous lists and alphabet jump windows). Otherwise use
 * server [absoluteIndex] when the contiguous list has loaded past that offset.
 */
internal fun resolveAlphabetScrollIndex(
    letter: Char,
    texts: List<String>,
    absoluteIndex: Map<Char, Int>,
    listComplete: Boolean,
): Int? {
    if (texts.isEmpty()) return null
    buildAlphabetIndexMap(texts)[letter]?.let { return it }
    val abs = absoluteIndex[letter]
    if (abs != null) {
        if (texts.size <= abs) return null
        return indexForAlphabetLetter(letter, texts)
            ?: abs.coerceIn(0, texts.lastIndex)
    }
    if (!listComplete) return null
    return indexForAlphabetLetter(letter, texts) ?: texts.lastIndex
}

@Composable
fun AlphabetIndexBar(
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf<Char?>(null) }
    var fingerY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(28.sdp())
            .padding(vertical = 4.sdp()),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val letters = AlphabetIndexLetters

        fun letterAt(y: Float): Char {
            val t = (y / heightPx).coerceIn(0f, 0.999f)
            return letters[(t * letters.size).toInt()]
        }

        fun selectAt(y: Float) {
            val letter = letterAt(y)
            fingerY = y
            if (active != letter) {
                active = letter
                onSelect(letter)
            }
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(24.sdp())
                .pointerInput(heightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        selectAt(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            // Keep tracking even when positionChange is tiny / zero between frames
                            // so letter boundaries still update under a slow finger.
                            selectAt(change.position.y)
                            if (change.positionChange() != Offset.Zero) change.consume()
                        } while (true)
                        active = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                letters.forEach { letter ->
                    val selected = letter == active
                    Box(
                        Modifier
                            .size(if (selected) 16.sdp() else 12.sdp())
                            .then(
                                if (selected) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Stellar.Cyan)
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter.toString(),
                            color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                            fontSize = 9.ssp(),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        val current = active
        if (current != null) {
            val bubbleSize = 56.sdp()
            val bubblePx = with(density) { bubbleSize.toPx() }
            // Keep the preview clear of the thumb on the alphabet rail.
            val bubbleShiftX = with(density) { 72.sdp().roundToPx() }
            val y = (fingerY - bubblePx / 2f)
                .coerceIn(0f, (heightPx - bubblePx).coerceAtLeast(0f))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -bubbleShiftX,
                            y = y.roundToInt(),
                        )
                    }
                    .size(bubbleSize)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.sdp(),
                            topEnd = 28.sdp(),
                            bottomStart = 28.sdp(),
                            bottomEnd = 4.sdp(),
                        ),
                    )
                    .background(Stellar.SurfaceHigh.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.toString(),
                    color = Stellar.OnSurface,
                    fontSize = 28.ssp(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
