package dev.whekin.whfin.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
                        onRangeChange = { range = it },
                        onShowAllTrend = { filter = AnalyticsTrendFilter.All },
                        onShowCategoryTrend = { filter = AnalyticsTrendFilter.Category(it) },
                        onOpenTransactions = {},
                    )
                }
            }
        }

        compose.onNodeWithText("3 months").performClick()
        compose.runOnIdle { assertEquals(3, range) }

        compose.onNodeWithTag("analytics-category-1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AnalyticsTrendFilter.Category(1), filter) }
        compose.onAllNodesWithText("Food").assertCountEquals(2)
    }

    @Test
    fun trendBarSelectionUpdatesAmountAndOpensMatchingTransactions() {
        var opened: AnalyticsTransactionsRequest? = null
        compose.setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AnalyticsContent(
                        data = contentData.copy(
                            trendFilter = AnalyticsTrendFilter.Category(1),
                            trendFilterName = "Food",
                        ),
                        onBack = {},
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onRangeChange = {},
                        onShowAllTrend = {},
                        onShowCategoryTrend = {},
                        onOpenTransactions = { opened = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("analytics-list").performScrollToIndex(3)
        compose.onNodeWithTag("whfin-monthly-bar-5").performClick()
        compose.onNodeWithTag("analytics-selected-trend-amount").assertTextEquals("60.00 ₾")
        compose.waitForIdle()
        compose.onNodeWithTag("analytics-view-transactions").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 1_000) { opened != null }

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 6), opened?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(1L, opened?.categoryId)
            assertEquals(6_000L, opened?.expectedExpenseMinor)
        }
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
        trendValues = (1..12).map { AnalyticsMonthValue(YearMonth.of(2026, it), it * 1_000L) },
        previousTrendExpenseMinor = 6_000,
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
    )
}
