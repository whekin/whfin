package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Immutable
data class WhfinExtendedColors(
    val paper: Color,
    val ink: Color,
    val bottle: Color,
    val sage: Color,
    val clay: Color,
    val oxide: Color,
    /** A legible amber used for caution before a state becomes an error. */
    val warning: Color,
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
    val dockIcon: Dp = 22.dp,
    val dockCenterSlot: Dp = 64.dp,
    val ledgerMarker: Dp = 3.dp,
    /** Mirrors `MaterialTheme.shapes.medium`; kept as a number so a press can animate it. */
    val buttonCorner: Dp = 14.dp,
    /** Mirrors `MaterialTheme.shapes.small`. */
    val pillCorner: Dp = 10.dp,
)

/**
 * Motion is borrowed from the platform rather than invented here.
 *
 * These are the Material 3 expressive spring tokens Android 16 itself animates with, written out
 * as constants because the springs are public API only in an alpha of Material 3 while the values
 * are stable. Springs, not durations: an interrupted movement — a second tap, a Back gesture
 * abandoned half-way — continues from its current velocity instead of restarting a fixed curve.
 */
object WhfinMotion {
    private const val SPATIAL_DAMPING = .8f
    private const val SPATIAL_STIFFNESS = 380f
    private const val SLOW_SPATIAL_STIFFNESS = 200f
    private const val EFFECTS_DAMPING = 1f
    private const val EFFECTS_STIFFNESS = 1600f
    private const val FAST_EFFECTS_STIFFNESS = 3800f

    /** Colour, alpha and other non-spatial changes that should land immediately. */
    fun <T> quick(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = FAST_EFFECTS_STIFFNESS)

    /** Anything that physically moves or resizes. */
    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = SPATIAL_STIFFNESS)

    /** Whole-screen travel. */
    fun <T> screen(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = SLOW_SPATIAL_STIFFNESS)

    /**
     * Movement measured in pixels stops at the pixel: without this threshold a spring keeps
     * resolving fractions of a pixel nobody can see, and the layout keeps recomposing for them.
     */
    fun travel(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = SPATIAL_DAMPING,
        stiffness = SPATIAL_STIFFNESS,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /**
     * Sibling panes (the two dock destinations) trade places instead of pushing each other:
     * the outgoing pane clears first, so two opaque ledgers never blend into a muddy frame.
     */
    fun <T> paneExit(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = FAST_EFFECTS_STIFFNESS)

    fun <T> paneEnter(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = EFFECTS_STIFFNESS)
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
