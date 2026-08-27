package dev.whekin.whfin.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One ordered period in a savings pace chart.
 *
 * [valueMinor] is nullable so callers can distinguish a known zero pace from a period for which
 * no observation exists. Formatting belongs to the feature layer; [valueDescription] is what a
 * screen wants TalkBack to read for the value.
 */
@Immutable
data class WhfinSavingsPaceBar(
    val periodLabel: String,
    val valueMinor: Long?,
    val valueDescription: String,
    val periodDescription: String = periodLabel,
    val selected: Boolean = false,
)

/** One ordered balance observation for the non-interactive savings balance line. */
@Immutable
data class WhfinSavingsBalancePoint(
    val periodLabel: String,
    val balanceMinor: Long,
    val balanceDescription: String,
    val periodDescription: String = periodLabel,
    val isProjected: Boolean = false,
    /** Optional common-axis position, e.g. an epoch day. All points must supply it to use it. */
    val position: Long? = null,
)

/**
 * Signed monthly savings pace.
 *
 * Positive bars grow above the shared zero baseline and negative bars grow below it. A year (at
 * most twelve periods) fits its available width; longer ranges retain a 48 dp period target and
 * scroll horizontally. The feature owns selection, formatting, and period navigation.
 */
@Composable
fun WhfinSavingsPaceChart(
    bars: List<WhfinSavingsPaceBar>,
    modifier: Modifier = Modifier,
    targetMinor: Long? = null,
    targetDescription: String? = null,
    onBarClick: ((Int) -> Unit)? = null,
    fitToWidth: Boolean = bars.size <= SAVINGS_CHART_DENSE_PERIOD_LIMIT,
) {
    if (bars.isEmpty()) return

    val dense = fitToWidth && bars.size <= SAVINGS_CHART_DENSE_PERIOD_LIMIT && LocalDensity.current.fontScale <= 1.2f
    val scrollState = rememberScrollState()
    val contentModifier = if (dense) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(SAVINGS_CHART_PERIOD_WIDTH * bars.size)
    }
    val chartModifier = modifier
        .fillMaxWidth()
        .then(if (dense) Modifier else Modifier.horizontalScroll(scrollState))
        .height(SAVINGS_CHART_HEIGHT)

    // Keep the plot width to the viewport in dense mode, and to one 48 dp slot per period in a
    // rolling range. The canvas and slots share this exact width, so the target line remains one
    // continuous line while the user scrolls. A bounded height is intentional: charts live inside
    // unbounded LazyColumn measurement and must not consume the rest of the screen.
    SavingsPaceChartContent(
        bars = bars,
        targetMinor = targetMinor,
        targetDescription = targetDescription,
        onBarClick = onBarClick,
        dense = dense,
        modifier = chartModifier.then(contentModifier),
    )
}

@Composable
private fun SavingsPaceChartContent(
    bars: List<WhfinSavingsPaceBar>,
    targetMinor: Long?,
    targetDescription: String?,
    onBarClick: ((Int) -> Unit)?,
    dense: Boolean,
    modifier: Modifier,
) {
    val positiveMaximum = bars.maxOfOrNull { it.valueMinor?.takeIf { value -> value > 0L }?.toDouble() ?: 0.0 }
        ?: 0.0
    val negativeMaximum = bars.maxOfOrNull {
        it.valueMinor?.takeIf { value -> value < 0L }?.let { value -> -value.toDouble() } ?: 0.0
    } ?: 0.0
    val positiveTarget = targetMinor?.takeIf { it > 0L }?.toDouble() ?: 0.0
    val plotPositiveMaximum = maxOf(positiveMaximum, positiveTarget)
    val range = (plotPositiveMaximum + negativeMaximum).takeIf { it > 0.0 } ?: 1.0
    // With no signed observations, keep the baseline in the middle so the empty state still
    // communicates that both directions are supported. A one-sided range keeps zero at its edge.
    val zeroFraction = if (plotPositiveMaximum == 0.0 && negativeMaximum == 0.0) {
        .5f
    } else {
        (plotPositiveMaximum / range).toFloat().coerceIn(0f, 1f)
    }
    val targetFraction = if (positiveTarget > 0.0) {
        (positiveTarget / range).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    val lineColor = MaterialTheme.colorScheme.outline
    val targetColor = MaterialTheme.colorScheme.secondary

    Box(modifier.height(SAVINGS_CHART_HEIGHT)) {
        Canvas(Modifier.fillMaxSize()) {
            drawSavingsPaceGuides(
                zeroFraction = zeroFraction,
                targetFraction = targetFraction,
                plotHeight = SAVINGS_CHART_PLOT_HEIGHT,
                baselineColor = lineColor,
                targetColor = targetColor,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(SAVINGS_CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            bars.forEachIndexed { index, bar ->
                SavingsPaceBarSlot(
                    bar = bar,
                    index = index,
                    zeroFraction = zeroFraction,
                    range = range,
                    targetDescription = targetDescription,
                    onClick = onBarClick?.let { callback -> { callback(index) } },
                    modifier = if (dense) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.width(SAVINGS_CHART_PERIOD_WIDTH)
                    },
                )
            }
        }
    }
}

@Composable
private fun SavingsPaceBarSlot(
    bar: WhfinSavingsPaceBar,
    index: Int,
    zeroFraction: Float,
    range: Double,
    targetDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val description = buildPaceBarDescription(bar, targetDescription)
    val semanticsModifier = modifier
        .fillMaxHeight()
        .testTag("whfin-savings-pace-bar-$index")
        .semantics(mergeDescendants = true) {
            contentDescription = description
            selected = bar.selected
        }
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SAVINGS_CHART_PLOT_HEIGHT),
            ) {
                val value = bar.valueMinor
                if (value == null) {
                    // Missing is deliberately different from a known zero: a quiet dash at the
                    // baseline gives the preview and the eye a stable place for the gap.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = SAVINGS_CHART_PLOT_HEIGHT * zeroFraction - 1.dp)
                            .width(10.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    )
                } else if (value != 0L) {
                    val fraction = (safeAbs(value).toDouble() / range).toFloat().coerceIn(0f, 1f)
                    val barHeight = SAVINGS_CHART_PLOT_HEIGHT * fraction
                    val topOffset = if (value > 0L) {
                        SAVINGS_CHART_PLOT_HEIGHT * (zeroFraction - fraction).coerceAtLeast(0f)
                    } else {
                        SAVINGS_CHART_PLOT_HEIGHT * zeroFraction
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = topOffset)
                            .widthIn(min = 10.dp)
                            .fillMaxWidth(.68f)
                            .height(barHeight)
                            .clip(
                                if (value > 0L) {
                                    RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                                } else {
                                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 7.dp, bottomEnd = 7.dp)
                                },
                            )
                            .background(savingsPaceBarColor(value > 0L, bar.selected)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SAVINGS_CHART_LABEL_HEIGHT)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = bar.periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = semanticsModifier,
            shape = MaterialTheme.shapes.small,
            color = if (bar.selected) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent,
            tonalElevation = 0.dp,
            content = content,
        )
    } else {
        Box(modifier = semanticsModifier) { content() }
    }
}

private fun DrawScope.drawSavingsPaceGuides(
    zeroFraction: Float,
    targetFraction: Float?,
    plotHeight: Dp,
    baselineColor: Color,
    targetColor: Color,
) {
    val plotHeightPx = plotHeight.toPx()
    val zeroY = plotHeightPx * zeroFraction
    drawLine(
        color = baselineColor,
        start = androidx.compose.ui.geometry.Offset(0f, zeroY),
        end = androidx.compose.ui.geometry.Offset(size.width, zeroY),
        strokeWidth = 1.dp.toPx(),
    )
    targetFraction?.let { fraction ->
        val targetY = zeroY - (plotHeightPx * fraction)
        drawLine(
            color = targetColor,
            start = androidx.compose.ui.geometry.Offset(0f, targetY),
            end = androidx.compose.ui.geometry.Offset(size.width, targetY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

private fun buildPaceBarDescription(
    bar: WhfinSavingsPaceBar,
    targetDescription: String?,
): String = buildString {
    append(bar.periodDescription)
    if (bar.periodDescription.isNotBlank() && bar.valueDescription.isNotBlank()) append(", ")
    append(bar.valueDescription)
    if (!targetDescription.isNullOrBlank()) append(", $targetDescription")
}

@Composable
private fun savingsPaceBarColor(positive: Boolean, selected: Boolean): Color {
    val color = if (positive) WhfinThemeTokens.colors.positive else WhfinThemeTokens.colors.clay
    return if (selected) color else color.copy(alpha = .48f)
}

/**
 * A quiet, non-interactive balance history. The line is intentionally a single solid stroke: the
 * surrounding screen provides exact values and period navigation, while this chart gives the eye
 * the shape of the reserve over time. [contentDescription] should be a localized chart summary;
 * when omitted, a complete description is assembled from every point and the goal.
 */
@Composable
fun WhfinSavingsBalanceChart(
    points: List<WhfinSavingsBalancePoint>,
    modifier: Modifier = Modifier,
    goalMinor: Long? = null,
    goalDescription: String? = null,
    contentDescription: String? = null,
    selectedIndex: Int? = null,
) {
    if (points.isEmpty()) return

    val completeDescription = contentDescription?.takeUnless { it.isBlank() }
        ?: buildSavingsBalanceDescription(points, goalDescription)
    val chartModifier = modifier
        .fillMaxWidth()
        .height(SAVINGS_BALANCE_CHART_HEIGHT)
        .semantics { this.contentDescription = completeDescription }

    SavingsBalanceChartContent(
        points = points,
        goalMinor = goalMinor,
        selectedIndex = selectedIndex,
        modifier = chartModifier,
    )
}

@Composable
private fun SavingsBalanceChartContent(
    points: List<WhfinSavingsBalancePoint>,
    goalMinor: Long?,
    selectedIndex: Int?,
    modifier: Modifier,
) {
    val values = buildList {
        addAll(points.map { it.balanceMinor.toDouble() })
        goalMinor?.let { add(it.toDouble()) }
    }
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val rawRange = maximum - minimum
    val padding = if (rawRange > 0.0) rawRange * .12 else maxOf(kotlin.math.abs(maximum) * .12, 1.0)
    val lowerBound = minimum - padding
    val upperBound = maximum + padding
    val range = (upperBound - lowerBound).coerceAtLeast(1.0)
    val goal = goalMinor?.toDouble()
    val goalColor = MaterialTheme.colorScheme.secondary
    val balanceColor = MaterialTheme.colorScheme.primary
    val projectionColor = MaterialTheme.colorScheme.tertiary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val denseLabels = points.size <= SAVINGS_CHART_DENSE_PERIOD_LIMIT && LocalDensity.current.fontScale <= 1.2f

    Box(modifier.height(SAVINGS_BALANCE_CHART_HEIGHT)) {
        Canvas(Modifier.fillMaxSize()) {
            val plotHeightPx = SAVINGS_BALANCE_PLOT_HEIGHT.toPx()
            val xStep = size.width / points.size
            val positions = points.mapNotNull { it.position }
            val firstPosition = positions.minOrNull()?.toDouble() ?: 0.0
            val positionSpan = (positions.maxOrNull()?.toDouble() ?: 0.0) - firstPosition
            fun xFor(index: Int): Float = if (positions.size == points.size && positionSpan > 0.0) {
                xStep / 2 + ((positions[index].toDouble() - firstPosition) / positionSpan).toFloat() * (size.width - xStep)
            } else (index + .5f) * xStep
            fun yFor(value: Double): Float = ((upperBound - value) / range * plotHeightPx).toFloat()
            goal?.let { goalValue ->
                drawLine(
                    color = goalColor,
                    start = androidx.compose.ui.geometry.Offset(0f, yFor(goalValue)),
                    end = androidx.compose.ui.geometry.Offset(size.width, yFor(goalValue)),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            if (points.size == 1) {
                // A short static tick keeps a single observation visible without creating a tiny
                // control that suggests it can be tapped.
                drawLine(
                    color = balanceColor,
                    start = androidx.compose.ui.geometry.Offset(0f, yFor(points.first().balanceMinor.toDouble())),
                    end = androidx.compose.ui.geometry.Offset(size.width, yFor(points.first().balanceMinor.toDouble())),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            } else {
                fun pathFor(start: Int, end: Int): androidx.compose.ui.graphics.Path {
                    val path = androidx.compose.ui.graphics.Path()
                    for (index in start..end) {
                        val x = xFor(index)
                        val y = yFor(points[index].balanceMinor.toDouble())
                        if (index == start) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }
                val forecastStart = points.indexOfFirst { it.isProjected }
                val actualEnd = if (forecastStart < 0) points.lastIndex else forecastStart - 1
                if (actualEnd > 0) drawPath(pathFor(0, actualEnd), balanceColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                if (forecastStart >= 0) {
                    val anchor = (forecastStart - 1).coerceAtLeast(0)
                    drawPath(pathFor(anchor, points.lastIndex), projectionColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))))
                    drawLine(guideColor, androidx.compose.ui.geometry.Offset(xFor(anchor), 0f),
                        androidx.compose.ui.geometry.Offset(xFor(anchor), plotHeightPx), strokeWidth = 1.dp.toPx())
                    drawCircle(balanceColor, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(xFor(anchor), yFor(points[anchor].balanceMinor.toDouble())))
                }
            }
            selectedIndex?.takeIf { it in points.indices }?.let { index ->
                drawCircle(balanceColor, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(xFor(index), yFor(points[index].balanceMinor.toDouble())))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(SAVINGS_BALANCE_CHART_HEIGHT),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labels = if (denseLabels || points.size <= 3) points
                else listOf(points.first(), points[points.size / 2], points.last())
            labels.forEach { point ->
                Box(
                    modifier = if (denseLabels) {
                        Modifier.weight(1f).fillMaxHeight()
                    } else {
                        Modifier.fillMaxHeight()
                    },
                ) {
                    Text(
                        text = point.periodLabel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun buildSavingsBalanceDescription(
    points: List<WhfinSavingsBalancePoint>,
    goalDescription: String?,
): String = buildString {
    points.forEachIndexed { index, point ->
        if (index > 0) append("; ")
        append(point.periodDescription)
        if (point.periodDescription.isNotBlank() && point.balanceDescription.isNotBlank()) append(", ")
        append(point.balanceDescription)
    }
    if (!goalDescription.isNullOrBlank()) {
        if (points.isNotEmpty()) append("; ")
        append(goalDescription)
    }
}

private fun safeAbs(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)

private const val SAVINGS_CHART_DENSE_PERIOD_LIMIT = 12
private val SAVINGS_CHART_PERIOD_WIDTH = 48.dp
private val SAVINGS_CHART_PLOT_HEIGHT = 148.dp
private val SAVINGS_BALANCE_PLOT_HEIGHT = 132.dp
private val SAVINGS_CHART_LABEL_HEIGHT = 48.dp
private val SAVINGS_CHART_HEIGHT = SAVINGS_CHART_PLOT_HEIGHT + SAVINGS_CHART_LABEL_HEIGHT
private val SAVINGS_BALANCE_CHART_HEIGHT = SAVINGS_BALANCE_PLOT_HEIGHT + SAVINGS_CHART_LABEL_HEIGHT
