package com.zeroglab.hotwords.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.zeroglab.hotwords.data.Definition
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp
import com.zeroglab.hotwords.ui.theme.HwColors

@Composable
fun DefinitionList(
    definitions: List<Definition>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.ssp(),
    posFontSize: TextUnit = fontSize,
    rowSpacing: androidx.compose.ui.unit.Dp = 4.sdp(),
) {
    Column(modifier) {
        definitions.forEach { def ->
            DefinitionRow(
                definition = def,
                fontSize = fontSize,
                posFontSize = posFontSize,
                modifier = Modifier.padding(bottom = rowSpacing),
            )
        }
    }
}

@Composable
fun DefinitionRow(
    definition: Definition,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.ssp(),
    posFontSize: TextUnit = fontSize,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (definition.isUserAdded) {
                Modifier
                    .clip(RoundedCornerShape(6.sdp()))
                    .background(HwColors.UserNoteHighlight)
                    .padding(vertical = 4.sdp())
            } else {
                Modifier
            },
        )
    Row(
        rowModifier,
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(44.sdp())) {
            if (definition.pos.isNotBlank()) {
                Text(
                    text = definition.pos,
                    color = HwColors.TextSecondary,
                    fontSize = posFontSize,
                )
            }
        }
        Text(
            text = definition.meaning,
            color = HwColors.TextPrimary,
            fontSize = fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DefinitionListSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = HwColors.TextSecondary,
        fontSize = 12.ssp(),
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(bottom = 6.sdp()),
    )
}
