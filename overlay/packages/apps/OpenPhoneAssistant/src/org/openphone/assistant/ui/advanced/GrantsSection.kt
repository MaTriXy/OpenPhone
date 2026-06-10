package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.TaskGrants
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@Composable
fun GrantsSection(state: TaskGrants, onChange: (TaskGrants) -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Task grants", style = MaterialTheme.typography.titleMedium)
            SettingRow("Input", state.input) { onChange(state.copy(input = it)) }
            SettingRow("Screenshots", state.screenshot) { onChange(state.copy(screenshot = it)) }
            SettingRow("Clipboard", state.clipboard) { onChange(state.copy(clipboard = it)) }
            SettingRow("Share", state.share) { onChange(state.copy(share = it)) }
            SettingRow("Network", state.network) { onChange(state.copy(network = it)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GrantsSectionPreview() {
    OpenPhoneTheme {
        GrantsSection(TaskGrants(), {})
    }
}
