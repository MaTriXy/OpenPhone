package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import org.openphone.assistant.state.DeveloperState
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperSection(
    state: DeveloperState,
    onGoalChange: (String) -> Unit,
    onRawActionJsonChange: (String) -> Unit,
    onStartTask: () -> Unit,
    onRunAgent: () -> Unit,
    onStopAgent: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRefresh: () -> Unit,
    onReadScreen: () -> Unit,
    onReadScreenshot: () -> Unit,
    onExecuteBack: () -> Unit,
    onRunRawAction: () -> Unit,
    onExportTrace: () -> Unit,
    onExportAudit: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Developer controls", style = MaterialTheme.typography.titleMedium)
            Text(state.taskStatus, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.goal,
                onValueChange = onGoalChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Goal") },
                minLines = 2,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onStartTask) { Text("Start") }
                Button(onClick = onRunAgent) { Text("Run Agent") }
                OutlinedButton(onClick = onStopAgent) { Text("Stop") }
                OutlinedButton(onClick = onReadScreen) { Text("Screen") }
                OutlinedButton(onClick = onReadScreenshot) { Text("Shot") }
                OutlinedButton(onClick = onExecuteBack) { Text("Back") }
                OutlinedButton(onClick = onExportTrace) { Text("Export Trace") }
                OutlinedButton(onClick = onExportAudit) { Text("Export Audit") }
            }
            OutlinedTextField(
                value = state.rawActionJson,
                onValueChange = onRawActionJsonChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Raw action JSON") },
                minLines = 3,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRunRawAction) { Text("Action") }
                Button(onClick = onApprove) { Text("Approve") }
                OutlinedButton(onClick = onDeny) { Text("Deny") }
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeveloperSectionPreview() {
    OpenPhoneTheme {
        DeveloperSection(
            state = DeveloperState(goal = "Open Settings"),
            onGoalChange = {},
            onRawActionJsonChange = {},
            onStartTask = {},
            onRunAgent = {},
            onStopAgent = {},
            onApprove = {},
            onDeny = {},
            onRefresh = {},
            onReadScreen = {},
            onReadScreenshot = {},
            onExecuteBack = {},
            onRunRawAction = {},
            onExportTrace = {},
            onExportAudit = {},
        )
    }
}
