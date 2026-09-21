package dev.whekin.whfin.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Shared scale: solid actual, faint projection and a separate recorded-average tick. */
@Composable
fun WhfinPaceTrack(actual: Long, projected: Long, baseline: Long, modifier: Modifier = Modifier) {
    val scale = maxOf(actual, projected, baseline, 1L).toDouble()
    val ink = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val reference = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(24.dp).clearAndSetSemantics { }) {
        if (size.width < 2.dp.toPx()) return@Canvas
        val h = 8.dp.toPx()
        val y = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), radius)
        drawRoundRect(ink.copy(alpha = .23f), Offset(0f, y), Size(size.width * (projected.coerceAtLeast(0) / scale).toFloat(), h), radius)
        drawRoundRect(ink, Offset(0f, y), Size(size.width * (actual.coerceAtLeast(0) / scale).toFloat(), h), radius)
        if (baseline > 0) {
            val x = (size.width * (baseline / scale).toFloat()).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(reference, Offset(x, y - 3.dp.toPx()), Offset(x, y + h + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
