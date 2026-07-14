package dev.whekin.whfin.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = Color(0xFFF6F0E4)
private val Ink = Color(0xFF162019)
private val Bottle = Color(0xFF244C39)
private val Sage = Color(0xFFB9C9AE)
private val Clay = Color(0xFFC86243)
private val Oxide = Color(0xFF9E3F32)

val WhfinLightColorScheme = lightColorScheme(
    primary = Bottle,
    onPrimary = Color(0xFFFFFBF3),
    primaryContainer = Color(0xFFDCE8D5),
    onPrimaryContainer = Color(0xFF10291B),
    secondary = Color(0xFF617261),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EBDD),
    onSecondaryContainer = Color(0xFF20291E),
    tertiary = Clay,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4D9CC),
    onTertiaryContainer = Color(0xFF4B190D),
    error = Oxide,
    onError = Color.White,
    errorContainer = Color(0xFFF4D9D5),
    onErrorContainer = Color(0xFF42110C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE3D7),
    onSurfaceVariant = Color(0xFF5C635B),
    surfaceContainerLowest = Color(0xFFFFFBF4),
    surfaceContainerLow = Color(0xFFF2EBDF),
    surfaceContainer = Color(0xFFEDE6DA),
    surfaceContainerHigh = Color(0xFFE6DED2),
    surfaceContainerHighest = Color(0xFFDDD5C9),
    outline = Color(0xFF7A8379),
    outlineVariant = Color(0xFFD1C9BC),
)

val WhfinDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC2D9B8),
    onPrimary = Color(0xFF112719),
    primaryContainer = Color(0xFF2B4A37),
    onPrimaryContainer = Color(0xFFE1F1DC),
    secondary = Color(0xFFC2CDBD),
    onSecondary = Color(0xFF273126),
    secondaryContainer = Color(0xFF343D33),
    onSecondaryContainer = Color(0xFFE2EBDE),
    tertiary = Color(0xFFF0A080),
    onTertiary = Color(0xFF4C1A0C),
    tertiaryContainer = Color(0xFF653120),
    onTertiaryContainer = Color(0xFFFFDBCC),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF650008),
    errorContainer = Color(0xFF85221C),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF101612),
    onBackground = Color(0xFFF2ECDF),
    surface = Color(0xFF101612),
    onSurface = Color(0xFFF2ECDF),
    surfaceVariant = Color(0xFF343A34),
    onSurfaceVariant = Color(0xFFBEC5BA),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF161D18),
    surfaceContainer = Color(0xFF1A211C),
    surfaceContainerHigh = Color(0xFF242C26),
    surfaceContainerHighest = Color(0xFF2E3730),
    outline = Color(0xFF929B91),
    outlineVariant = Color(0xFF3C463E),
)

private val WhfinShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif
private val WhfinTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-.7).sp, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 37.sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-.35).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = .25.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = .2.sp),
)

@Composable
fun WhfinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val scheme = colorScheme ?: if (darkTheme) WhfinDarkColorScheme else WhfinLightColorScheme
    val extended = if (colorScheme != null) {
        WhfinExtendedColors(
            paper = scheme.background,
            ink = scheme.onSurface,
            bottle = scheme.primary,
            sage = scheme.secondary,
            clay = scheme.tertiary,
            oxide = scheme.error,
            rule = scheme.outlineVariant,
            positive = scheme.primary,
            pending = scheme.tertiary,
        )
    } else if (darkTheme) {
        WhfinExtendedColors(
            paper = scheme.background,
            ink = scheme.onSurface,
            bottle = scheme.primary,
            sage = Color(0xFF78906F),
            clay = scheme.tertiary,
            oxide = scheme.error,
            rule = scheme.outlineVariant,
            positive = scheme.primary,
            pending = scheme.tertiary,
        )
    } else {
        WhfinExtendedColors(Paper, Ink, Bottle, Sage, Clay, Oxide, scheme.outlineVariant, Bottle, Clay)
    }
    CompositionLocalProvider(
        LocalWhfinColors provides extended,
        LocalWhfinSpacing provides WhfinSpacing(),
        LocalWhfinSizes provides WhfinSizes(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WhfinTypography,
            shapes = WhfinShapes,
            content = content,
        )
    }
}
