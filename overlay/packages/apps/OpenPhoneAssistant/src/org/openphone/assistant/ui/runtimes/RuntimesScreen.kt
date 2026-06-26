package org.openphone.assistant.ui.runtimes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.state.AssistantViewModel
import org.openphone.assistant.state.RuntimeAdapterUiState
import org.openphone.assistant.state.RuntimesUiState
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.chat.IconCapsule
import org.openphone.assistant.ui.common.AssistantGlyph
import org.openphone.assistant.ui.common.GlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuntimesScreen(
    state: RuntimesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onSelectChatRuntime: (String) -> Unit,
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
                text = "Runtimes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(state.status)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default chat runtime", style = MaterialTheme.typography.titleMedium)
                    KeyValueLine("Selected", runtimeDisplayName(state.chatRuntime))
                    KeyValueLine("Effective", runtimeDisplayName(state.effectiveChatRuntime))
                    KeyValueLine("Volume trigger", "${runtimeDisplayName(state.volumeRuntime)} (V1)")
                    KeyValueLine("Background tasks", "${runtimeDisplayName(state.backgroundRuntime)} (V1)")
                }
            }

            LocalRuntimeCard(
                selected = state.chatRuntime != "openclaw",
                onSelect = { onSelectChatRuntime("builtin") },
            )

            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Remote runtime manager", style = MaterialTheme.typography.titleMedium)
                    KeyValueLine("Status", state.status)
                    KeyValueLine("Manager", state.managerStatus)
                    if (state.updatedAtMillis > 0L) {
                        KeyValueLine("Updated", ageText(state.updatedAtMillis))
                    }
                    if (state.lastAction.isNotBlank()) {
                        KeyValueLine("Last action", state.lastAction)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onRefresh) { Text("Refresh") }
                        OutlinedButton(onClick = onReconnect) { Text("Reconnect") }
                    }
                }
            }

            if (state.adapters.isEmpty()) {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No external runtimes are configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.adapters.forEach { adapter ->
                    RuntimeCard(
                        adapter = adapter,
                        selected = state.chatRuntime == adapter.name,
                        onSelect = { onSelectChatRuntime(adapter.name) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LocalRuntimeCard(
    selected: Boolean,
    onSelect: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot("connected")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = "Local Phone Runtime",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Built-in phone brain and execution layer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge("available")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallBadge("Always on")
                SmallBadge("Volume trigger")
                SmallBadge("Phone actions")
                SmallBadge(if (selected) "Selected" else "Fallback")
            }
            RuntimeSelectButton(selected = selected, enabled = true, onSelect = onSelect)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RuntimeCard(
    adapter: RuntimeAdapterUiState,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(adapter.status)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = adapter.label.ifBlank { adapter.name },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = runtimeTitle(adapter.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(adapter.status)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallBadge(if (adapter.enabled) "Enabled" else "Disabled")
                SmallBadge(if (adapter.configured) "Configured" else "Missing URL")
                SmallBadge(if (selected) "Selected" else "Remote brain")
            }
            if (adapter.url.isNotBlank()) {
                KeyValueLine("Endpoint", adapter.url)
            }
            if (adapter.deviceId.isNotBlank()) {
                KeyValueLine("Device", adapter.deviceId)
            }
            RuntimeSelectButton(
                selected = selected,
                enabled = adapter.enabled && adapter.configured,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun RuntimeSelectButton(
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    if (selected) {
        OutlinedButton(onClick = {}, enabled = false) { Text("Selected for chat") }
    } else {
        Button(onClick = onSelect, enabled = enabled) { Text("Use for chat") }
    }
}

@Composable
private fun KeyValueLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "unknown" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    SmallBadge(status.ifBlank { "unknown" })
}

@Composable
private fun SmallBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun StatusDot(status: String) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(statusColor(status)),
    )
}

private fun statusColor(status: String): Color =
    when (status.lowercase()) {
        "connected", "available" -> Color(0xFF0E8A4A)
        "connecting", "waiting_for_challenge", "handshaking" -> Color(0xFFB7791F)
        "degraded" -> Color(0xFFD97706)
        "disabled", "stopped", "not_configured" -> Color(0xFF768094)
        else -> Color(0xFFB42318)
    }

private fun runtimeTitle(name: String): String =
    when (name.lowercase()) {
        "openclaw" -> "OpenClaw"
        "hermes" -> "Hermes"
        else -> name.replaceFirstChar { it.uppercase() }
    }

private fun runtimeDisplayName(name: String): String =
    when (name.lowercase()) {
        "builtin", "local" -> "Local Phone Runtime"
        "openclaw" -> "OpenClaw"
        "hermes" -> "Hermes"
        "auto" -> "Auto"
        else -> name.ifBlank { "unknown" }
    }

private fun ageText(updatedAtMillis: Long): String {
    val elapsedSeconds = ((System.currentTimeMillis() - updatedAtMillis) / 1000L).coerceAtLeast(0L)
    return when {
        elapsedSeconds < 5L -> "just now"
        elapsedSeconds < 60L -> "${elapsedSeconds}s ago"
        elapsedSeconds < 3600L -> "${elapsedSeconds / 60L}m ago"
        else -> "${elapsedSeconds / 3600L}h ago"
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RuntimesScreenPreview() {
    OpenPhoneTheme {
        RuntimesScreen(
            state = AssistantViewModel.previewState().runtimes,
            onBack = {},
            onRefresh = {},
            onReconnect = {},
            onSelectChatRuntime = {},
        )
    }
}
