package org.openphone.assistant.ui.advanced

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import org.openphone.assistant.ui.OpenPhoneTheme
import org.openphone.assistant.ui.common.GlassSurface

@Composable
fun ContextDump(text: String) {
    DumpCard(title = "Screen context", text = text)
}

@Composable
fun AuditDump(text: String) {
    DumpCard(title = "Audit log", text = text)
}

@Composable
private fun DumpCard(title: String, text: String) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = text,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DumpCardPreview() {
    OpenPhoneTheme {
        ContextDump("package: com.android.settings\nnode: Continue")
    }
}
