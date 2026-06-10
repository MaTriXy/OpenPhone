package org.openphone.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.ChatMessage
import org.openphone.assistant.ui.OpenPhoneTheme

@Composable
fun ChatBubble(message: ChatMessage) {
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
    }
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        if (message.isUser) Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .widthIn(max = 316.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = message.body,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (!message.isUser) Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatBubblePreview() {
    OpenPhoneTheme {
        ChatBubble(ChatMessage("OpenPhone", "I can help with the visible screen.", false))
    }
}
