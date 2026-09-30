package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.ui.analytics.AnalyticsBaseline
import dev.whekin.whfin.ui.analytics.AnalyticsCategoryChange
import dev.whekin.whfin.ui.analytics.AnalyticsData
import dev.whekin.whfin.ui.analytics.AnalyticsMonthValue
import dev.whekin.whfin.ui.analytics.AnalyticsPace
import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
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

    @Test
    fun `no category spending so far is not projected as a promise of zero`() {
        val data = analyticsData(
            pace = AnalyticsPace(20, 30, 100_000, 100_000),
            categoryChanges = listOf(change(0, 20_000, "Travel")),
        )

        assertTrue(deriveHomeInsights(data).isEmpty())
    }

    private fun analyticsData(
        pace: AnalyticsPace,
        categoryChanges: List<AnalyticsCategoryChange>,
    ) = AnalyticsData(
        period = AnalyticsPeriod.month(YearMonth.of(2026, 8)),
        incomeMinor = 0,
        expenseMinor = pace.projectedExpenseMinor * pace.daysElapsed / pace.daysTotal,
        categoryValues = emptyList(),
        trendFilter = AnalyticsTrendFilter.All,
        trendFilterName = null,
        trendValues = listOf(AnalyticsMonthValue(YearMonth.of(2026, 8), 0)),
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
        pace = pace,
        categoryChanges = categoryChanges,
        baseline = AnalyticsBaseline(
            periods = (5..7).map { AnalyticsPeriod.month(YearMonth.of(2026, it)) },
            requestedPeriods = 3,
            expenseMinor = pace.typicalWholeExpenseMinor,
            wholeExpenseMinor = pace.typicalWholeExpenseMinor,
        ),
    )

    private fun change(
        expenseMinor: Long,
        typicalExpenseMinor: Long,
        name: String? = "Groceries",
    ) = AnalyticsCategoryChange(
        categoryId = 1,
        name = name,
        icon = "ShoppingCart",
        color = null,
        expenseMinor = expenseMinor,
        typicalExpenseMinor = typicalExpenseMinor,
    )

    @Test
    fun `without a recorded baseline home says nothing about the usual`() {
        val data = analyticsData(
            pace = AnalyticsPace(10, 30, 180_000, 100_000),
            categoryChanges = listOf(change(40_000, 30_000, "Eating out")),
        ).copy(baseline = AnalyticsBaseline(emptyList(), 3, 0L, 0L))

        assertTrue(deriveHomeInsights(data).isEmpty())
    }
}
