package dev.whekin.whfin.ui.analytics

import android.content.res.Configuration
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinDistributionBar
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinHaptics
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinMonthlyBar
import dev.whekin.whfin.core.ui.WhfinMonthlyBarChart
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinTotalRule
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun AnalyticsScreen(
    onBack: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
    viewModel: AnalyticsViewModel = viewModel(),
) {
    val model by viewModel.uiState.collectAsState()
    AnalyticsContent(
        model = model,
        onBack = onBack,
        onPreviousPeriod = viewModel::selectPreviousPeriod,
        onNextPeriod = viewModel::selectNextPeriod,
        onScaleChange = viewModel::setScale,
        onSelectMonth = viewModel::selectMonth,
        onShowAllTrend = viewModel::showAllExpensesTrend,
        onOpenExpenses = {
            viewModel.showAllExpensesTrend()
            onOpenExpenses()
        },
        onOpenTransactions = onOpenTransactions,
    )
}

/**
 * The frame every statistics screen shares: title, scale, period, then whatever the period is.
 *
 * Loading and failure keep the same period controls rather than a frozen "now", so the screen never
 * shows a month the user is not on and never offers an arrow that does nothing.
 */
@Composable
internal fun AnalyticsScaffold(
    model: AnalyticsUiModel,
    title: String,
    emptyTitle: String,
    emptyBody: String,
    onBack: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onScaleChange: (AnalyticsScale) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    listTestTag: String,
    content: LazyListScope.(AnalyticsData) -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val swipe = periodSwipe(
            width = maxWidth,
            canSelectPrevious = model.canSelectPrevious,
            canSelectNext = model.canSelectNext,
            onPreviousPeriod = onPreviousPeriod,
            onNextPeriod = onNextPeriod,
        )
        // The drag lives on the full-screen box rather than on the list: the gesture belongs to the
        // page, and the list only declines it because it scrolls the other way.
        Box(Modifier.fillMaxSize().then(swipe.dragModifier)) {
            LazyColumn(
                Modifier.fillMaxSize().then(swipe.pageModifier).testTag(listTestTag),
                state = listState,
                contentPadding = PaddingValues(bottom = navigationBottom + 28.dp),
            ) {
                item(key = "analytics-header") { AnalyticsHeader(onBack, title) }
                item(key = "analytics-period") {
                    PeriodSelector(
                        period = model.period,
                        canSelectPrevious = model.canSelectPrevious,
                        canSelectNext = model.canSelectNext,
                        onPreviousPeriod = onPreviousPeriod,
                        onNextPeriod = onNextPeriod,
                        onScaleChange = onScaleChange,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
                    )
                }
                when (val state = model.state) {
                    AnalyticsUiState.Loading -> item(key = "state") {
                        WhfinStatePane(
                            WhfinPaneState.Loading,
                            title,
                            stringResource(R.string.analytics_loading),
                            Modifier.fillMaxWidth(),
                        )
                    }
                    AnalyticsUiState.Empty -> item(key = "state") {
                        WhfinStatePane(WhfinPaneState.Empty, emptyTitle, emptyBody, Modifier.fillMaxWidth())
                    }
                    AnalyticsUiState.Error -> item(key = "state") {
                        WhfinStatePane(
                            WhfinPaneState.Error,
                            stringResource(R.string.analytics_error_title),
                            stringResource(R.string.analytics_error_body),
                            Modifier.fillMaxWidth(),
                        )
                    }
                    is AnalyticsUiState.Content -> content(state.data)
                }
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Moving through time by dragging the page, as an equal of the two arrows above it.
 *
 * The page follows the finger along the same shared axis the shell already uses between screens — a
 * short slide under a fade — so a drag let go half-way has said what it would do and then does
 * nothing. A committed one keeps going until the page is invisible, swaps the period underneath,
 * and brings the next one in from the other side: the substitution happens at the moment there is
 * nothing on screen to see it happen.
 *
 * A direction with nothing behind it still moves and still springs back. Declining the drag outright
 * would be indistinguishable from a screen that does not answer to the gesture at all, and the
 * arrow above it is already the thing that shows the edge of the record by going grey.
 */
@Composable
private fun periodSwipe(
    width: Dp,
    canSelectPrevious: Boolean,
    canSelectNext: Boolean,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
): PeriodSwipe {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val widthPx = with(density) { width.toPx() }
    // Travel is the shell's own: an eighth of the width. Commit distance is the finger's, and it is
    // longer, so the page never runs out of room before the gesture has been decided.
    val travel = widthPx / 8f
    val commitDistance = widthPx * .22f
    val flingVelocity = with(density) { 400.dp.toPx() }
    val exitSpec = WhfinMotion.quick<Float>()
    val enterSpec = WhfinMotion.standard<Float>()

    // One number is where the page is, written by the finger and by the settle in turn. An
    // `Animatable` would serialise the two through its own mutex, and a snap still queued from the
    // last delta would then cancel the settle it was handed off to — the page would leave and the
    // period would never change.
    val page = remember { mutableFloatStateOf(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragged += delta
        page.floatValue = (dragged / commitDistance).coerceIn(-1f, 1f) * travel * DRAG_REACH
    }
    val dragModifier = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Horizontal,
        onDragStarted = { dragged = 0f },
        onDragStopped = { velocity ->
            val progress = dragged / commitDistance
            // Dragging the page to the left uncovers what comes after it, as on any page of a book.
            val forward = progress < 0f
            val decided = abs(progress) >= 1f ||
                (abs(velocity) >= flingVelocity && abs(progress) >= FLING_MIN_PROGRESS)
            dragged = 0f
            if (decided && (if (forward) canSelectNext else canSelectPrevious)) {
                haptics.performHapticFeedback(WhfinHaptics.navigation)
                val sign = if (forward) -1f else 1f
                animate(page.floatValue, sign * travel, animationSpec = exitSpec) { value, _ ->
                    page.floatValue = value
                }
                if (forward) onNextPeriod() else onPreviousPeriod()
                page.floatValue = -sign * travel
            }
            animate(page.floatValue, 0f, animationSpec = enterSpec) { value, _ ->
                page.floatValue = value
            }
        },
    )
    return PeriodSwipe(page, travel, dragModifier)
}

@Stable
private class PeriodSwipe(
    private val page: FloatState,
    private val travel: Float,
    val dragModifier: Modifier,
) {
    /** Only the page moves and fades; the cover over the status bar is not part of the period. */
    val pageModifier: Modifier
        get() = Modifier.graphicsLayer {
            translationX = page.floatValue
            alpha = 1f - (abs(page.floatValue) / travel).coerceAtMost(1f)
        }
}

/** How much of the fade a drag may spend before it is released: enough to be read, short of blank. */
private const val DRAG_REACH = .65f

/** A flick still has to have gone somewhere; below this it is a tap that slipped. */
private const val FLING_MIN_PROGRESS = .25f

@Composable
internal fun AnalyticsContent(
    model: AnalyticsUiModel,
    onBack: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onScaleChange: (AnalyticsScale) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onShowAllTrend: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val listState = rememberLazyListState()
    AnalyticsScaffold(
        model = model,
        title = stringResource(R.string.analytics_title),
        emptyTitle = stringResource(R.string.analytics_empty_title),
        emptyBody = stringResource(R.string.analytics_empty_body),
        onBack = onBack,
        onPreviousPeriod = onPreviousPeriod,
        onNextPeriod = onNextPeriod,
        onScaleChange = onScaleChange,
        listState = listState,
        listTestTag = "analytics-list",
    ) { data ->
        item(key = "period-result") {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                PeriodResult(data, onOpenExpenses)
            }
        }
        item(key = "trend") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                    .testTag("analytics-trend"),
            ) {
                PeriodTrend(
                    data = data,
                    onSelectMonth = onSelectMonth,
                    onShowAllTrend = onShowAllTrend,
                    onOpenTransactions = onOpenTransactions,
                )
            }
        }
        data.pace?.let { pace ->
            item(key = "pace") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    SpendingPace(data.expenseMinor, pace, data.period.scale)
                }
            }
        }
        if (data.categoryChanges.isNotEmpty()) {
            item(key = "changes") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    CategoryChanges(
                        changes = data.categoryChanges,
                        period = data.period,
                        onOpenTransactions = onOpenTransactions,
                    )
                }
            }
        }
        item(key = "categories") {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                CategoryShape(data = data, onOpenExpenses = onOpenExpenses)
            }
        }
        if (data.unaccountedNetMinor != 0L) item(key = "unaccounted") {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                UnaccountedSection(data.unaccountedNetMinor)
            }
        }
        if (data.otherCurrencyExpenses.isNotEmpty()) item(key = "currencies") {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                OtherCurrenciesSection(data.otherCurrencyExpenses)
            }
        }
    }
}

@Composable
internal fun AnalyticsHeader(onBack: () -> Unit, title: String = stringResource(R.string.analytics_title)) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PeriodResult(
    data: AnalyticsData,
    onOpenExpenses: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            WhfinFieldLabel(
                stringResource(
                    when (data.period.scale) {
                        AnalyticsScale.MONTH -> R.string.analytics_month_result
                        AnalyticsScale.YEAR -> R.string.analytics_year_result
                    },
                ),
            )
            WhfinAmount(
                formatMinor(data.deltaMinor, "GEL", withSign = true),
                symbol = currencySymbol("GEL"),
                style = MaterialTheme.typography.displayMedium,
                color = if (data.deltaMinor >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
        // Итог месяца закрывается бухгалтерской двойной чертой, как и блок месяца в ленте.
        WhfinTotalRule()
        val metrics: @Composable (Modifier, Modifier) -> Unit = { incomeModifier, expenseModifier ->
            AnalyticsMetric(
                stringResource(R.string.summary_income),
                formatMinor(data.incomeMinor, "GEL"),
                MaterialTheme.colorScheme.primary,
                incomeModifier,
            )
            AnalyticsMetric(
                stringResource(R.string.summary_expenses),
                formatMinor(data.expenseMinor, "GEL"),
                MaterialTheme.colorScheme.tertiary,
                expenseModifier,
                onClick = onOpenExpenses,
            )
        }
        if (data.period.scale == AnalyticsScale.YEAR) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                metrics(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                metrics(Modifier.weight(1f), Modifier.weight(1f))
            }
        }
        if (data.pendingCount > 0) Text(
            stringResource(R.string.analytics_pending, data.pendingCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PeriodSelector(
    period: AnalyticsPeriod,
    canSelectPrevious: Boolean,
    canSelectNext: Boolean,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onScaleChange: (AnalyticsScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WhfinIconButton(
                Icons.Default.ChevronLeft,
                stringResource(R.string.analytics_previous_period),
                onPreviousPeriod,
                outlined = false,
                enabled = canSelectPrevious,
            )
            // The month zooms out to its year by tapping the month, with the chevron saying so.
            // A separate centred link under the title spent a whole row on a second way to move
            // through time, next to the two arrows that already do it.
            if (period.scale == AnalyticsScale.MONTH) {
                Surface(
                    onClick = { onScaleChange(AnalyticsScale.YEAR) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("analytics-view-year"),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            periodTitle(period),
                            modifier = Modifier.testTag("analytics-period-title"),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = stringResource(
                                R.string.analytics_view_year_total,
                                period.year,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Text(
                    periodTitle(period),
                    modifier = Modifier.weight(1f).testTag("analytics-period-title"),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            WhfinIconButton(
                Icons.Default.ChevronRight,
                stringResource(R.string.analytics_next_period),
                onNextPeriod,
                outlined = false,
                enabled = canSelectNext,
            )
        }
        if (period.scale == AnalyticsScale.YEAR) {
            Text(
                stringResource(R.string.analytics_choose_month_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalyticsMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WhfinAmount(
                value,
                symbol = currencySymbol("GEL"),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).testTag("analytics-open-expenses"),
            shape = MaterialTheme.shapes.small,
            color = Color.Transparent,
            content = content,
        )
    } else {
        Box(modifier.heightIn(min = 48.dp)) { content() }
    }
}

@Composable
private fun SpendingPace(
    expenseMinor: Long,
    pace: AnalyticsPace,
    scale: AnalyticsScale,
) {
    Column(
        Modifier.testTag("analytics-pace"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_pace_title),
            supportingText = stringResource(
                R.string.analytics_pace_hint,
                pace.daysElapsed,
                pace.daysTotal,
            ),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    WhfinFieldLabel(stringResource(R.string.analytics_projected))
                    WhfinAmount(
                        formatMinor(pace.projectedExpenseMinor, "GEL"),
                        symbol = currencySymbol("GEL"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        comparisonText(
                            pace.projectedExpenseMinor,
                            pace.previousPeriodExpenseMinor,
                            scale,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AnalyticsMetric(
                        stringResource(R.string.analytics_spent_so_far),
                        formatMinor(expenseMinor, "GEL"),
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.weight(1f),
                    )
                    AnalyticsMetric(
                        stringResource(
                            when (scale) {
                                AnalyticsScale.MONTH -> R.string.analytics_previous_month_expenses
                                AnalyticsScale.YEAR -> R.string.analytics_previous_year_expenses
                            },
                        ),
                        formatMinor(pace.previousPeriodExpenseMinor, "GEL"),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChanges(
    changes: List<AnalyticsCategoryChange>,
    period: AnalyticsPeriod,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val fallbackColors = listOf(
        WhfinThemeTokens.colors.clay,
        WhfinThemeTokens.colors.bottle,
        MaterialTheme.colorScheme.secondary,
    )
    Column(
        Modifier.testTag("analytics-changes"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_changes_title),
            supportingText = stringResource(
                when (period.scale) {
                    AnalyticsScale.MONTH -> R.string.analytics_changes_hint
                    AnalyticsScale.YEAR -> R.string.analytics_changes_hint_year
                },
            ),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            changes.forEachIndexed { index, change ->
                val name = change.name ?: stringResource(R.string.analytics_uncategorized)
                CategoryChangeRow(
                    change = change,
                    name = name,
                    color = change.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size],
                    divider = index < changes.lastIndex,
                    onClick = {
                        onOpenTransactions(
                            AnalyticsTransactionsRequest(
                                period = period,
                                categoryFilterEnabled = true,
                                categoryId = change.categoryId,
                                filterName = name,
                                expectedExpenseMinor = change.expenseMinor,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryChangeRow(
    change: AnalyticsCategoryChange,
    name: String,
    color: Color,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val deltaColor = if (change.deltaMinor > 0L) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics-change-${change.categoryId ?: "none"}"),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(38.dp).background(color.copy(alpha = .14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.resolve(change.icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    // Раздел уже подписан «Крупнейшие сдвиги категорий с прошлого месяца»,
                    // поэтому префикс «Изменение:» в каждой строке только удлинял её.
                    WhfinAmount(
                        formatMinor(change.deltaMinor, "GEL", withSign = true),
                        symbol = currencySymbol("GEL"),
                        style = MaterialTheme.typography.bodySmall,
                        color = deltaColor,
                    )
                }
                WhfinAmount(
                    formatMinor(change.expenseMinor, "GEL"),
                    symbol = currencySymbol("GEL"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (divider) HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * The shape of the period's spending, and one way into the screen that itemises it.
 *
 * Statistics used to carry a full category list of its own next to Spending's, each over a
 * different window. Two lists of the same categories can only make a reader wonder which one is
 * the truth, so the composition question now belongs to Spending alone and Statistics keeps the
 * bar that answers it at a glance.
 */
@Composable
private fun CategoryShape(data: AnalyticsData, onOpenExpenses: () -> Unit) {
    val fallbackColors = listOf(
        WhfinThemeTokens.colors.bottle,
        WhfinThemeTokens.colors.clay,
        MaterialTheme.colorScheme.secondary,
        WhfinThemeTokens.colors.sage,
        MaterialTheme.colorScheme.tertiary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_expenses_composition),
            supportingText = stringResource(R.string.analytics_categories_hint),
        )
        if (data.categoryValues.isEmpty()) {
            Text(
                stringResource(R.string.analytics_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WhfinDistributionBar(
                data.categoryValues.mapIndexed { index, value ->
                    WhfinDistributionSegment(
                        value.expenseMinor.toFloat(),
                        value.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size],
                    )
                },
            )
            WhfinButton(
                label = stringResource(R.string.analytics_expenses_by_category),
                onClick = onOpenExpenses,
                modifier = Modifier.fillMaxWidth().testTag("analytics-open-categories"),
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.AutoMirrored.Filled.List,
            )
        }
    }
}

@Composable
internal fun PeriodTrend(
    data: AnalyticsData,
    onSelectMonth: (YearMonth) -> Unit,
    onShowAllTrend: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val locale = currentLocale()
    val period = data.period
    val filterName = when (val filter = data.trendFilter) {
        AnalyticsTrendFilter.All -> stringResource(R.string.analytics_all_expenses)
        is AnalyticsTrendFilter.Category -> data.trendFilterName ?: stringResource(R.string.analytics_uncategorized)
    }
    // In month scale the chart is a rolling window around one selected bar. In year scale every bar
    // belongs to the selected period, so the footer reports the year itself and a bar is a drill-down.
    val selectedValue = when (period.scale) {
        AnalyticsScale.MONTH -> data.trendValues.firstOrNull { it.month == period.month }?.expenseMinor ?: 0L
        AnalyticsScale.YEAR -> data.trendValues.sumOf { it.expenseMinor }
    }
    val previousValue = when (period.scale) {
        AnalyticsScale.MONTH ->
            data.trendValues.firstOrNull { it.month == period.month.minusMonths(1) }?.expenseMinor
                ?: data.previousTrendExpenseMinor
        AnalyticsScale.YEAR -> data.previousTrendExpenseMinor
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_year_trend_months, period.year),
            // The filter names itself only while one is on. Unfiltered, "All expenses" repeated
            // what the chart under it is: a second line that carried no new fact.
            supportingText = filterName.takeIf { data.trendFilter is AnalyticsTrendFilter.Category },
        )
        if (data.trendFilter is AnalyticsTrendFilter.Category) WhfinChoiceRail {
            item {
                WhfinFilterPill(
                    stringResource(R.string.analytics_all_expenses),
                    selected = false,
                    onClick = onShowAllTrend,
                )
            }
            item {
                WhfinFilterPill(filterName, selected = true, onClick = {})
            }
        }
        WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WhfinMonthlyBarChart(
                    bars = data.trendValues.map { point ->
                        WhfinMonthlyBar(
                            label = point.month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                            value = point.expenseMinor,
                            amountDescription = formatMinor(point.expenseMinor, "GEL"),
                            selected = period.scale == AnalyticsScale.MONTH && point.month == period.month,
                            periodDescription = monthTitle(point.month),
                        )
                    },
                    onBarClick = { index -> data.trendValues.getOrNull(index)?.month?.let(onSelectMonth) },
                    fitToWidth = true,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(periodTitle(period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WhfinAmount(
                            formatMinor(selectedValue, "GEL"),
                            symbol = currencySymbol("GEL"),
                            modifier = Modifier.testTag("analytics-selected-trend-amount"),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(
                        comparisonText(selectedValue, previousValue, period.scale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WhfinButton(
                    label = stringResource(R.string.analytics_view_transactions),
                    onClick = {
                        onOpenTransactions(
                            AnalyticsTransactionsRequest(
                                period = period,
                                categoryFilterEnabled = data.trendFilter is AnalyticsTrendFilter.Category,
                                categoryId = (data.trendFilter as? AnalyticsTrendFilter.Category)?.categoryId,
                                filterName = filterName,
                                expectedExpenseMinor = selectedValue,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("analytics-view-transactions"),
                    enabled = selectedValue > 0L,
                    style = WhfinActionStyle.Secondary,
                    leadingIcon = Icons.AutoMirrored.Filled.List,
                )
            }
        }
    }
}

@Composable
private fun UnaccountedSection(amountMinor: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_unaccounted),
            supportingText = stringResource(R.string.analytics_unaccounted_hint),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                WhfinAmount(
                    formatMinor(amountMinor, "GEL", withSign = true),
                    symbol = currencySymbol("GEL"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun OtherCurrenciesSection(values: List<AnalyticsCurrencyValue>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_other_currencies),
            supportingText = stringResource(R.string.analytics_other_currencies_hint),
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            values.forEachIndexed { index, value ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(value.currency, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    WhfinAmount(
                        formatMinor(value.expenseMinor, value.currency),
                        symbol = currencySymbol(value.currency),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index < values.lastIndex) HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        WhfinNotice(
            title = stringResource(R.string.analytics_rates_unavailable_title),
            body = stringResource(R.string.analytics_rates_unavailable_body),
            kind = WhfinNoticeKind.Unavailable,
            icon = Icons.Default.Info,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun comparisonText(current: Long, previous: Long, scale: AnalyticsScale): String {
    val month = scale == AnalyticsScale.MONTH
    if (previous <= 0L) return stringResource(
        if (month) R.string.analytics_no_previous else R.string.analytics_no_previous_year,
    )
    val percent = abs(current - previous).toDouble() / previous * 100.0
    val formatted = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(percent / 100.0)
    return when {
        current > previous -> stringResource(
            if (month) R.string.analytics_more_than_previous else R.string.analytics_more_than_previous_year,
            formatted,
        )
        current < previous -> stringResource(
            if (month) R.string.analytics_less_than_previous else R.string.analytics_less_than_previous_year,
            formatted,
        )
        else -> stringResource(
            if (month) R.string.analytics_same_as_previous else R.string.analytics_same_as_previous_year,
        )
    }
}

@Composable
internal fun periodTitle(period: AnalyticsPeriod): String = when (period.scale) {
    AnalyticsScale.MONTH -> monthTitle(period.month)
    AnalyticsScale.YEAR -> period.year.toString()
}

@Composable
internal fun monthTitle(month: YearMonth): String {
    val locale = currentLocale()
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return "${name.replaceFirstChar { it.titlecase(locale) }} ${month.year}"
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private val previewData = AnalyticsData(
    period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
    incomeMinor = 730_800,
    expenseMinor = 109_127,
    categoryValues = listOf(
        AnalyticsCategoryValue(1, "Groceries", "ShoppingCart", 0xff4f725f.toInt(), 38_200),
        AnalyticsCategoryValue(2, "Eating out", "Restaurant", 0xffc96d4f.toInt(), 27_840),
        AnalyticsCategoryValue(3, "Transport", "DirectionsBus", 0xff788a67.toInt(), 18_400),
        AnalyticsCategoryValue(null, null, null, null, 24_687),
    ),
    trendFilter = AnalyticsTrendFilter.All,
    trendFilterName = null,
    trendValues = (1..12).map { month ->
        AnalyticsMonthValue(YearMonth.of(2026, month), listOf(82, 91, 76, 104, 98, 96, 109, 0, 0, 0, 0, 0)[month - 1] * 1_000L)
    },
    previousTrendExpenseMinor = 96_000,
    unaccountedNetMinor = 4_200,
    otherCurrencyExpenses = listOf(AnalyticsCurrencyValue("USD", 6_900)),
    pendingCount = 2,
    hasAnyTransactions = true,
    pace = AnalyticsPace(
        daysElapsed = 20,
        daysTotal = 31,
        projectedExpenseMinor = 169_147,
        previousPeriodExpenseMinor = 96_000,
    ),
    categoryChanges = listOf(
        AnalyticsCategoryChange(1, "Groceries", "ShoppingCart", 0xff4f725f.toInt(), 38_200, 22_400),
        AnalyticsCategoryChange(2, "Eating out", "Restaurant", 0xffc96d4f.toInt(), 27_840, 41_500),
        AnalyticsCategoryChange(3, "Transport", "DirectionsBus", 0xff788a67.toInt(), 18_400, 12_000),
    ),
)

private val previewYearData = previewData.copy(
    period = AnalyticsPeriod.year(YearMonth.of(2026, 7)),
    incomeMinor = 8_411_000,
    expenseMinor = 7_284_500,
    pace = AnalyticsPace(
        daysElapsed = 224,
        daysTotal = 365,
        projectedExpenseMinor = 11_870_000,
        previousPeriodExpenseMinor = 10_240_000,
    ),
    previousTrendExpenseMinor = 10_240_000,
)

private fun previewModel(
    data: AnalyticsData = previewData,
    state: AnalyticsUiState = AnalyticsUiState.Content(data),
) = AnalyticsUiModel(
    period = data.period,
    canSelectPrevious = true,
    canSelectNext = false,
    state = state,
)

@Preview(name = "Analytics populated", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "Analytics dark", widthDp = 400, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Analytics font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Preview(name = "Analytics compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(previewModel(), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Analytics year", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "Analytics year font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Composable
private fun AnalyticsYearPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(previewModel(previewYearData), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Analytics empty", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun AnalyticsEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(
                previewModel(state = AnalyticsUiState.Empty),
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}

@Preview(name = "Analytics loading", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsLoadingPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(
                previewModel(state = AnalyticsUiState.Loading),
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}

@Preview(name = "Analytics error", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsErrorPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(
                previewModel(state = AnalyticsUiState.Error),
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
