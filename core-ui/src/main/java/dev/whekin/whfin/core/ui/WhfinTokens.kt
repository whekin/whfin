package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class WhfinExtendedColors(
    val paper: Color,
    val ink: Color,
    val bottle: Color,
    val sage: Color,
    val clay: Color,
    val oxide: Color,
    val rule: Color,
    val positive: Color,
    val pending: Color,
)

@Immutable
data class WhfinSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val rail: Dp = 20.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
)

@Immutable
data class WhfinSizes(
    val minTouchTarget: Dp = 48.dp,
    val icon: Dp = 20.dp,
    val prominentIcon: Dp = 26.dp,
    val iconContainer: Dp = 40.dp,
    val buttonHeight: Dp = 52.dp,
    val dockHeight: Dp = 64.dp,
    val ledgerMarker: Dp = 3.dp,
)

object WhfinMotion {
    private val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    fun <T> quick() = tween<T>(durationMillis = 140, easing = easing)
    fun <T> standard() = tween<T>(durationMillis = 220, easing = easing)
    fun <T> screen() = tween<T>(durationMillis = 280, easing = easing)
}

/** Quiet, action-oriented feedback that respects the device's system haptic setting. */
object WhfinHaptics {
    val navigation: HapticFeedbackType = HapticFeedbackType.SegmentTick
    fun toggle(enabled: Boolean): HapticFeedbackType =
        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
}

val LocalWhfinColors = staticCompositionLocalOf<WhfinExtendedColors> {
    error("WhfinTheme is missing")
}
val LocalWhfinSpacing = staticCompositionLocalOf { WhfinSpacing() }
val LocalWhfinSizes = staticCompositionLocalOf { WhfinSizes() }

object WhfinThemeTokens {
    val colors: WhfinExtendedColors
        @androidx.compose.runtime.Composable get() = LocalWhfinColors.current
    val spacing: WhfinSpacing
        @androidx.compose.runtime.Composable get() = LocalWhfinSpacing.current
    val sizes: WhfinSizes
        @androidx.compose.runtime.Composable get() = LocalWhfinSizes.current
}
