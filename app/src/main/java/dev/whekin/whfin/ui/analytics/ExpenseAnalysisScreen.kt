package dev.whekin.whfin.ui.analytics

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinDonutChart
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.NumberFormat
import java.time.YearMonth
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ExpenseAnalysisScreen(
    onBack: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
    viewModel: AnalyticsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    when (val value = state) {
        AnalyticsUiState.Loading -> ExpenseAnalysisPeriodState(
            month = YearMonth.now(),
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.analytics_expenses_title),
            body = stringResource(R.string.analytics_loading),
            onBack = onBack,
            onPreviousMonth = {},
            onNextMonth = {},
        )
        is AnalyticsUiState.Empty -> ExpenseAnalysisPeriodState(
            month = value.selectedMonth,
            state = WhfinPaneState.Empty,
            title = stringResource(R.string.analytics_expenses_empty_title),
            body = stringResource(R.string.analytics_expenses_empty_body),
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
        )
        is AnalyticsUiState.Error -> ExpenseAnalysisPeriodState(
            month = value.selectedMonth,
            state = WhfinPaneState.Error,
            title = stringResource(R.string.analytics_error_title),
            body = stringResource(R.string.analytics_error_body),
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
        )
        is AnalyticsUiState.Content -> ExpenseAnalysisContent(
            data = value.data,
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onShowAllTrend = viewModel::showAllExpensesTrend,
            onShowCategoryTrend = viewModel::showCategoryTrend,
            onOpenTransactions = onOpenTransactions,
        )
    }
}

@Composable
private fun ExpenseAnalysisPeriodState(
    month: YearMonth,
    state: WhfinPaneState,
    title: String,
    body: String,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { AnalyticsHeader(onBack, stringResource(R.string.analytics_expenses_title)) }
            item {
                MonthSelector(
                    month,
                    onPreviousMonth,
                    onNextMonth,
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            item { WhfinStatePane(state, title, body, Modifier.fillMaxWidth()) }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun ExpenseAnalysisContent(
    data: AnalyticsData,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowAllTrend: () -> Unit,
    onShowCategoryTrend: (Long?) -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var selectedTrendIndex by rememberSaveable(data.selectedMonth.toString(), data.trendFilter) {
        mutableIntStateOf(data.trendValues.lastIndex.coerceAtLeast(0))
    }
    val selectedTrendMonth = data.trendValues.getOrNull(selectedTrendIndex)?.month ?: data.selectedMonth

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("expense-analysis-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = navigationBottom + 28.dp),
        ) {
            item(key = "expense-header") {
                AnalyticsHeader(onBack, stringResource(R.string.analytics_expenses_title))
            }
            item(key = "expense-hero") {
                ExpenseHero(
                    data = data,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                )
            }
            item(key = "expense-distribution") {
                ExpenseDistribution(
                    data = data,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item(key = "expense-trend") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp)
                        .testTag("expense-analysis-trend"),
                ) {
                    YearTrend(
                        data = data,
                        selectedMonth = selectedTrendMonth,
                        onSelectMonth = { month ->
                            selectedTrendIndex = data.trendValues.indexOfFirst { it.month == month }
                                .takeIf { it >= 0 }
                                ?: data.trendValues.lastIndex
                        },
                        onShowAllTrend = onShowAllTrend,
                        onOpenTransactions = onOpenTransactions,
                    )
                }
            }
            item(key = "expense-categories") {
                ExpenseCategories(
                    data = data,
                    onCategoryClick = { categoryId ->
                        onShowCategoryTrend(categoryId)
                        scope.launch { listState.animateScrollToItem(3) }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (data.otherCurrencyExpenses.isNotEmpty()) item(key = "expense-currencies") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
                    OtherCurrenciesSection(data.otherCurrencyExpenses)
                }
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ExpenseHero(
    data: AnalyticsData,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthSelector(data.selectedMonth, onPreviousMonth, onNextMonth)
        WhfinFieldLabel(stringResource(R.string.analytics_expenses_total))
        WhfinAmount(
            formatMinor(data.expenseMinor, "GEL"),
            symbol = currencySymbol("GEL"),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            averageComparisonText(data.expenseMinor, data.spendingAverageMinor),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.pendingCount > 0) Text(
            stringResource(R.string.analytics_pending, data.pendingCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ExpenseRingSlice(
    val categoryId: Long?,
    val expenseMinor: Long,
    val color: Color,
    val other: Boolean = false,
)

@Composable
private fun ExpenseDistribution(data: AnalyticsData, modifier: Modifier = Modifier) {
    val fallbackColors = listOf(
        WhfinThemeTokens.colors.bottle,
        WhfinThemeTokens.colors.clay,
        MaterialTheme.colorScheme.secondary,
        WhfinThemeTokens.colors.sage,
        MaterialTheme.colorScheme.tertiary,
    )
    val colored = data.spendingCategoryValues.mapIndexed { index, value ->
        ExpenseRingSlice(
            categoryId = value.categoryId,
            expenseMinor = value.expenseMinor,
            color = value.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size],
        )
    }
    val slices = if (colored.size <= 6) colored else colored.take(5) + ExpenseRingSlice(
        categoryId = null,
        expenseMinor = colored.drop(5).sumOf { it.expenseMinor },
        color = MaterialTheme.colorScheme.outline,
        other = true,
    )
    val selectedFilter = data.trendFilter as? AnalyticsTrendFilter.Category
    val selectedValue = selectedFilter?.let { filter ->
        data.spendingCategoryValues.firstOrNull { it.categoryId == filter.categoryId }
    }
    val selectedIndex = selectedFilter?.let { filter ->
        slices.indexOfFirst { !it.other && it.categoryId == filter.categoryId }.takeIf { it >= 0 }
    }
    val selectedName = when {
        selectedFilter == null -> stringResource(R.string.analytics_all_expenses)
        selectedValue?.name != null -> selectedValue.name
        else -> stringResource(R.string.analytics_uncategorized)
    }
    val selectedShare = if (data.expenseMinor <= 0L) 0.0 else {
        (selectedValue?.expenseMinor ?: data.expenseMinor).toDouble() / data.expenseMinor
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_expenses_composition),
            supportingText = stringResource(R.string.analytics_expenses_composition_hint),
        )
        if (data.spendingCategoryValues.isEmpty()) {
            WhfinStatePane(
                state = WhfinPaneState.Empty,
                title = stringResource(R.string.analytics_expenses_empty_title),
                body = stringResource(R.string.analytics_expenses_empty_body),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WhfinDonutChart(
                    segments = slices.map { WhfinDistributionSegment(it.expenseMinor.toFloat(), it.color) },
                    contentDescription = stringResource(
                        R.string.analytics_expenses_distribution_description,
                        formatMinor(data.expenseMinor, "GEL"),
                    ),
                    selectedIndex = selectedIndex,
                ) {
                    Column(
                        Modifier.width(112.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            NumberFormat.getPercentInstance().format(selectedShare),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            selectedName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCategories(
    data: AnalyticsData,
    onCategoryClick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackColors = listOf(
        WhfinThemeTokens.colors.bottle,
        WhfinThemeTokens.colors.clay,
        MaterialTheme.colorScheme.secondary,
        WhfinThemeTokens.colors.sage,
        MaterialTheme.colorScheme.tertiary,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_expenses_by_category),
            supportingText = stringResource(R.string.analytics_expenses_category_hint),
        )
        if (data.spendingCategoryValues.isNotEmpty()) {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                data.spendingCategoryValues.forEachIndexed { index, value ->
                    SpendingCategoryRow(
                        value = value,
                        totalMinor = data.expenseMinor,
                        color = value.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size],
                        selected = data.trendFilter == AnalyticsTrendFilter.Category(value.categoryId),
                        divider = index < data.spendingCategoryValues.lastIndex,
                        onClick = { onCategoryClick(value.categoryId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingCategoryRow(
    value: AnalyticsCategoryValue,
    totalMinor: Long,
    color: Color,
    selected: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val percentage = if (totalMinor <= 0L) 0.0 else value.expenseMinor.toDouble() / totalMinor
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("expense-category-${value.categoryId ?: "none"}"),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f) else Color.Transparent,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(38.dp).background(color.copy(alpha = .14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.resolve(value.icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        value.name ?: stringResource(R.string.analytics_uncategorized),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        NumberFormat.getPercentInstance().format(percentage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        averageComparisonText(value.expenseMinor, value.averageExpenseMinor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WhfinAmount(
                    formatMinor(value.expenseMinor, "GEL"),
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

@Composable
private fun averageComparisonText(current: Long, average: Long): String {
    if (average <= 0L) return stringResource(R.string.analytics_expenses_no_average)
    val difference = abs(current - average)
    val amount = formatMinor(difference, "GEL")
    return when {
        current > average -> stringResource(R.string.analytics_expenses_above_average, amount)
        current < average -> stringResource(R.string.analytics_expenses_below_average, amount)
        else -> stringResource(R.string.analytics_expenses_at_average)
    }
}

private val expensePreviewData = AnalyticsData(
    selectedMonth = YearMonth.of(2026, 7),
    incomeMinor = 730_800,
    expenseMinor = 903_600,
    categoryRangeMonths = 1,
    categoryExpenseMinor = 903_600,
    categoryValues = emptyList(),
    spendingCategoryValues = listOf(
        AnalyticsCategoryValue(null, null, null, null, 654_700, 300_200),
        AnalyticsCategoryValue(1, "Groceries", "ShoppingCart", 0xff4f725f.toInt(), 101_100, 93_400),
        AnalyticsCategoryValue(2, "Health & Fitness", "MedicalServices", 0xffc96d4f.toInt(), 56_100, 68_000),
        AnalyticsCategoryValue(3, "Eating out", "Restaurant", 0xff788a67.toInt(), 48_700, 42_300),
        AnalyticsCategoryValue(4, "Transport", "DirectionsBus", 0xffb58b4d.toInt(), 43_000, 38_800),
    ),
    spendingAverageMinor = 549_100,
    trendFilter = AnalyticsTrendFilter.All,
    trendFilterName = null,
    trendValues = (0L..11L).map { index ->
        AnalyticsMonthValue(YearMonth.of(2025, 8).plusMonths(index), listOf(510, 430, 390, 620, 470, 520, 490, 550, 610, 590, 640, 904)[index.toInt()] * 1_000L)
    },
    previousTrendExpenseMinor = 640_000,
    unaccountedNetMinor = 0,
    otherCurrencyExpenses = emptyList(),
    pendingCount = 2,
    hasAnyTransactions = true,
)

@Preview(name = "Expenses populated", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "Expenses dark", widthDp = 400, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expenses font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Preview(name = "Expenses compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun ExpenseAnalysisPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpenseAnalysisContent(expensePreviewData, {}, {}, {}, {}, {}, {})
        }
    }
}
