package dev.whekin.whfin.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The caller mounts this only after a successful persisted action, and owns its readable lifetime. */
@Composable
fun WhfinAcknowledgement(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    var check by remember { mutableStateOf(true) }
    val fade = WhfinMotion.quick<Float>()
    LaunchedEffect(Unit) { delay(1200); check = false }
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedContent(check, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "saved-category") {
            Icon(if (it) Icons.Default.Check else icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A single trip from the upper source selector to the lower destination selector; never a transfer status. */
@Composable
fun WhfinTransferDirection(replayKey: Any, modifier: Modifier = Modifier) {
    val progress = remember(replayKey) { Animatable(0f) }
    val motion = WhfinMotion.screen<Float>()
    LaunchedEffect(replayKey) { progress.animateTo(1f, motion) }
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.width(28.dp).height(48.dp).clearAndSetSemantics { }) {
        val x = size.width / 2
        val top = 7.dp.toPx(); val bottom = size.height - top
        drawLine(colors.outlineVariant, Offset(x, top), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
        drawLine(colors.primary, Offset(x - 4.dp.toPx(), bottom - 4.dp.toPx()), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
        drawLine(colors.primary, Offset(x + 4.dp.toPx(), bottom - 4.dp.toPx()), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(colors.primary, 3.dp.toPx(), Offset(x, top + (bottom - top) * progress.value.coerceIn(0f, 1f)))
    }
}

/** An optional, finite long-press scene, independent of developer-mode gestures. All pigments are theme roles. */
@Composable
fun WhfinCuriousCat(description: String, longClickLabel: String, modifier: Modifier = Modifier, activeDescription: String? = null) {
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val peek = remember { Animatable(if (inspection) 1f else 0f) }
    val paw = remember { Animatable(if (inspection) 1f else 0f) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val motion = WhfinMotion.screen<Float>()
    val colors = MaterialTheme.colorScheme
    val play = {
        if (!playing) {
            playing = true
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            scope.launch {
                try {
                    peek.animateTo(1f, motion)
                    paw.animateTo(1f, motion)
                    delay(1200)
                    paw.animateTo(0f, motion)
                    peek.animateTo(0f, motion)
                } finally { playing = false }
            }
        }
        Unit
    }
    val currentPlay by rememberUpdatedState(play)
    Canvas(modifier.fillMaxWidth().height(128.dp).clipToBounds()
        .pointerInput(Unit) { detectTapGestures(onLongPress = { currentPlay() }) }
        .semantics {
            contentDescription = description
            if (playing && activeDescription != null) { stateDescription = activeDescription; liveRegion = LiveRegionMode.Polite }
            onLongClick(longClickLabel) { play(); true }
        }) {
        val unit = minOf(size.width / 320f, size.height / 140f)
        translate((size.width - 320 * unit) / 2, (size.height - 140 * unit) / 2) {
            scale(unit, pivot = Offset.Zero) {
                drawCircle(colors.secondaryContainer, 52f, Offset(156f, 76f))
                clipRect(70f, 0f, 240f, 103f) {
                    translate(0f, (1f - peek.value.coerceIn(0f, 1f)) * 100f) {
                        val head = Path().apply {
                            moveTo(122f, 95f); lineTo(117f, 37f); lineTo(143f, 53f)
                            quadraticTo(160f, 47f, 177f, 53f); lineTo(201f, 35f); lineTo(199f, 94f)
                            quadraticTo(160f, 117f, 122f, 95f); close()
                        }
                        drawPath(head, colors.onSurface)
                        drawCircle(colors.surface, 3f, Offset(145f, 73f)); drawCircle(colors.surface, 3f, Offset(176f, 73f))
                        drawCircle(colors.tertiary, 2.5f, Offset(161f, 85f))
                        drawLine(colors.surface, Offset(127f, 83f), Offset(145f, 86f), 1.5f)
                        drawLine(colors.surface, Offset(177f, 86f), Offset(196f, 82f), 1.5f)
                    }
                }
                // The sculpture masks the entrance; the paw then pushes its small satellite.
                drawRoundRect(colors.primaryContainer, Offset(94f, 101f), Size(130f, 25f), CornerRadius(12f))
                drawCircle(colors.tertiary, 14f, Offset(237f + paw.value * 17f, 99f + paw.value * 8f))
                if (peek.value > 0f) {
                    val reach = paw.value.coerceIn(0f, 1f)
                    drawLine(colors.onSurface.copy(alpha = peek.value.coerceIn(0f, 1f)), Offset(185f, 98f), Offset(198f + reach * 28f, 104f), 12f, StrokeCap.Round)
                    repeat(2) { drawLine(colors.surface, Offset(197f + reach * 28f, 102f + it * 4f), Offset(201f + reach * 28f, 102f + it * 4f), 1f) }
                }
                drawCircle(colors.secondary, 5f, Offset(79f, 74f))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Delight light", widthDp = 400, heightDp = 260)
@androidx.compose.ui.tooling.preview.Preview(name = "Delight dark large", widthDp = 360, heightDp = 300,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun DelightPreview() = WhfinTheme {
    androidx.compose.material3.Surface {
        Column(Modifier.padding(20.dp)) {
            WhfinCuriousCat("WHFIN illustration", "Call cat")
            WhfinAcknowledgement(Icons.Default.Check, "Category remembered. Next time, automatically.")
            WhfinTransferDirection("preview")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Empty illustrations light", widthDp = 400, heightDp = 330)
@androidx.compose.ui.tooling.preview.Preview(name = "Empty illustrations dark compact", widthDp = 320, heightDp = 330,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun EmptyIllustrationsPreview() = WhfinTheme {
    androidx.compose.material3.Surface {
        Column {
            listOf(WhfinIllustrationScene.History, WhfinIllustrationScene.Debts, WhfinIllustrationScene.Savings).forEach {
                WhfinIllustration(it, Modifier.height(110.dp), animate = false)
            }
        }
    }
}
