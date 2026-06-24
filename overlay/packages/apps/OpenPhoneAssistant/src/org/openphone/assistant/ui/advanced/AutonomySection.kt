package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutonomySection(mode: String, onModeChange: (String) -> Unit) {
    val yolo = mode == "yolo"
    val dryRun = mode == "dry_run"
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Autonomy", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!yolo && !dryRun) {
                    Button(onClick = { onModeChange("reviewed") }) { Text("Reviewed") }
                } else {
                    OutlinedButton(onClick = { onModeChange("reviewed") }) { Text("Reviewed") }
                }
                if (yolo) {
                    Button(onClick = { onModeChange("yolo") }) { Text("YOLO") }
                } else {
                    OutlinedButton(onClick = { onModeChange("yolo") }) { Text("YOLO") }
                }
                if (dryRun) {
                    Button(onClick = { onModeChange("dry_run") }) { Text("Dry run") }
                } else {
                    OutlinedButton(onClick = { onModeChange("dry_run") }) { Text("Dry run") }
                }
            }
            Text(
                text = when {
                    yolo -> "YOLO runs registered actions without confirmation prompts. External app or OS prompts may still appear."
                    dryRun -> "Dry run previews registered actions and records them without executing device changes."
                    else -> "Reviewed asks before medium and high-risk registered actions."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AutonomySectionPreview() {
    OpenPhoneTheme {
        AutonomySection("yolo", {})
    }
}
