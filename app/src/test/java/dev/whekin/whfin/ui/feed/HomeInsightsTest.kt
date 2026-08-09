package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.ui.analytics.AnalyticsCategoryChange
import dev.whekin.whfin.ui.analytics.AnalyticsData
import dev.whekin.whfin.ui.analytics.AnalyticsMonthValue
import dev.whekin.whfin.ui.analytics.AnalyticsPace
import dev.whekin.whfin.ui.analytics.AnalyticsTrendFilter
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInsightsTest {
    @Test
    fun `early month does not pretend to know the spending pace`() {
        val data = analyticsData(
            pace = AnalyticsPace(4, 31, 310_000, 100_000),
            categoryChanges = listOf(change(80_000, 20_000)),
        )

        assertTrue(deriveHomeInsights(data).isEmpty())
    }

    @Test
    fun `home explains the total pace and its largest projected driver`() {
        val data = analyticsData(
            pace = AnalyticsPace(10, 30, 180_000, 100_000),
            categoryChanges = listOf(change(40_000, 30_000, "Eating out")),
        )

        assertEquals(
            listOf(
                HomeInsight.SpendingPace(180_000, 100_000),
                HomeInsight.CategoryDriver("Eating out", 120_000, 30_000),
            ),
            deriveHomeInsights(data),
        )
    }

    @Test
    fun `normal variation stays quiet`() {
        val data = analyticsData(
            pace = AnalyticsPace(15, 30, 103_000, 100_000),
            categoryChanges = listOf(change(7_500, 15_000)),
        )

        assertTrue(deriveHomeInsights(data).isEmpty())
    }

    private fun analyticsData(
        pace: AnalyticsPace,
        categoryChanges: List<AnalyticsCategoryChange>,
    ) = AnalyticsData(
        selectedMonth = YearMonth.of(2026, 8),
        incomeMinor = 0,
        expenseMinor = pace.projectedExpenseMinor * pace.daysElapsed / pace.daysInMonth,
        categoryRangeMonths = 1,
        categoryExpenseMinor = 0,
        categoryValues = emptyList(),
        trendFilter = AnalyticsTrendFilter.All,
        trendFilterName = null,
        trendValues = listOf(AnalyticsMonthValue(YearMonth.of(2026, 8), 0)),
        previousTrendExpenseMinor = 0,
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
        pace = pace,
        categoryChanges = categoryChanges,
    )

    private fun change(
        expenseMinor: Long,
        previousExpenseMinor: Long,
        name: String? = "Groceries",
    ) = AnalyticsCategoryChange(
        categoryId = 1,
        name = name,
        icon = "ShoppingCart",
        color = null,
        expenseMinor = expenseMinor,
        previousExpenseMinor = previousExpenseMinor,
    )
}
