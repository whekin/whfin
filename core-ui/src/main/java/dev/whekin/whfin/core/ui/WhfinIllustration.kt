package dev.whekin.whfin.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Decorative compositions, never a representation of balances, coverage or sync success. */
enum class WhfinIllustrationScene { Gather, Sort, Balance }

/**
 * A small family of cut-out forms. Every pigment comes from the active Material palette, including
 * wallpaper colours. One finite Compose animation respects the system duration scale; theme changes
 * and ordinary recomposition recolour the drawing without replaying it. No bitmap or frame loop.
 */
@Composable
fun WhfinIllustration(
    scene: WhfinIllustrationScene,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val inspection = LocalInspectionMode.current
    var appeared by rememberSaveable(scene) { mutableStateOf(false) }
    val arrival = remember(scene) { Animatable(if (appeared || !animate || inspection) 1f else 0f) }
    val motion = WhfinMotion.screen<Float>()
    LaunchedEffect(scene, animate) {
        appeared = true
        if (animate && !inspection) arrival.animateTo(1f, motion) else arrival.snapTo(1f)
    }
    Canvas(modifier.fillMaxWidth().height(144.dp).clearAndSetSemantics { }) {
        val unit = minOf(size.width / 320f, size.height / 180f)
        val drift = 1f - arrival.value
        translate((size.width - 320f * unit) / 2f, (size.height - 180f * unit) / 2f) {
            scale(unit, pivot = Offset.Zero) {
                when (scene) {
                    WhfinIllustrationScene.Gather -> {
                        drawCircle(colors.secondaryContainer, 68f, Offset(163f, 91f))
                        piece(94f, 99f, -12f, drift, -18f, 12f) {
                            arch(colors.primary, colors.onPrimary)
                        }
                        piece(184f, 112f, 12f, drift, 16f, 18f) {
                            capsule(colors.tertiaryContainer, colors.onTertiaryContainer)
                        }
                        piece(208f, 47f, 0f, drift, 20f, -16f) {
                            drawCircle(colors.tertiary, 23f, Offset.Zero)
                            drawCircle(colors.onTertiary, 4f, Offset(0f, -4f))
                        }
                        piece(53f, 40f, -12f, drift, -12f, -14f) { petal(colors.secondary) }
                        drawCircle(colors.primary, 4f, Offset(255f + drift * 12f, 133f))
                        sparkle(Offset(268f, 69f), colors.tertiary, 8f)
                    }
                    WhfinIllustrationScene.Sort -> {
                        // Three open shelves: grouping by shape as well as colour.
                        for (index in 0..2) {
                            drawRoundRect(colors.surfaceContainerHighest,
                                Offset(33f + index * 91f, 140f), Size(72f, 5f), CornerRadius(2.5f))
                        }
                        piece(69f, 108f, 0f, drift, -15f, -12f) { drawCircle(colors.primary, 25f, Offset.Zero) }
                        piece(69f, 55f, 0f, drift, -24f, -18f) { drawCircle(colors.primaryContainer, 20f, Offset.Zero) }
                        piece(160f, 115f, 0f, drift, 4f, -22f) {
                            drawRoundRect(colors.tertiary, Offset(-27f, -18f), Size(54f, 36f), CornerRadius(18f))
                        }
                        piece(160f, 69f, -12f * drift, drift, 12f, -12f) {
                            drawRoundRect(colors.tertiaryContainer, Offset(-27f, -18f), Size(54f, 36f), CornerRadius(18f))
                        }
                        piece(251f, 112f, -8f * drift, drift, 22f, -8f) { petal(colors.secondary) }
                        piece(251f, 63f, 90f + 12f * drift, drift, 14f, -20f) { petal(colors.secondaryContainer) }
                        sparkle(Offset(205f, 29f), colors.tertiary, 7f)
                    }
                    WhfinIllustrationScene.Balance -> {
                        drawCircle(colors.secondaryContainer, 65f, Offset(160f, 96f))
                        piece(160f, 128f, 0f, drift, 0f, 18f) { arch(colors.primary, colors.onPrimary) }
                        piece(160f, 75f, -7f * drift, drift, -24f, -5f) {
                            capsule(colors.tertiaryContainer, colors.onTertiaryContainer)
                        }
                        piece(173f, 33f, 0f, drift, 18f, -15f) { drawCircle(colors.tertiary, 23f, Offset.Zero) }
                        piece(61f, 113f, -25f, drift, -15f, 8f) { petal(colors.secondary) }
                        sparkle(Offset(251f, 50f), colors.tertiary, 10f)
                        sparkle(Offset(274f, 112f), colors.primary, 6f)
                        drawCircle(colors.secondary, 3f, Offset(78f, 51f))
                    }
                }
            }
        }
    }
}

private inline fun DrawScope.piece(
    x: Float, y: Float, angle: Float, drift: Float, dx: Float, dy: Float,
    crossinline draw: DrawScope.() -> Unit,
) = translate(x + drift * dx, y + drift * dy) { rotate(angle, Offset.Zero) { draw() } }

private fun DrawScope.arch(fill: Color, detail: Color) {
    val arch = Path().apply {
        moveTo(-42f, 34f); lineTo(-42f, -4f)
        cubicTo(-42f, -57f, 42f, -57f, 42f, -4f)
        lineTo(42f, 34f); lineTo(17f, 34f); lineTo(17f, -4f)
        cubicTo(17f, -25f, -17f, -25f, -17f, -4f)
        lineTo(-17f, 34f); close()
    }
    drawPath(arch, fill)
    drawLine(detail, Offset(-32f, 20f), Offset(-25f, 20f), 2f)
    drawLine(detail, Offset(-32f, 26f), Offset(-25f, 26f), 2f)
}

private fun DrawScope.capsule(fill: Color, detail: Color) {
    drawRoundRect(fill, Offset(-52f, -17f), Size(104f, 34f), CornerRadius(17f))
    drawCircle(detail, 6f, Offset(32f, 0f), style = Stroke(2f))
    drawLine(detail, Offset(-33f, 0f), Offset(-7f, 0f), 2f)
}

private fun DrawScope.petal(fill: Color) {
    drawPath(Path().apply {
        moveTo(-22f, 20f); cubicTo(-28f, -9f, -7f, -29f, 22f, -20f)
        cubicTo(28f, 9f, 7f, 29f, -22f, 20f); close()
    }, fill)
}

private fun DrawScope.sparkle(center: Offset, fill: Color, radius: Float) {
    translate(center.x, center.y) {
        drawPath(Path().apply {
            moveTo(0f, -radius)
            quadraticTo(1f, -1f, radius, 0f); quadraticTo(1f, 1f, 0f, radius)
            quadraticTo(-1f, 1f, -radius, 0f); quadraticTo(-1f, -1f, 0f, -radius)
            close()
        }, fill)
    }
}
