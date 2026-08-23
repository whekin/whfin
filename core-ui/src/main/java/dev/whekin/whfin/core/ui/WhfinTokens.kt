package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
    /** Width of the rule that marks the dock destination being read. */
    val dockRule: Dp = 22.dp,
    /** Mirrors `MaterialTheme.shapes.medium`; kept as a number so a press can animate it. */
    val buttonCorner: Dp = 14.dp,
    /** Mirrors `MaterialTheme.shapes.small`. */
    val pillCorner: Dp = 10.dp,
)

/**
 * Motion is borrowed from the platform rather than invented here.
 *
 * Every spec comes from the theme's Material 3 expressive [androidx.compose.material3.MotionScheme],
 * so an interrupted movement — a second tap, a Back gesture abandoned half-way — continues from its
 * current velocity instead of restarting a fixed curve, and WHFIN's own transitions cannot drift
 * away from what its Material components do.
 */
object WhfinMotion {
    /** Colour, alpha and other non-spatial changes that should land immediately. */
    @Composable
    @ReadOnlyComposable
    fun <T> quick(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    /** Anything that physically moves or resizes. */
    @Composable
    @ReadOnlyComposable
    fun <T> standard(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Whole-screen travel. */
    @Composable
    @ReadOnlyComposable
    fun <T> screen(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    /**
     * Movement measured in whole pixels.
     *
     * This one spring is written out rather than taken from the scheme: a spec carries its own
     * visibility threshold, and the scheme's spatial spec uses the default fractional one. On an
     * `IntOffset` that means the animation keeps resolving fractions of a pixel nobody can see, and
     * the layout keeps recomposing for them. The numbers are the expressive spatial token's own.
     */
    fun travel(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = .8f,
        stiffness = 380f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /**
     * Sibling panes (the two dock destinations) trade places instead of pushing each other:
     * the outgoing pane clears first, so two opaque ledgers never blend into a muddy frame.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> paneExit(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> paneEnter(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()
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
