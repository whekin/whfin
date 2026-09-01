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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinAmount
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinDonutChart
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
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
    val model by viewModel.uiState.collectAsState()
    ExpenseAnalysisContent(
        model = model,
        onBack = onBack,
        onPreviousPeriod = viewModel::selectPreviousPeriod,
        onNextPeriod = viewModel::selectNextPeriod,
        onScaleChange = viewModel::setScale,
        onSelectMonth = viewModel::selectMonth,
        onShowAllTrend = viewModel::showAllExpensesTrend,
        onShowCategoryTrend = viewModel::showCategoryTrend,
        onOpenTransactions = onOpenTransactions,
    )
}

@Composable
internal fun ExpenseAnalysisContent(
    model: AnalyticsUiModel,
    onBack: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onScaleChange: (AnalyticsScale) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onShowAllTrend: () -> Unit,
    onShowCategoryTrend: (Long?) -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    AnalyticsScaffold(
        model = model,
        title = stringResource(R.string.analytics_expenses_title),
        emptyTitle = stringResource(
            when (model.period.scale) {
                AnalyticsScale.MONTH -> R.string.analytics_expenses_empty_title
                AnalyticsScale.YEAR -> R.string.analytics_expenses_empty_title_year
            },
        ),
        emptyBody = stringResource(R.string.analytics_expenses_empty_body),
        onBack = onBack,
        onPreviousPeriod = onPreviousPeriod,
        onNextPeriod = onNextPeriod,
        onScaleChange = onScaleChange,
        listState = listState,
        listTestTag = "expense-analysis-list",
    ) { data ->
        item(key = "expense-hero") {
            ExpenseHero(
                data = data,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
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
                PeriodTrend(
                    data = data,
                    onSelectMonth = onSelectMonth,
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
                    // Header and period selector precede the hero and the ring.
                    scope.launch { listState.animateScrollToItem(4) }
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (data.merchantValues.isNotEmpty()) item(key = "expense-merchants") {
            ExpenseMerchants(
                data = data,
                onOpenTransactions = onOpenTransactions,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
            )
        }
        if (data.otherCurrencyExpenses.isNotEmpty()) item(key = "expense-currencies") {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
                OtherCurrenciesSection(data.otherCurrencyExpenses)
            }
        }
    }
}

@Composable
private fun ExpenseHero(
    data: AnalyticsData,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhfinFieldLabel(stringResource(R.string.analytics_expenses_total))
        WhfinAmount(
            formatMinor(data.expenseMinor, "GEL"),
            symbol = currencySymbol("GEL"),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            averageComparisonText(data.expenseMinor, data.spendingAverageMinor, data.period.scale),
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
    val colored = data.categoryValues.mapIndexed { index, value ->
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
        data.categoryValues.firstOrNull { it.categoryId == filter.categoryId }
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
        )
        if (data.categoryValues.isEmpty()) {
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
            supportingText = stringResource(
                if (data.period.scale == AnalyticsScale.MONTH) {
                    R.string.analytics_expenses_category_hint
                } else {
                    R.string.analytics_expenses_category_hint_year
                },
            ),
        )
        if (data.categoryValues.isNotEmpty()) {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                data.categoryValues.forEachIndexed { index, value ->
                    SpendingCategoryRow(
                        value = value,
                        scale = data.period.scale,
                        totalMinor = data.expenseMinor,
                        color = value.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size],
                        selected = data.trendFilter == AnalyticsTrendFilter.Category(value.categoryId),
                        divider = index < data.categoryValues.lastIndex,
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
    scale: AnalyticsScale,
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
                    // The comparison is a signed number, not a sentence: the section heading
                    // already names what it is measured against, and repeating "above the
                    // previous 3-month average" on every row wrapped each one onto three lines.
                    val delta = averageDeltaText(value.expenseMinor, value.averageExpenseMinor)
                    Text(
                        listOfNotNull(
                            NumberFormat.getPercentInstance().format(percentage),
                            delta,
                        ).joinToString(" · "),
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

/**
 * The rung between a category and each of its payments: who the money actually went to.
 *
 * Two questions land here that the ledger below cannot answer by being reordered. "Which were the
 * big ones" is a sort; "which keep coming back" is not — repetition belongs to the counterparty, and
 * forty rows of the same shop stay forty rows however they are arranged. So the row states both, and
 * one switch decides which of the two orders the list.
 *
 * The scope is whatever the ring and the trend are already showing, so the three never describe
 * different spending, and no row is hidden — including the one nobody has named — so the column
 * still adds up to the total above it.
 */
@Composable
private fun ExpenseMerchants(
    data: AnalyticsData,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = data.trendFilter as? AnalyticsTrendFilter.Category
    val categoryName = when {
        category == null -> null
        data.trendFilterName != null -> data.trendFilterName
        else -> stringResource(R.string.analytics_uncategorized)
    }
    var sortByCount by rememberSaveable { mutableStateOf(false) }
    // A period or a category is a different set of counterparties, so how much of it was unfolded
    // does not carry over to it.
    var expanded by rememberSaveable(data.period, data.trendFilter) { mutableStateOf(false) }

    val ordered = if (sortByCount) {
        // Sums break ties: two counterparties paid the same number of times are still not equal.
        data.merchantValues.sortedWith(
            compareByDescending(AnalyticsMerchantValue::transactionCount)
                .thenByDescending(AnalyticsMerchantValue::expenseMinor),
        )
    } else {
        data.merchantValues
    }
    val visible = if (expanded) ordered else ordered.take(TOP_MERCHANTS)
    val scopeTotal = data.merchantValues.sumOf { it.expenseMinor }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_merchants_title),
            supportingText = categoryName,
        )
        if (data.merchantValues.size > 1) WhfinChoiceRail {
            item {
                WhfinFilterPill(
                    stringResource(R.string.analytics_merchants_sort_amount),
                    selected = !sortByCount,
                    onClick = { sortByCount = false },
                    modifier = Modifier.testTag("expense-merchants-sort-amount"),
                )
            }
            item {
                WhfinFilterPill(
                    stringResource(R.string.analytics_merchants_sort_count),
                    selected = sortByCount,
                    onClick = { sortByCount = true },
                    modifier = Modifier.testTag("expense-merchants-sort-count"),
                )
            }
        }
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            visible.forEachIndexed { index, value ->
                val name = value.name ?: stringResource(R.string.analytics_merchants_unnamed)
                val share = if (scopeTotal <= 0L) 0.0 else value.expenseMinor.toDouble() / scopeTotal
                WhfinLedgerRow(
                    title = name,
                    modifier = Modifier.testTag("expense-merchant-${value.merchantId ?: "none"}"),
                    supportingText = listOf(
                        pluralStringResource(
                            R.plurals.analytics_merchants_payments,
                            value.transactionCount,
                            value.transactionCount,
                        ),
                        NumberFormat.getPercentInstance().format(share),
                    ).joinToString(" · "),
                    trailing = {
                        WhfinAmount(
                            formatMinor(value.expenseMinor, "GEL"),
                            symbol = currencySymbol("GEL"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    onClick = {
                        onOpenTransactions(
                            AnalyticsTransactionsRequest(
                                period = data.period,
                                categoryFilterEnabled = category != null,
                                categoryId = category?.categoryId,
                                filterName = listOfNotNull(categoryName, name).joinToString(" · "),
                                expectedExpenseMinor = value.expenseMinor,
                                merchantFilterEnabled = true,
                                merchantId = value.merchantId,
                            ),
                        )
                    },
                    divider = index < visible.lastIndex,
                )
            }
        }
        if (ordered.size > TOP_MERCHANTS) WhfinButton(
            label = if (expanded) {
                stringResource(R.string.analytics_merchants_show_less)
            } else {
                stringResource(R.string.analytics_merchants_show_all, ordered.size)
            },
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().testTag("expense-merchants-expand"),
            style = WhfinActionStyle.Secondary,
        )
    }
}

/** Enough rows to see the shape of a period without turning the section into a second ledger. */
private const val TOP_MERCHANTS = 6

/** The distance from the comparison base, signed. Null when there is no base to compare with. */
@Composable
private fun averageDeltaText(current: Long, average: Long): String? {
    if (average <= 0L) return null
    val difference = current - average
    if (difference == 0L) return stringResource(R.string.analytics_expenses_at_average_short)
    return formatMinor(difference, "GEL", withSign = true)
}

@Composable
private fun averageComparisonText(current: Long, average: Long, scale: AnalyticsScale): String {
    // A month is read against the previous three; a year has only the year before it to be read against.
    val month = scale == AnalyticsScale.MONTH
    if (average <= 0L) return stringResource(
        if (month) R.string.analytics_expenses_no_average else R.string.analytics_expenses_no_average_year,
    )
    val difference = abs(current - average)
    val amount = formatMinor(difference, "GEL")
    return when {
        current > average -> stringResource(
            if (month) R.string.analytics_expenses_above_average else R.string.analytics_expenses_above_average_year,
            amount,
        )
        current < average -> stringResource(
            if (month) R.string.analytics_expenses_below_average else R.string.analytics_expenses_below_average_year,
            amount,
        )
        else -> stringResource(
            if (month) R.string.analytics_expenses_at_average else R.string.analytics_expenses_at_average_year,
        )
    }
}

private val expensePreviewData = AnalyticsData(
    period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
    incomeMinor = 730_800,
    expenseMinor = 903_600,
    categoryValues = listOf(
        AnalyticsCategoryValue(null, null, null, null, 654_700, 300_200),
        AnalyticsCategoryValue(1, "Groceries", "ShoppingCart", 0xff4f725f.toInt(), 101_100, 93_400),
        AnalyticsCategoryValue(2, "Health & Fitness", "MedicalServices", 0xffc96d4f.toInt(), 56_100, 68_000),
        AnalyticsCategoryValue(3, "Eating out", "Restaurant", 0xff788a67.toInt(), 48_700, 42_300),
        AnalyticsCategoryValue(4, "Transport", "DirectionsBus", 0xffb58b4d.toInt(), 43_000, 38_800),
    ),
    merchantValues = listOf(
        AnalyticsMerchantValue(1, "Agrohub", 61_400, 9),
        AnalyticsMerchantValue(2, "Carrefour", 39_700, 4),
        AnalyticsMerchantValue(3, "Bolt", 24_300, 31),
        AnalyticsMerchantValue(4, "Wolt", 18_900, 12),
        AnalyticsMerchantValue(5, "Silknet", 12_000, 1),
        AnalyticsMerchantValue(6, "Aversi", 9_450, 3),
        AnalyticsMerchantValue(null, null, 7_820, 6),
    ),
    spendingAverageMinor = 549_100,
    trendFilter = AnalyticsTrendFilter.All,
    trendFilterName = null,
    trendValues = (1..12).map { month ->
        AnalyticsMonthValue(YearMonth.of(2026, month), listOf(510, 430, 390, 620, 470, 520, 490, 904, 0, 0, 0, 0)[month - 1] * 1_000L)
    },
    previousTrendExpenseMinor = 640_000,
    unaccountedNetMinor = 0,
    otherCurrencyExpenses = emptyList(),
    pendingCount = 2,
    hasAnyTransactions = true,
)

private fun expensePreviewModel(data: AnalyticsData = expensePreviewData) = AnalyticsUiModel(
    period = data.period,
    canSelectPrevious = true,
    canSelectNext = false,
    state = AnalyticsUiState.Content(data),
)

@Preview(name = "Expenses populated", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "Expenses dark", widthDp = 400, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expenses font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Preview(name = "Expenses compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun ExpenseAnalysisPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpenseAnalysisContent(expensePreviewModel(), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Expenses year", widthDp = 400, heightDp = 1000, showBackground = true)
@Composable
private fun ExpenseAnalysisYearPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpenseAnalysisContent(
                expensePreviewModel(
                    expensePreviewData.copy(
                        period = AnalyticsPeriod.year(YearMonth.of(2026, 7)),
                        expenseMinor = 6_294_000,
                        spendingAverageMinor = 5_810_000,
                    ),
                ),
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
