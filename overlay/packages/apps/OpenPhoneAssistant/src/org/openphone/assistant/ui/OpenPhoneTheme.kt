package org.openphone.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OpenPhoneColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF071C3D),
    secondary = Color(0xFF0E7C66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2EA),
    onSecondaryContainer = Color(0xFF05231D),
    tertiary = Color(0xFF8B4EC6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0DFFF),
    onTertiaryContainer = Color(0xFF2D0A4A),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF181B20),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF404756),
    outline = Color(0xFF768094),
    background = Color(0xFFF8F5EF),
    onBackground = Color(0xFF181B20),
)

private val OpenPhoneShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun OpenPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenPhoneColors,
        typography = MaterialTheme.typography,
        shapes = OpenPhoneShapes,
        content = content,
    )
}
