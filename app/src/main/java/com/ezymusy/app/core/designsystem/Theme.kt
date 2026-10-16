package com.ezymusy.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezymusy.app.R

// Tokens live only in this package. See DESIGN.md. Feature code never uses raw colors or sizes.

private class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
)

private val Dark = Palette(
    bg = Color(0xFF0B0B0C),
    surface = Color(0xFF141416),
    surfaceHigh = Color(0xFF1C1C1F),
    line = Color(0xFF2A2A2E),
    text = Color(0xFFEDEDED),
    textMuted = Color(0xFF9A9AA0),
    accent = Color(0xFFC6F432),
    onAccent = Color(0xFF0B0B0C),
    error = Color(0xFFFF6B6B),
)

// Lime fails contrast on white (≈1.3:1), so light mode uses a deep olive of the same hue for the accent.
private val Light = Palette(
    bg = Color(0xFFFAFAF7),
    surface = Color(0xFFF0F0EC),
    surfaceHigh = Color(0xFFE6E6E1),
    line = Color(0xFFD6D6D0),
    text = Color(0xFF111113),
    textMuted = Color(0xFF5C5C63),
    accent = Color(0xFF4A6800),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
)

// Every role is mapped so no Material default (purple) leaks into components.
private fun Palette.scheme(base: ColorScheme) = base.copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = surfaceHigh,
    onPrimaryContainer = accent,
    secondary = textMuted,
    onSecondary = bg,
    secondaryContainer = surfaceHigh,
    onSecondaryContainer = text,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = surfaceHigh,
    onTertiaryContainer = accent,
    surfaceContainerLowest = bg,
    surfaceContainerLow = surface,
    surfaceContainerHighest = line,
    surfaceBright = surfaceHigh,
    surfaceDim = bg,
    surfaceTint = accent,
    inverseSurface = text,
    inverseOnSurface = bg,
    inversePrimary = bg,
    errorContainer = surfaceHigh,
    onErrorContainer = error,
    scrim = bg,
    background = bg,
    onBackground = text,
    surface = bg,
    onSurface = text,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = textMuted,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHigh,
    outline = line,
    outlineVariant = line,
    error = error,
    onError = bg,
)

private val darkColors = Dark.scheme(darkColorScheme())
private val lightColors = Light.scheme(lightColorScheme())

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Inter = FontFamily(inter(400), inter(500), inter(600))

private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

private val typography = Typography(
    headlineSmall = style(24, 30, 600),
    titleLarge = style(20, 26, 600),
    titleMedium = style(16, 22, 500),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 20, 400),
    labelLarge = style(14, 20, 500),
    labelMedium = style(12, 16, 500),
)

private val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
)

/** Spacing scale (4dp grid) and fixed sizes. */
object Dimens {
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp
    val TouchTarget = 48.dp
    val PlayButton = 64.dp

    /** Blur radius of the cover behind Now Playing. */
    val ArtworkBlur = 48.dp

    /** Material's opacity for disabled content. */
    const val DisabledAlpha = 0.38f
}

/** Dark minimal, with a matching light theme; follows the system setting (DESIGN.md). */
@Composable
fun EzymusyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColors else lightColors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
