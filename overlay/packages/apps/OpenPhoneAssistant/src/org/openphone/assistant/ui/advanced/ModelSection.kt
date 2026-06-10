package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.ModelConfig
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@Composable
fun ModelSection(state: ModelConfig, onChange: (ModelConfig) -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Model", style = MaterialTheme.typography.titleMedium)
            SettingRow(
                "Realtime vision",
                checked = state.useRealtimeVision,
                onCheckedChange = { onChange(state.copy(useRealtimeVision = it)) },
            )
            SettingRow(
                "Use broker",
                checked = state.useBroker,
                onCheckedChange = { onChange(state.copy(useBroker = it)) },
            )
            OutlinedTextField(
                value = state.devApiKey,
                onValueChange = { onChange(state.copy(devApiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Development API key") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.brokerUrl,
                onValueChange = { onChange(state.copy(brokerUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Broker URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.brokerToken,
                onValueChange = { onChange(state.copy(brokerToken = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Broker token") },
                singleLine = true,
            )
            Text(state.disclosure, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSectionPreview() {
    OpenPhoneTheme {
        ModelSection(ModelConfig(useRealtimeVision = true, useBroker = true), {})
    }
}
