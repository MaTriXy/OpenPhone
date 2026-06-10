package org.openphone.assistant.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openphone.assistant.ui.OpenPhoneTheme

enum class AssistantGlyph {
    Back,
    Menu,
    Mic,
    Profile,
    Send,
    Stop,
}

@Composable
fun AssistantIcon(glyph: AssistantGlyph, tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (glyph) {
            AssistantGlyph.Back -> {
                drawLine(tint, Offset(cx + 5.dp.toPx(), cy - 7.dp.toPx()), Offset(cx - 4.dp.toPx(), cy), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx - 4.dp.toPx(), cy), Offset(cx + 5.dp.toPx(), cy + 7.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AssistantGlyph.Menu -> {
                drawLine(tint, Offset(cx - 8.dp.toPx(), cy - 6.dp.toPx()), Offset(cx + 8.dp.toPx(), cy - 6.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx - 8.dp.toPx(), cy), Offset(cx + 8.dp.toPx(), cy), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx - 8.dp.toPx(), cy + 6.dp.toPx()), Offset(cx + 8.dp.toPx(), cy + 6.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AssistantGlyph.Mic -> {
                drawRoundRect(tint, Offset(cx - 3.8.dp.toPx(), cy - 7.5.dp.toPx()), Size(7.6.dp.toPx(), 12.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(width = stroke.width))
                drawArc(tint, 20f, 140f, false, Offset(cx - 8.dp.toPx(), cy - 3.dp.toPx()), Size(16.dp.toPx(), 12.dp.toPx()), style = stroke)
                drawLine(tint, Offset(cx, cy + 6.dp.toPx()), Offset(cx, cy + 8.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(cx - 4.dp.toPx(), cy + 8.dp.toPx()), Offset(cx + 4.dp.toPx(), cy + 8.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AssistantGlyph.Profile -> {
                drawCircle(tint, radius = 4.dp.toPx(), center = Offset(cx, cy - 4.dp.toPx()), style = stroke)
                drawArc(tint, 205f, 130f, false, Offset(cx - 8.dp.toPx(), cy), Size(16.dp.toPx(), 12.dp.toPx()), style = stroke)
            }
            AssistantGlyph.Send -> {
                val path = Path().apply {
                    moveTo(cx - 8.dp.toPx(), cy - 7.dp.toPx())
                    lineTo(cx + 9.dp.toPx(), cy)
                    lineTo(cx - 8.dp.toPx(), cy + 7.dp.toPx())
                    lineTo(cx - 4.dp.toPx(), cy)
                    close()
                }
                drawPath(path, tint)
            }
            AssistantGlyph.Stop -> {
                drawRoundRect(tint, Offset(cx - 6.dp.toPx(), cy - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantIconPreview() {
    OpenPhoneTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistantGlyph.entries.forEach { glyph ->
                AssistantIcon(glyph = glyph, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
