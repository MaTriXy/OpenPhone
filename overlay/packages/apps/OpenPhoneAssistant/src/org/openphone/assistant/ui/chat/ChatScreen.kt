package org.openphone.assistant.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.AssistantViewModel
import org.openphone.assistant.state.ChatSessionSummary
import org.openphone.assistant.state.ChatUiState
import org.openphone.assistant.state.PendingConfirmation
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.AssistantGlyph
import org.openphone.assistant.ui.common.AssistantIcon
import kotlinx.coroutines.launch

private val Suggestions = listOf(
    "What's on my screen?",
    "Summarize this page",
    "Help me finish this task",
)

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    pending: PendingConfirmation?,
    onShowAdvanced: () -> Unit,
    onShowRuntimes: () -> Unit,
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
) {
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AssistantDrawer(
                history = state.history,
                onNewChat = {
                    scope.launch { drawerState.close() }
                    onNewChat()
                },
                onShowAdvanced = {
                    scope.launch { drawerState.close() }
                    onShowAdvanced()
                },
                onShowRuntimes = {
                    scope.launch { drawerState.close() }
                    onShowRuntimes()
                },
                onOpenChat = {
                    scope.launch { drawerState.close() }
                    onOpenChat(it)
                },
                onOpenMemories = {
                    scope.launch { drawerState.close() }
                    onOpenMemories()
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                HeaderBar(
                    statusText = state.activeTaskId?.let { state.statusText } ?: "Ready",
                    onMenuClick = {
                        dismissKeyboard()
                        scope.launch { drawerState.open() }
                    },
                )
                Spacer(Modifier.height(16.dp))
                if (state.messages.isEmpty()) {
                    EmptyStateHero(
                        modifier = Modifier
                            .weight(1f)
                            .dismissKeyboardOnTap(dismissKeyboard),
                        suggestions = Suggestions,
                        onSuggestionClick = onSuggestionClick,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .dismissKeyboardOnTap(dismissKeyboard),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.messages.size) { index ->
                            ChatBubble(message = state.messages[index])
                        }
                        item {
                            AnimatedVisibility(
                                visible = pending != null,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                            ) {
                                if (pending != null) {
                                    ConfirmationCard(pending, onApprove, onDeny)
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.imePadding()) {
                    ComposerBar(
                        text = state.composerText,
                        isListening = state.isListening,
                        isRunning = state.isAgentRunning,
                        onTextChange = onComposerTextChange,
                        onActionClick = onComposerAction,
                        onMicClick = onMicClick,
                        onStopClick = onStopClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    statusText: String,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCapsule(
            glyph = AssistantGlyph.Menu,
            contentDescription = "Menu",
            onClick = onMenuClick,
        )
        Text(
            text = "OpenPhone",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        StatusPill(text = statusText)
    }
}

@Composable
private fun AssistantDrawer(
    history: List<ChatSessionSummary>,
    onNewChat: () -> Unit,
    onShowAdvanced: () -> Unit,
    onShowRuntimes: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenMemories: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "OpenPhone",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            NavigationDrawerItem(
                label = { Text("New Chat") },
                selected = false,
                onClick = onNewChat,
            )
            NavigationDrawerItem(
                label = { Text("Runtimes") },
                selected = false,
                onClick = onShowRuntimes,
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = false,
                onClick = onShowAdvanced,
            )
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            )
            Text(
                text = "Chats history",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (history.isEmpty()) {
                Text(
                    text = "No saved chats",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else {
                history.take(8).forEach { chat ->
                    NavigationDrawerItem(
                        label = { Text(chat.title) },
                        selected = false,
                        onClick = { onOpenChat(chat.id) },
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            )
            NavigationDrawerItem(
                label = { Text("Memories") },
                selected = false,
                onClick = onOpenMemories,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    AnimatedVisibility(visible = text.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun IconCapsule(glyph: AssistantGlyph, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AssistantIcon(glyph = glyph, tint = MaterialTheme.colorScheme.onSurface)
    }
}

private fun Modifier.dismissKeyboardOnTap(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        detectTapGestures(onTap = { onDismiss() })
    }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatScreenPreview() {
    OpenPhoneTheme {
        val state = AssistantViewModel.previewState()
        ChatScreen(
            state = state.chat,
            pending = state.pending,
            onShowAdvanced = {},
            onShowRuntimes = {},
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
        )
    }
}
