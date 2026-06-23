package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.AdvancedUiState
import org.openphone.assistant.state.AssistantViewModel
import org.openphone.assistant.state.ModelConfig
import org.openphone.assistant.state.TaskGrants
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.chat.IconCapsule
import org.openphone.assistant.ui.common.AssistantGlyph

@Composable
fun AdvancedScreen(
    state: AdvancedUiState,
    onBack: () -> Unit,
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
    onCheckOta: () -> Unit,
    onDownloadOta: () -> Unit,
    onModelConfigChange: (ModelConfig) -> Unit,
    onOtaFeedUrlChange: (String) -> Unit,
    onTaskGrantsChange: (TaskGrants) -> Unit,
    onAutonomyModeChange: (String) -> Unit,
    onDeveloperGoalChange: (String) -> Unit,
    onRawActionJsonChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCapsule(glyph = AssistantGlyph.Back, contentDescription = "Back", onClick = onBack)
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModelSection(state.model, onModelConfigChange)
            OtaSection(state.ota, onOtaFeedUrlChange, onCheckOta, onDownloadOta)
            AutonomySection(state.autonomyMode, onAutonomyModeChange)
            GrantsSection(state.grants, onTaskGrantsChange)
            DeveloperSection(
                state = state.developer,
                onGoalChange = onDeveloperGoalChange,
                onRawActionJsonChange = onRawActionJsonChange,
                onStartTask = onStartTask,
                onRunAgent = onRunAgent,
                onStopAgent = onStopAgent,
                onApprove = onApprove,
                onDeny = onDeny,
                onRefresh = onRefresh,
                onReadScreen = onReadScreen,
                onReadScreenshot = onReadScreenshot,
                onExecuteBack = onExecuteBack,
                onRunRawAction = onRunRawAction,
                onExportTrace = onExportTrace,
                onExportAudit = onExportAudit,
            )
            ContextDump(text = state.developer.screenContext)
            AuditDump(text = state.developer.auditLog)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdvancedScreenPreview() {
    OpenPhoneTheme {
        AdvancedScreen(
            state = AssistantViewModel.previewState().advanced,
            onBack = {},
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
            onCheckOta = {},
            onDownloadOta = {},
            onModelConfigChange = {},
            onOtaFeedUrlChange = {},
            onTaskGrantsChange = {},
            onAutonomyModeChange = {},
            onDeveloperGoalChange = {},
            onRawActionJsonChange = {},
        )
    }
}
