package sample

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal data class AppThemePalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val background: Color,
    val cardSurface: Color,
    val insetSurface: Color,
)

internal fun AppThemePalette.toColorScheme(): ColorScheme = lightColorScheme(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    surface = surface,
    background = background,
)
