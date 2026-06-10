package org.openphone.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.AssistantGlyph
import org.openphone.assistant.ui.common.AssistantIcon

@Composable
fun ComposerBar(
    text: String,
    isListening: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
    onActionClick: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val busy = isListening || isRunning
    val glyph = when {
        busy -> AssistantGlyph.Stop
        text.isBlank() -> AssistantGlyph.Mic
        else -> AssistantGlyph.Send
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Ask OpenPhone",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (busy || text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                )
                .clickable {
                when {
                    busy -> onStopClick()
                    text.isBlank() -> onMicClick()
                    else -> onActionClick()
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            AssistantIcon(
                glyph = glyph,
                tint = if (busy || text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(if (glyph == AssistantGlyph.Mic) 14.dp else 18.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposerBarPreview() {
    OpenPhoneTheme {
        ComposerBar(
            text = "Find the latest notification",
            isListening = false,
            isRunning = false,
            onTextChange = {},
            onActionClick = {},
            onMicClick = {},
            onStopClick = {},
        )
    }
}
