package com.zeroglab.hotwords.ui.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp

@Composable
fun LookupInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .stellarGlass(neon = true)
            .padding(horizontal = 14.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = "查询",
            tint = Stellar.OnSurfaceVariant,
            modifier = Modifier
                .size(22.sdp())
                .clickable(onClick = onSubmit),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.sdp()),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Stellar.OnSurface,
                    fontSize = 16.ssp(),
                ),
                cursorBrush = SolidColor(Stellar.CyanBright),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.sdp())
                    .onFocusChanged { focused = it.isFocused },
            )
            if (value.isEmpty()) {
                Text(
                    text = "Search words...",
                    color = Stellar.OnSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 16.ssp(),
                )
            }
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Cancel,
                contentDescription = "清除",
                tint = Stellar.OnSurfaceVariant,
                modifier = Modifier
                    .size(20.sdp())
                    .clickable { onValueChange("") },
            )
        }
    }
}
