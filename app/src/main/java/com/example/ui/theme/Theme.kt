package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CivicNavy,
    onPrimary = Color.White,
    primaryContainer = CivicNavyContainer,
    onPrimaryContainer = CivicNavy,
    secondary = CivicTeal,
    onSecondary = Color.White,
    secondaryContainer = CivicTealContainer,
    onSecondaryContainer = CivicTealOnContainer,
    tertiary = HazardAmber,
    onTertiary = Color.White,
    tertiaryContainer = HazardAmberContainer,
    onTertiaryContainer = HazardAmberOnContainer,
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = AlertCrimsonOnContainer,
    background = SurfaceCanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE6ECF2)
)

private val DarkColorScheme = darkColorScheme(
    primary = CivicTealBright,
    onPrimary = Color(0xFF042622),
    primaryContainer = Color(0xFF14385A),
    onPrimaryContainer = Color(0xFFD6E4F3),
    secondary = CivicTealBright,
    onSecondary = Color(0xFF042622),
    secondaryContainer = Color(0xFF0F4C46),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = HazardAmberBright,
    onTertiary = Color(0xFF3E1C00),
    tertiaryContainer = Color(0xFF5C2D05),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = SurfaceCanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF1F354C)
)

val CivicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CivicLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CivicTypography,
        shapes = CivicShapes,
        content = content
    )
}
