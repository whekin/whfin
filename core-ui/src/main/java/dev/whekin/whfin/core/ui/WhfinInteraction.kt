package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * A control that answers the finger by changing shape, the way Android 16's own controls do.
 *
 * A press is the one moment a flat ledger surface has to prove it is a control at all. Colour
 * alone cannot do it here — the palette is quiet by design and a pressed tint reads as noise — so
 * the corner tightens instead and springs back when the finger leaves.
 */
@Composable
fun rememberWhfinPressShape(
    interactionSource: InteractionSource,
    corner: Dp,
    pressedCorner: Dp = corner * .4f,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) pressedCorner else corner,
        animationSpec = WhfinMotion.standard(),
        label = "press shape",
    )
    return RoundedCornerShape(radius)
}
