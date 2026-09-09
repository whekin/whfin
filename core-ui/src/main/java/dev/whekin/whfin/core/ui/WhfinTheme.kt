package dev.whekin.whfin.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = Color(0xFFEFF5F8)
private val Ink = Color(0xFF193442)
private val Bottle = Color(0xFF176C70)
private val Sage = Color(0xFFB5D7D9)
private val Clay = Color(0xFFAA4834)
private val Oxide = Color(0xFFAB343C)
private val Warning = Color(0xFF806000)

val WhfinLightColorScheme = lightColorScheme(
    primary = Bottle,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBEC),
    onPrimaryContainer = Color(0xFF103D42),
    secondary = Color(0xFF506D79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEAF0),
    onSecondaryContainer = Color(0xFF223E4C),
    tertiary = Clay,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0D5),
    onTertiaryContainer = Color(0xFF54291F),
    error = Oxide,
    onError = Color.White,
    errorContainer = Color(0xFFFFE0E0),
    onErrorContainer = Color(0xFF570B1C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDDE9EF),
    onSurfaceVariant = Color(0xFF516874),
    surfaceContainerLowest = Color(0xFFFAFDFE),
    surfaceContainerLow = Color(0xFFE7F0F4),
    surfaceContainer = Color(0xFFE0EBF0),
    surfaceContainerHigh = Color(0xFFD9E6EC),
    surfaceContainerHighest = Color(0xFFCFDFE6),
    outline = Color(0xFF718994),
    outlineVariant = Color(0xFFC6D8E1),
)

val WhfinDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8ED5D5),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF155054),
    onPrimaryContainer = Color(0xFFC5F1F0),
    secondary = Color(0xFFB6CDD8),
    onSecondary = Color(0xFF233B46),
    secondaryContainer = Color(0xFF304C59),
    onSecondaryContainer = Color(0xFFDCECF3),
    tertiary = Color(0xFFFFB39D),
    onTertiary = Color(0xFF54271C),
    tertiaryContainer = Color(0xFF70392A),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF650008),
    errorContainer = Color(0xFF85221C),
    onErrorContainer = Color(0xFFFFDAD4),
    // Blue-black canvas with teal and peach accents; retain contrast independently of light mode.
    background = Color(0xFF101F29),
    onBackground = Color(0xFFE5F0F5),
    surface = Color(0xFF101F29),
    onSurface = Color(0xFFE5F0F5),
    surfaceVariant = Color(0xFF30444F),
    onSurfaceVariant = Color(0xFFB6C9D3),
    surfaceContainerLowest = Color(0xFF0A1821),
    surfaceContainerLow = Color(0xFF152833),
    surfaceContainer = Color(0xFF1B303B),
    surfaceContainerHigh = Color(0xFF243B47),
    surfaceContainerHighest = Color(0xFF2E4753),
    outline = Color(0xFF8CA6B3),
    outlineVariant = Color(0xFF3C5663),
)

private val WhfinShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val WhfinSerif = FontFamily(
    Font(R.font.noto_serif, weight = FontWeight.Normal),
)
private val SystemFont = FontFamily.Default
private val Sans = FontFamily.SansSerif
private fun whfinTypography(editorialFont: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = editorialFont, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = editorialFont, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-.7).sp, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontFamily = editorialFont, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 37.sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = editorialFont, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-.35).sp),
    headlineMedium = TextStyle(fontFamily = editorialFont, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
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
private val WhfinTypography = whfinTypography(WhfinSerif)
private val SystemTypography = whfinTypography(SystemFont)

@Composable
fun WhfinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme? = null,
    useSystemFont: Boolean = false,
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
            warning = Color(0xFF806000),
            rule = scheme.outlineVariant,
            positive = scheme.primary,
            pending = scheme.tertiary,
        )
    } else if (darkTheme) {
        WhfinExtendedColors(
            paper = scheme.background,
            ink = scheme.onSurface,
            bottle = scheme.primary,
            sage = Color(0xFF709DA4),
            clay = scheme.tertiary,
            oxide = scheme.error,
            warning = Color(0xFFF0C45C),
            rule = scheme.outlineVariant,
            positive = scheme.primary,
            pending = scheme.tertiary,
        )
    } else {
        WhfinExtendedColors(Paper, Ink, Bottle, Sage, Clay, Oxide, Warning, scheme.outlineVariant, Bottle, Clay)
    }
    CompositionLocalProvider(
        LocalWhfinColors provides extended,
        LocalWhfinSpacing provides WhfinSpacing(),
        LocalWhfinSizes provides WhfinSizes(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            // Springs, not durations: every Material component in the app settles with the same
            // physics Android 16 itself animates with, and WhfinMotion hands the same scheme to
            // WHFIN's own transitions.
            motionScheme = MotionScheme.expressive(),
            typography = if (useSystemFont) SystemTypography else WhfinTypography,
            shapes = WhfinShapes,
            content = content,
        )
    }
}
