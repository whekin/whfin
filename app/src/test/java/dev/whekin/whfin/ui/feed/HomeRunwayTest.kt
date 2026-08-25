package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.ui.analytics.AnalyticsData
import dev.whekin.whfin.ui.analytics.AnalyticsMonthValue
import dev.whekin.whfin.ui.analytics.AnalyticsPace
import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
import dev.whekin.whfin.ui.analytics.AnalyticsScale
import dev.whekin.whfin.ui.analytics.AnalyticsTrendFilter
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRunwayTest {

    private val today = LocalDate.of(2026, 8, 25)

    @Test
    fun `runway divides spendable money by the projected daily rate`() {
        // 310 000 minor projected over 31 days is 10 000 a day; 120 000 lasts twelve of them.
        val runway = homeRunway(
            spendablePivotMinor = 120_000,
            analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
            incomeSources = emptyList(),
            today = today,
        )

        assertNotNull(runway)
        assertEquals(12, runway!!.daysLeft)
        assertEquals(10_000L, runway.dailyBurnMinor)
        assertNull(runway.nextIncome)
        assertFalse(runway.shortOfIncome)
    }

    @Test
    fun `a month too young to project says nothing`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = 120_000,
                analytics = analytics(AnalyticsPace(3, 31, 310_000, 300_000)),
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `nothing spendable is not a runway of zero days`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = 0,
                analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
                incomeSources = emptyList(),
                today = today,
            ),
        )
        assertNull(
            homeRunway(
                spendablePivotMinor = null,
                analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `a comfortable runway stays quiet`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = 2_000_000,
                analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `money that runs out before the declared window closes is called short`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
            incomeSources = listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
            today = today,
        )

        assertNotNull(runway)
        assertEquals(4, runway!!.daysLeft)
        assertTrue(runway.shortOfIncome)
        assertEquals(LocalDate.of(2026, 9, 5), runway.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 9, 10), runway.nextIncome?.to)
    }

    @Test
    fun `a declared payday does not make a comfortable runway speak`() {
        // A declared window is never further out than the next month, so money that comfortably
        // outlasts the quiet threshold has already outlasted the wait for it.
        assertNull(
            homeRunway(
                spendablePivotMinor = 500_000,
                analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
                incomeSources = listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
                today = today,
            ),
        )
    }

    @Test
    fun `an open window is the answer until it closes`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
            incomeSources = listOf(source(expectedDayFrom = 20, expectedDayTo = 27)),
            today = today,
        )

        assertEquals(LocalDate.of(2026, 8, 20), runway?.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 8, 27), runway?.nextIncome?.to)
    }

    @Test
    fun `a source whose era ended does not promise a payday`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            analytics = analytics(AnalyticsPace(25, 31, 310_000, 300_000)),
            incomeSources = listOf(
                source(
                    expectedDayFrom = 5,
                    expectedDayTo = 10,
                    endedOn = LocalDate.of(2026, 6, 30).toEpochDay(),
                ),
            ),
            today = today,
        )

        assertNull(runway?.nextIncome)
        assertFalse(runway!!.shortOfIncome)
    }

    @Test
    fun `a month-end window is clamped to a day the month actually has`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            analytics = analytics(
                AnalyticsPace(25, 28, 280_000, 300_000),
                month = YearMonth.of(2026, 2),
            ),
            incomeSources = listOf(source(expectedDayFrom = 30, expectedDayTo = 31)),
            today = LocalDate.of(2026, 2, 25),
        )

        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.to)
    }

    private fun source(
        expectedDayFrom: Int,
        expectedDayTo: Int,
        endedOn: Long? = null,
    ) = IncomeSourceEntity(
        id = 1,
        label = "Salary",
        amountMinor = 500_000,
        currency = "GEL",
        accountId = 1,
        expectedDayFrom = expectedDayFrom,
        expectedDayTo = expectedDayTo,
        startedOn = LocalDate.of(2025, 1, 1).toEpochDay(),
        endedOn = endedOn,
        createdAt = 0,
    )

    private fun analytics(
        pace: AnalyticsPace,
        month: YearMonth = YearMonth.of(2026, 8),
    ) = AnalyticsData(
        period = AnalyticsPeriod.month(month),
        incomeMinor = 0,
        expenseMinor = pace.projectedExpenseMinor * pace.daysElapsed / pace.daysTotal,
        categoryValues = emptyList(),
        trendFilter = AnalyticsTrendFilter.All,
        trendFilterName = null,
        trendValues = listOf(AnalyticsMonthValue(month, 0)),
        previousTrendExpenseMinor = 0,
        unaccountedNetMinor = 0,
        otherCurrencyExpenses = emptyList(),
        pendingCount = 0,
        hasAnyTransactions = true,
        pace = pace,
    ).also { require(it.period.scale == AnalyticsScale.MONTH) }
}
