package dev.whekin.whfin.data

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
import dev.whekin.whfin.ui.analytics.AnalyticsTrendFilter
import dev.whekin.whfin.ui.analytics.calculateAnalytics
import dev.whekin.whfin.ui.feed.buildBaseFeedItems
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A ledger row belongs to the bank's day, wherever its owner happens to be standing.
 *
 * Every case here is set up with the machine's own zone put east of Tbilisi, because that is the
 * only condition under which the two answers differ: on +04 the bug is invisible and any assertion
 * would pass by accident.
 */
class LedgerCalendarTest {

    private val original: TimeZone = TimeZone.getDefault()

    @After fun restoreDefaultZone() = TimeZone.setDefault(original)

    private fun readingFrom(zone: String) = TimeZone.setDefault(TimeZone.getTimeZone(zone))

    /**
     * Late on the given Tbilisi day, past midnight where the reader is standing.
     *
     * How wide that window is depends on how far east the reader is: Almaty is one hour ahead of
     * Tbilisi, so only the last hour of the day crosses over. A reader further east loses more of
     * the evening, which is why the rule cannot be a threshold on the clock.
     */
    private fun tbilisiLateEvening(day: LocalDate): Long =
        ZonedDateTime.of(day.atTime(23, 30), LedgerCalendar.zone).toInstant().toEpochMilli()

    @Test
    fun `an evening row keeps the bank's day when the reader is further east`() {
        readingFrom("Asia/Almaty")
        val at = tbilisiLateEvening(LocalDate.of(2026, 9, 6))

        assertEquals(LocalDate.of(2026, 9, 6), LedgerCalendar.dayOf(at))
        // The divergence this exists to prevent: the reader's own calendar files it under the 7th.
        assertNotEquals(
            LedgerCalendar.dayOf(at),
            Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(),
        )
    }

    @Test
    fun `the feed groups an evening row under the bank's day`() {
        readingFrom("Asia/Almaty")
        val day = LocalDate.of(2026, 9, 6)
        val row = expense(1, tbilisiLateEvening(day))

        val items = buildBaseFeedItems(
            transactions = listOf(row),
            merchants = emptyList(),
            categories = listOf(food),
            accounts = emptyList(),
            masksByAccount = emptyMap(),
            zone = LedgerCalendar.zone,
        )

        assertEquals(listOf(day), items.map { it.day })
    }

    @Test
    fun `a Sunday-evening row stays in the month the statement files it under`() {
        readingFrom("Asia/Almaty")
        // The consequence that outlives a single day: on the last evening of a month the reader's
        // calendar moves the row into the next one, and the month's total stops matching the bank's.
        val row = expense(1, tbilisiLateEvening(LocalDate.of(2026, 8, 31)))

        val august = calculateAnalytics(
            transactions = listOf(row),
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 8)),
            trendFilter = AnalyticsTrendFilter.All,
            today = LocalDate.of(2026, 9, 20),
        )
        val september = calculateAnalytics(
            transactions = listOf(row),
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 9)),
            trendFilter = AnalyticsTrendFilter.All,
            today = LocalDate.of(2026, 9, 20),
        )

        assertEquals(4_000L, august.expenseMinor)
        assertEquals(0L, september.expenseMinor)
    }

    private val food = CategoryEntity(
        id = 1, name = "Food", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0,
    )

    private fun expense(id: Long, occurredAt: Long) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = -4_000,
        currency = "GEL",
        gelValueMinor = -4_000,
        occurredAt = occurredAt,
        categoryId = food.id,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
    )
}
