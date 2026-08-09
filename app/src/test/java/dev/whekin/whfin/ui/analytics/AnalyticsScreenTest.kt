package dev.whekin.whfin.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rangeAndCategorySelectionUpdateAnalyticsControls() {
        var range by mutableStateOf(1)
        var filter by mutableStateOf<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData.copy(
                            categoryRangeMonths = range,
                            trendFilter = filter,
                            trendFilterName = (filter as? AnalyticsTrendFilter.Category)?.let { "Food" },
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {},
                        onRangeChange = { range = it },
                        onShowAllTrend = { filter = AnalyticsTrendFilter.All },
                        onShowCategoryTrend = { filter = AnalyticsTrendFilter.Category(it) },
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(4)
        compose.onNodeWithText("3 mo").performClick()
        compose.runOnIdle { assertEquals(3, range) }

        compose.onNodeWithTag("analytics-category-1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AnalyticsTrendFilter.Category(1), filter) }
        compose.onAllNodesWithText("Food").assertCountEquals(2)
    }

    @Test
    fun trendBarSelectionUpdatesAmountAndOpensMatchingTransactions() {
        var opened: AnalyticsTransactionsRequest? = null
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData.copy(
                            selectedMonth = month,
                            trendFilter = AnalyticsTrendFilter.Category(1),
                            trendFilterName = "Food",
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = { month = it },
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(5)
        compose.onNodeWithTag("whfin-monthly-bar-10").performClick()
        compose.onNodeWithTag("analytics-selected-trend-amount").assertTextEquals("110.00 ₾")
        compose.waitForIdle()
        compose.onNodeWithTag("analytics-view-transactions").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 1_000) { opened != null }

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 6), opened?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(1L, opened?.categoryId)
            assertEquals(11_000L, opened?.expectedExpenseMinor)
        }
    }

    @Test
    fun categoryDriverOpensCurrentMonthTransactions() {
        var opened: AnalyticsTransactionsRequest? = null
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData,
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {},
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(3)
        compose.onNodeWithTag("analytics-change-2").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 7), opened?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(2L, opened?.categoryId)
            assertEquals("Transport", opened?.filterName)
            assertEquals(30_000L, opened?.expectedExpenseMinor)
        }
    }

    @Test
    fun expenseMetricOpensFocusedSpendingAnalysis() {
        var opened = false
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData,
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {},
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenExpenses = { opened = true },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-open-expenses").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun focusedSpendingSelectsCategoryAndShowsItsComparison() {
        var filter by mutableStateOf<AnalyticsTrendFilter>(AnalyticsTrendFilter.All)
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        data = contentData.copy(
                            spendingAverageMinor = 60_000,
                            spendingCategoryValues = listOf(
                                AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000, 40_000),
                                AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 35_000),
                            ),
                            trendFilter = filter,
                            trendFilterName = (filter as? AnalyticsTrendFilter.Category)?.let { "Food" },
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {},
                        onShowAllTrend = { filter = AnalyticsTrendFilter.All },
                        onShowCategoryTrend = { filter = AnalyticsTrendFilter.Category(it) },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(4)
        compose.onNodeWithText("100.00 ₾ above the previous 3-month average").assertExists()
        compose.onNodeWithTag("expense-category-1").performClick()
        compose.runOnIdle { assertEquals(AnalyticsTrendFilter.Category(1), filter) }
    }

    @Test
    fun lastTwelveMonthsSelectionRefreshesStatisticsCategories() {
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            val category = if (month == YearMonth.of(2026, 7)) {
                AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000)
            } else {
                AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000)
            }
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData.copy(
                            selectedMonth = month,
                            categoryExpenseMinor = category.expenseMinor,
                            categoryValues = listOf(category),
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = { month = it },
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(4)
        compose.onNodeWithTag("analytics-category-1").assertExists()
        compose.onNodeWithTag("analytics-list").performScrollToIndex(5)
        compose.onNodeWithTag("whfin-monthly-bar-10").performClick()
        compose.onNodeWithTag("analytics-list").performScrollToIndex(4)
        compose.onNodeWithTag("analytics-category-2").assertExists()
        compose.onNodeWithTag("analytics-category-1").assertDoesNotExist()
    }

    @Test
    fun focusedSpendingMonthBarRefreshesRingAndCategorySummary() {
        var month by mutableStateOf(YearMonth.of(2026, 7))
        compose.setContent {
            val category = if (month == YearMonth.of(2026, 7)) {
                AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000, 40_000)
            } else {
                AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 35_000)
            }
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        data = contentData.copy(
                            selectedMonth = month,
                            expenseMinor = category.expenseMinor,
                            spendingCategoryValues = listOf(category),
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = { month = it },
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(4)
        compose.onNodeWithText("Food").assertExists()
        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(3)
        compose.onNodeWithTag("whfin-monthly-bar-10").performClick()
        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(4)
        compose.onNodeWithText("Transport").assertExists()
        compose.onNodeWithText("Food").assertDoesNotExist()
    }

    @Test
    fun statisticsKeepsLaterTrendMonthReachableAfterSelectingEarlierMonth() {
        var month by mutableStateOf(YearMonth.of(2026, 8))
        var trendEnd by mutableStateOf(YearMonth.of(2026, 8))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData.copy(
                            selectedMonth = month,
                            trendValues = trendEndingAt(trendEnd),
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {
                            month = it
                            trendEnd = trendWindowEndAfterSelecting(trendEnd, it)
                        },
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenExpenses = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(5)
        compose.onNodeWithContentDescription("July 2026, 2,132.05 ₾").performClick()
        compose.onNodeWithContentDescription("August 2026, 321.54 ₾").assertExists().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 8), month) }
    }

    @Test
    fun spendingKeepsLaterTrendMonthReachableAfterSelectingEarlierMonth() {
        var month by mutableStateOf(YearMonth.of(2026, 8))
        var trendEnd by mutableStateOf(YearMonth.of(2026, 8))
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExpenseAnalysisContent(
                        data = contentData.copy(
                            selectedMonth = month,
                            trendValues = trendEndingAt(trendEnd),
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {
                            month = it
                            trendEnd = trendWindowEndAfterSelecting(trendEnd, it)
                        },
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("expense-analysis-list").performScrollToIndex(3)
        compose.onNodeWithContentDescription("July 2026, 2,132.05 ₾").performClick()
        compose.onNodeWithContentDescription("August 2026, 321.54 ₾").assertExists().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 8), month) }
    }

    private fun trendEndingAt(end: YearMonth): List<AnalyticsMonthValue> =
        (11L downTo 0L).map { monthsAgo ->
            val month = end.minusMonths(monthsAgo)
            AnalyticsMonthValue(
                month = month,
                expenseMinor = when (month) {
                    YearMonth.of(2026, 7) -> 213_205L
                    YearMonth.of(2026, 8) -> 32_154L
                    else -> 0L
                },
            )
        }

    private val contentData = AnalyticsData(
        selectedMonth = YearMonth.of(2026, 7),
        incomeMinor = 400_000,
        expenseMinor = 80_000,
        categoryRangeMonths = 1,
        categoryExpenseMinor = 80_000,
        categoryValues = listOf(
            AnalyticsCategoryValue(1, "Food", "ShoppingCart", 0xff4f725f.toInt(), 50_000),
            AnalyticsCategoryValue(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000),
        ),
        trendFilter = AnalyticsTrendFilter.All,
        trendFilterName = null,
        trendValues = (0L..11L).map {
            AnalyticsMonthValue(YearMonth.of(2025, 8).plusMonths(it), (it + 1) * 1_000L)
        },
        previousTrendExpenseMinor = 6_000,
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
        pace = AnalyticsPace(
            daysElapsed = 20,
            daysInMonth = 31,
            projectedExpenseMinor = 124_000,
            previousMonthExpenseMinor = 70_000,
        ),
        categoryChanges = listOf(
            AnalyticsCategoryChange(2, "Transport", "DirectionsBus", 0xffc96d4f.toInt(), 30_000, 20_000),
        ),
    )
}
