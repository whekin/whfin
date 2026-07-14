package dev.whekin.whfin.ui.analytics

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinDistributionBar
import dev.whekin.whfin.core.ui.WhfinDistributionSegment
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinMonthlyBar
import dev.whekin.whfin.core.ui.WhfinMonthlyBarChart
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.core.ui.WhfinThemeTokens
import dev.whekin.whfin.ui.CategoryIcons
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun AnalyticsScreen(
    onBack: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
    viewModel: AnalyticsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    when (val value = state) {
        AnalyticsUiState.Loading -> AnalyticsPeriodState(
            month = YearMonth.now(),
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.analytics_title),
            body = stringResource(R.string.analytics_loading),
            onBack = onBack,
            onPreviousMonth = {},
            onNextMonth = {},
        )
        is AnalyticsUiState.Empty -> AnalyticsPeriodState(
            month = value.selectedMonth,
            state = WhfinPaneState.Empty,
            title = stringResource(R.string.analytics_empty_title),
            body = stringResource(R.string.analytics_empty_body),
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
        )
        is AnalyticsUiState.Error -> AnalyticsPeriodState(
            month = value.selectedMonth,
            state = WhfinPaneState.Error,
            title = stringResource(R.string.analytics_error_title),
            body = stringResource(R.string.analytics_error_body),
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
        )
        is AnalyticsUiState.Content -> AnalyticsContent(
            data = value.data,
            onBack = onBack,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onRangeChange = viewModel::setCategoryRange,
            onShowAllTrend = viewModel::showAllExpensesTrend,
            onShowCategoryTrend = viewModel::showCategoryTrend,
            onOpenTransactions = onOpenTransactions,
        )
    }
}

@Composable
private fun AnalyticsPeriodState(
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
            item(key = "analytics-header") { AnalyticsHeader(onBack) }
            item(key = "month-selector") {
                MonthSelector(month, onPreviousMonth, onNextMonth, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
            item(key = "state") { WhfinStatePane(state, title, body, Modifier.fillMaxWidth()) }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun AnalyticsContent(
    data: AnalyticsData,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onShowAllTrend: () -> Unit,
    onShowCategoryTrend: (Long?) -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedTrendMonthNumber by rememberSaveable(data.selectedMonth.year, data.trendFilter) {
        mutableIntStateOf(data.selectedMonth.monthValue)
    }
    val selectedTrendMonth = remember(data.selectedMonth.year, selectedTrendMonthNumber) {
        YearMonth.of(data.selectedMonth.year, selectedTrendMonthNumber)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("analytics-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = navigationBottom + 28.dp),
        ) {
            item(key = "analytics-header") { AnalyticsHeader(onBack) }
            item(key = "month-result") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)) {
                    MonthResult(data, onPreviousMonth, onNextMonth)
                }
            }
            item(key = "categories") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    CategoryBreakdown(
                        data = data,
                        onRangeChange = onRangeChange,
                        onCategoryClick = { categoryId ->
                            onShowCategoryTrend(categoryId)
                            scope.launch { listState.animateScrollToItem(TREND_ITEM_INDEX) }
                        },
                    )
                }
            }
            item(key = "trend") {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    YearTrend(
                        data = data,
                        selectedMonth = selectedTrendMonth,
                        onSelectMonth = { selectedTrendMonthNumber = it.monthValue },
                        onShowAllTrend = onShowAllTrend,
                        onOpenTransactions = onOpenTransactions,
                    )
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
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AnalyticsHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MonthResult(data: AnalyticsData, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MonthSelector(data.selectedMonth, onPreviousMonth, onNextMonth)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            WhfinSectionLabel(stringResource(R.string.analytics_month_result))
            Text(
                formatMinor(data.deltaMinor, "GEL", withSign = true),
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = if (data.deltaMinor >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            Text(
                stringResource(R.string.analytics_net),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AnalyticsMetric(
                stringResource(R.string.summary_income),
                formatMinor(data.incomeMinor, "GEL"),
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
            )
            AnalyticsMetric(
                stringResource(R.string.summary_expenses),
                formatMinor(data.expenseMinor, "GEL"),
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
            )
        }
        if (data.pendingCount > 0) Text(
            stringResource(R.string.analytics_pending, data.pendingCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WhfinIconButton(
            Icons.Default.ChevronLeft,
            stringResource(R.string.analytics_previous_month),
            onPreviousMonth,
            outlined = false,
        )
        Text(
            monthTitle(month),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        WhfinIconButton(
            Icons.Default.ChevronRight,
            stringResource(R.string.analytics_next_month),
            onNextMonth,
            outlined = false,
            enabled = month < YearMonth.now(),
        )
    }
}

@Composable
private fun AnalyticsMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryBreakdown(
    data: AnalyticsData,
    onRangeChange: (Int) -> Unit,
    onCategoryClick: (Long?) -> Unit,
) {
    val fallbackColors = listOf(
        WhfinThemeTokens.colors.bottle,
        WhfinThemeTokens.colors.clay,
        MaterialTheme.colorScheme.secondary,
        WhfinThemeTokens.colors.sage,
        MaterialTheme.colorScheme.tertiary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(
            title = stringResource(R.string.analytics_categories),
            supportingText = stringResource(R.string.analytics_categories_hint),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 3, 6, 12).forEach { months ->
                WhfinFilterPill(
                    label = stringResource(
                        when (months) {
                            1 -> R.string.analytics_range_1
                            3 -> R.string.analytics_range_3
                            6 -> R.string.analytics_range_6
                            else -> R.string.analytics_range_12
                        },
                    ),
                    selected = data.categoryRangeMonths == months,
                    onClick = { onRangeChange(months) },
                )
            }
        }
        if (data.categoryValues.isEmpty()) {
            Text(
                stringResource(R.string.analytics_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val categoryColors = data.categoryValues.mapIndexed { index, value ->
                value.color?.let(::Color) ?: fallbackColors[index % fallbackColors.size]
            }
            WhfinDistributionBar(
                data.categoryValues.mapIndexed { index, value ->
                    WhfinDistributionSegment(value.expenseMinor.toFloat(), categoryColors[index])
                },
            )
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                data.categoryValues.forEachIndexed { index, value ->
                    CategoryRow(
                        value = value,
                        totalMinor = data.categoryExpenseMinor,
                        color = categoryColors[index],
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
private fun CategoryRow(
    value: AnalyticsCategoryValue,
    totalMinor: Long,
    color: Color,
    selected: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val percentage = if (totalMinor <= 0L) 0.0 else value.expenseMinor.toDouble() / totalMinor * 100.0
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("analytics-category-${value.categoryId ?: "none"}"),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f) else Color.Transparent,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 16.dp, vertical = 10.dp),
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
                Column(Modifier.weight(1f)) {
                    Text(value.name ?: stringResource(R.string.analytics_uncategorized), style = MaterialTheme.typography.titleMedium)
                    Text(
                        NumberFormat.getPercentInstance().format(percentage / 100.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatMinor(value.expenseMinor, "GEL"),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
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
private fun YearTrend(
    data: AnalyticsData,
    selectedMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    onShowAllTrend: () -> Unit,
    onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit,
) {
    val locale = currentLocale()
    val filterName = when (val filter = data.trendFilter) {
        AnalyticsTrendFilter.All -> stringResource(R.string.analytics_all_expenses)
        is AnalyticsTrendFilter.Category -> data.trendFilterName ?: stringResource(R.string.analytics_uncategorized)
    }
    val selectedValue = data.trendValues.firstOrNull { it.month == selectedMonth }?.expenseMinor ?: 0L
    val previousValue = data.trendValues.firstOrNull { it.month == selectedMonth.minusMonths(1) }?.expenseMinor ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WhfinSectionHeader(title = stringResource(R.string.analytics_year_trend), supportingText = filterName)
        if (data.trendFilter is AnalyticsTrendFilter.Category) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhfinFilterPill(stringResource(R.string.analytics_all_expenses), selected = false, onClick = onShowAllTrend)
            WhfinFilterPill(filterName, selected = true, onClick = {})
        }
        Text(
            stringResource(R.string.analytics_trend_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WhfinMonthlyBarChart(
                    bars = data.trendValues.map { point ->
                        WhfinMonthlyBar(
                            label = point.month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                            value = point.expenseMinor,
                            amountDescription = formatMinor(point.expenseMinor, "GEL"),
                            selected = point.month == selectedMonth,
                        )
                    },
                    onBarClick = { index -> data.trendValues.getOrNull(index)?.month?.let(onSelectMonth) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(monthTitle(selectedMonth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatMinor(selectedValue, "GEL"),
                            modifier = Modifier.testTag("analytics-selected-trend-amount"),
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                    Text(
                        comparisonText(selectedValue, previousValue),
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
                                month = selectedMonth,
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
                Text(
                    formatMinor(amountMinor, "GEL", withSign = true),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun OtherCurrenciesSection(values: List<AnalyticsCurrencyValue>) {
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
                    Text(
                        formatMinor(value.expenseMinor, value.currency),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
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
private fun comparisonText(current: Long, previous: Long): String {
    if (previous <= 0L) return stringResource(R.string.analytics_no_previous)
    val percent = abs(current - previous).toDouble() / previous * 100.0
    val formatted = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(percent / 100.0)
    return when {
        current > previous -> stringResource(R.string.analytics_more_than_previous, formatted)
        current < previous -> stringResource(R.string.analytics_less_than_previous, formatted)
        else -> stringResource(R.string.analytics_same_as_previous)
    }
}

@Composable
internal fun monthTitle(month: YearMonth): String {
    val locale = currentLocale()
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return "${name.replaceFirstChar { it.titlecase(locale) }} ${month.year}"
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private const val TREND_ITEM_INDEX = 3

private val previewData = AnalyticsData(
    selectedMonth = YearMonth.of(2026, 7),
    incomeMinor = 730_800,
    expenseMinor = 109_127,
    categoryRangeMonths = 1,
    categoryExpenseMinor = 109_127,
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
)

@Preview(name = "Analytics populated", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "Analytics dark", widthDp = 400, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Analytics font 1.5", widthDp = 400, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Preview(name = "Analytics compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsContent(previewData, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Analytics empty", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun AnalyticsEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsPeriodState(
                YearMonth.of(2026, 7),
                WhfinPaneState.Empty,
                "No statistics for this period",
                "Choose another month or import a statement to build a trend.",
                {},
                {},
                {},
            )
        }
    }
}

@Preview(name = "Analytics loading", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsLoadingPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsPeriodState(
                YearMonth.of(2026, 7),
                WhfinPaneState.Loading,
                "Statistics",
                "Calculating statistics…",
                {},
                {},
                {},
            )
        }
    }
}

@Preview(name = "Analytics error", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun AnalyticsErrorPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnalyticsPeriodState(
                YearMonth.of(2026, 7),
                WhfinPaneState.Error,
                "Statistics could not be calculated",
                "Your transactions are unchanged. Return and try opening statistics again.",
                {},
                {},
                {},
            )
        }
    }
}
