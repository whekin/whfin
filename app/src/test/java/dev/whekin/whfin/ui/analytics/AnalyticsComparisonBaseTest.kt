package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A number is only compared with a number measured the same way.
 *
 * On the fifth of a month, "88% less than the previous month" was five days measured against thirty
 * and printed as a change in behaviour. Every base a running period is read against now stops at the
 * same day of its own period; a projection, which does reach the end, keeps a whole period.
 */
class AnalyticsComparisonBaseTest {

    private val zone = ZoneOffset.UTC
    private val categories = listOf(
        CategoryEntity(id = 1, name = "Eating out", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0),
    )

    @Test
    fun `a running month is compared against the same days of the month before`() {
        val transactions = listOf(
            expense(1, LocalDate.of(2026, 8, 3), 10_000),
            expense(2, LocalDate.of(2026, 8, 20), 90_000),
            expense(3, LocalDate.of(2026, 9, 2), 20_000),
        )

        val data = calculateAnalytics(
            transactions = transactions,
            categories = categories,
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 9)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 9, 5),
        )

        assertEquals(5, data.comparisonDays)
        // Only the 3 August payment falls inside the first five days; the 20 August one does not.
        assertEquals(10_000L, data.previousTrendExpenseMinor)
        assertEquals(10_000L, data.categoryChanges.single().previousExpenseMinor)
        // The projection still reaches month end, so its own base stays the whole month.
        assertEquals(100_000L, data.pace?.previousPeriodExpenseMinor)
        assertEquals(100_000L, data.categoryChanges.single().previousWholeExpenseMinor)
    }

    @Test
    fun `a finished month keeps whole periods on both sides`() {
        val transactions = listOf(
            expense(1, LocalDate.of(2026, 7, 3), 10_000),
            expense(2, LocalDate.of(2026, 7, 20), 90_000),
            expense(3, LocalDate.of(2026, 8, 2), 20_000),
        )

        val data = calculateAnalytics(
            transactions = transactions,
            categories = categories,
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 8)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 9, 5),
        )

        assertEquals(null, data.comparisonDays)
        assertEquals(100_000L, data.previousTrendExpenseMinor)
        assertEquals(null, data.pace)
    }

    private fun expense(id: Long, day: LocalDate, minor: Long) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = -minor,
        currency = "GEL",
        gelValueMinor = -minor,
        occurredAt = day.atStartOfDay(zone).toInstant().toEpochMilli(),
        categoryId = 1,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
    )
}
