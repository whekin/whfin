package dev.whekin.whfin.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A point worth naming on the runway: where the money ends, when the next money is due. */
@Immutable
data class WhfinTimelineMark(
    /** Position along the window, 0 at its start and 1 at its end. */
    val fraction: Float,
    val label: String,
    /** Marks are placed in the order given; a later one is dropped when it would collide. */
    val emphasis: Boolean = false,
)

/**
 * How far the money reaches, drawn instead of described.
 *
 * "Does what I have last until I am paid?" is a question about time, and the card answered it with a
 * sentence of four facts joined by separators — three lines on a phone, eight in Russian at a large
 * font. One rule with two marks says the same thing at a glance: the filled part is the money, the
 * mark on it is the day it runs out, and the gap to the payday mark is the shortfall.
 *
 * The component owns no dates and no money: it takes fractions of a window the caller decided on,
 * already-formatted labels, and one description for the whole reading.
 */
@Composable
fun WhfinRunwayTimeline(
    /** How much of the window the current money covers, 0..1. */
    fundedFraction: Float,
    marks: List<WhfinTimelineMark>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** The money does not reach the end of the window; the uncovered part is the warning. */
    shortfall: Boolean = false,
) {
    val funded = fundedFraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.outlineVariant
    val covered = if (shortfall) WhfinThemeTokens.colors.warning else MaterialTheme.colorScheme.primary
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    ) {
        Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            val barHeight = RULE_THICKNESS.toPx()
            val top = (size.height - barHeight) / 2f
            val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )
            if (funded > 0f) drawRoundRect(
                color = covered,
                topLeft = Offset(0f, top),
                size = Size(size.width * funded, barHeight),
                cornerRadius = radius,
            )
            marks.forEach { mark ->
                val x = (size.width * mark.fraction.coerceIn(0f, 1f))
                    .coerceIn(TICK_WIDTH.toPx() / 2f, size.width - TICK_WIDTH.toPx() / 2f)
                drawRoundRect(
                    color = if (mark.emphasis) covered else markColor,
                    topLeft = Offset(x - TICK_WIDTH.toPx() / 2f, 0f),
                    size = Size(TICK_WIDTH.toPx(), size.height),
                    cornerRadius = CornerRadius(TICK_WIDTH.toPx() / 2f, TICK_WIDTH.toPx() / 2f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TimelineLabels(marks, markColor)
    }
}

/**
 * Labels under their own marks, and only as many as fit.
 *
 * Two dates a few days apart would otherwise overprint each other into an unreadable smear, so a
 * label that would touch one already placed is dropped rather than shifted: a date under the wrong
 * mark is worse than no date at all, and the rule still shows the shape.
 */
@Composable
private fun TimelineLabels(marks: List<WhfinTimelineMark>, color: Color) {
    if (marks.isEmpty()) return
    Layout(
        content = {
            marks.forEach { mark ->
                Text(
                    mark.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val gap = LABEL_GAP.roundToPx()
        val taken = mutableListOf<IntRange>()
        val positions = arrayOfNulls<Int>(placeables.size)
        placeables.forEachIndexed { index, placeable ->
            val center = (width * marks[index].fraction.coerceIn(0f, 1f)).toInt()
            val x = (center - placeable.width / 2).coerceIn(0, (width - placeable.width).coerceAtLeast(0))
            val range = (x - gap)..(x + placeable.width + gap)
            if (taken.none { it.first <= range.last && range.first <= it.last }) {
                positions[index] = x
                taken += range
            }
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                positions[index]?.let { placeable.placeRelative(it, 0) }
            }
        }
    }
}

private val BAR_HEIGHT = 12.dp
private val RULE_THICKNESS = 4.dp
private val TICK_WIDTH = 3.dp
private val LABEL_GAP = 6.dp
