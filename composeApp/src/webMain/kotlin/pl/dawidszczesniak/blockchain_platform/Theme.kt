package pl.dawidszczesniak.blockchain_platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF42D0FF),
    onPrimary = Color(0xFF03121D),
    primaryContainer = Color(0xFF0B2A47),
    onPrimaryContainer = Color(0xFFC7F0FF),
    secondary = Color(0xFF8BE8FF),
    onSecondary = Color(0xFF04151D),
    secondaryContainer = Color(0xFF0E3148),
    onSecondaryContainer = Color(0xFFC0F3FF),
    tertiary = Color(0xFF7FB5FF),
    onTertiary = Color(0xFF0B1730),
    tertiaryContainer = Color(0xFF102D4F),
    onTertiaryContainer = Color(0xFFCFE2FF),
    background = Color(0xFF061629),
    onBackground = Color(0xFFE6F3FF),
    surface = Color(0xFF0A1D35),
    onSurface = Color(0xFFE6F3FF),
    surfaceVariant = Color(0xFF102744),
    onSurfaceVariant = Color(0xFF9CB4D6),
    outline = Color(0xFF1E3C5E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2D0B0B),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        shapes = AppShapes,
        content = content,
    )
}
