package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.OtaState
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@Composable
fun OtaSection(
    state: OtaState,
    onFeedUrlChange: (String) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OTA preview", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.feedUrl,
                onValueChange = onFeedUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Feed URL") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCheck, modifier = Modifier.weight(1f)) {
                    Text("Check")
                }
                OutlinedButton(
                    onClick = onDownload,
                    enabled = state.canDownload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Download")
                }
            }
            Text(state.status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OtaSectionPreview() {
    OpenPhoneTheme {
        OtaSection(OtaState(feedUrl = "https://updates.openphone.invalid/feed.json", canDownload = true), {}, {}, {})
    }
}
