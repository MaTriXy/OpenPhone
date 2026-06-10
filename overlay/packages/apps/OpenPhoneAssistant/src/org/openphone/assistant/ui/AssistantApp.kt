package org.openphone.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.openphone.assistant.state.AssistantRoute
import org.openphone.assistant.state.AssistantUiState
import org.openphone.assistant.state.AssistantViewModel
import org.openphone.assistant.ui.advanced.AdvancedScreen
import org.openphone.assistant.ui.chat.ChatScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AssistantApp(
    state: AssistantUiState,
    onShowAdvanced: () -> Unit,
    onShowChat: () -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenMemories: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onComposerAction: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onStartTask: () -> Unit,
    onRunAgent: () -> Unit,
    onStopAgent: () -> Unit,
    onRefresh: () -> Unit,
    onReadScreen: () -> Unit,
    onReadScreenshot: () -> Unit,
    onExecuteBack: () -> Unit,
    onRunRawAction: () -> Unit,
    onExportTrace: () -> Unit,
    onExportAudit: () -> Unit,
    onCheckOta: () -> Unit,
    onDownloadOta: () -> Unit,
    onModelConfigChange: (org.openphone.assistant.state.ModelConfig) -> Unit,
    onOtaFeedUrlChange: (String) -> Unit,
    onTaskGrantsChange: (org.openphone.assistant.state.TaskGrants) -> Unit,
    onAutonomyModeChange: (String) -> Unit,
    onDeveloperGoalChange: (String) -> Unit,
    onRawActionJsonChange: (String) -> Unit,
) {
    AnimatedContent(
        targetState = state.route,
        transitionSpec = {
            val forward = targetState == AssistantRoute.Advanced
            val enterOffset: (Int) -> Int = { width -> if (forward) width / 5 else -width / 5 }
            val exitOffset: (Int) -> Int = { width -> if (forward) -width / 5 else width / 5 }
            (fadeIn() + slideInHorizontally(initialOffsetX = enterOffset))
                .togetherWith(fadeOut() + slideOutHorizontally(targetOffsetX = exitOffset))
        },
        label = "assistant-route",
    ) { route ->
        when (route) {
            AssistantRoute.Chat -> ChatScreen(
                state = state.chat,
                pending = state.pending,
                onShowAdvanced = onShowAdvanced,
                onNewChat = onNewChat,
                onOpenChat = onOpenChat,
                onOpenMemories = onOpenMemories,
                onComposerTextChange = onComposerTextChange,
                onSuggestionClick = onSuggestionClick,
                onComposerAction = onComposerAction,
                onMicClick = onMicClick,
                onStopClick = onStopClick,
                onApprove = onApprove,
                onDeny = onDeny,
            )
            AssistantRoute.Advanced -> AdvancedScreen(
                state = state.advanced,
                onBack = onShowChat,
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
                onCheckOta = onCheckOta,
                onDownloadOta = onDownloadOta,
                onModelConfigChange = onModelConfigChange,
                onOtaFeedUrlChange = onOtaFeedUrlChange,
                onTaskGrantsChange = onTaskGrantsChange,
                onAutonomyModeChange = onAutonomyModeChange,
                onDeveloperGoalChange = onDeveloperGoalChange,
                onRawActionJsonChange = onRawActionJsonChange,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AssistantAppPreview() {
    OpenPhoneTheme {
        AssistantApp(
            state = AssistantViewModel.previewState(),
            onShowAdvanced = {},
            onShowChat = {},
            onNewChat = {},
            onOpenChat = {},
            onOpenMemories = {},
            onComposerTextChange = {},
            onSuggestionClick = {},
            onComposerAction = {},
            onMicClick = {},
            onStopClick = {},
            onApprove = {},
            onDeny = {},
            onStartTask = {},
            onRunAgent = {},
            onStopAgent = {},
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
