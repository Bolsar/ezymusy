package com.ezymusy.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

private object Palette {
    val Ink = Color(0xFF0B0B0C)
    val Surface = Color(0xFF141416)
    val SurfaceHigh = Color(0xFF1C1C1F)
    val Line = Color(0xFF2A2A2E)
    val Text = Color(0xFFEDEDED)
    val TextMuted = Color(0xFF9A9AA0)
    val Lime = Color(0xFFC6F432)
    val Red = Color(0xFFFF6B6B)
}

private val colors = darkColorScheme(
    primary = Palette.Lime,
    onPrimary = Palette.Ink,
    // Every role is mapped so no Material default (purple) leaks into components.
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.Lime,
    secondary = Palette.TextMuted,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.SurfaceHigh,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Lime,
    onTertiary = Palette.Ink,
    tertiaryContainer = Palette.SurfaceHigh,
    onTertiaryContainer = Palette.Lime,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerHighest = Palette.Line,
    surfaceBright = Palette.SurfaceHigh,
    surfaceDim = Palette.Ink,
    surfaceTint = Palette.Lime,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.Ink,
    errorContainer = Palette.SurfaceHigh,
    onErrorContainer = Palette.Red,
    scrim = Palette.Ink,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Ink,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Red,
    onError = Palette.Ink,
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600))

private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = SpaceGrotesk,
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

/** Dark minimal: the app ships one dark theme on purpose (DESIGN.md). */
@Composable
fun EzymusyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
