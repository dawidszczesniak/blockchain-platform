package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val IntelliJDarkColors = darkColorScheme(
    primary = Color(0xFFCF8E6D),
    onPrimary = Color(0xFF1E1F22),
    primaryContainer = Color(0xFF4D3527),
    onPrimaryContainer = Color(0xFFFFD8C5),
    secondary = Color(0xFF7A7E85),
    onSecondary = Color(0xFFF4F4F5),
    secondaryContainer = Color(0xFF313335),
    onSecondaryContainer = Color(0xFFD7D9DD),
    tertiary = Color(0xFFCF8E6D),
    onTertiary = Color(0xFF1E1F22),
    tertiaryContainer = Color(0xFF4D3527),
    onTertiaryContainer = Color(0xFFFFD8C5),
    background = Color(0xFF1E1F22),
    onBackground = Color(0xFFD7D9DD),
    surface = Color(0xFF2B2D30),
    onSurface = Color(0xFFD7D9DD),
    surfaceVariant = Color(0xFF313335),
    onSurfaceVariant = Color(0xFF9DA0A8),
    outline = Color(0xFF43454A),
    outlineVariant = Color(0xFF3A3C41),
    error = Color(0xFFFF6B68),
    onError = Color(0xFF1E1F22),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

private val IntelliJTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    ProvideAppScrollbarStyle {
        MaterialTheme(
            colorScheme = IntelliJDarkColors,
            typography = IntelliJTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
