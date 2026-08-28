package dev.whekin.whfin.data.income

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeExpectationsTest {

    private val zone = ZoneId.of("UTC")
    private val august = YearMonth.of(2026, 8)

    private fun source(
        startedOn: LocalDate = LocalDate.of(2026, 6, 1),
        endedOn: LocalDate? = null,
        accountId: Long? = 1,
    ) = IncomeSourceEntity(
        id = 1,
        label = "Salary",
        amountMinor = 270_000,
        currency = "USDT",
        accountId = accountId,
        expectedDayFrom = 5,
        expectedDayTo = 10,
        startedOn = startedOn.toEpochDay(),
        endedOn = endedOn?.toEpochDay(),
        createdAt = 0,
    )

    private fun arrival(day: Int, amount: Long = 270_000, accountId: Long = 1) = TransactionEntity(
        id = day.toLong(),
        accountId = accountId,
        amountMinor = amount,
        currency = "USDT",
        occurredAt = LocalDate.of(2026, 8, day).atStartOfDay(zone).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
        createdAt = 0,
    )

    @Test
    fun `a payment inside the month is reported beside what was declared`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertEquals(270_000L, result.receivedMinor)
        assertTrue(result.arrived)
        assertFalse(result.overdue)
    }

    @Test
    fun `passing the usual payday but not the deadline is not called late`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            emptyList(),
            august,
            LocalDate.of(2026, 8, 7),
            zone,
        ).single()

        assertFalse(result.arrived)
        assertFalse(result.overdue)
    }

    @Test
    fun `a payday deadline that passed with nothing received is late`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            emptyList(),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertTrue(result.overdue)
    }

    /** The declaration is an anchor, not a truth: what actually arrived is reported as it is. */
    @Test
    fun `a payment that differs from the declaration is neither hidden nor corrected`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6, amount = 190_000)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertEquals(190_000L, result.receivedMinor)
        assertEquals(270_000L, result.source.amountMinor)
    }

    @Test
    fun `money on another account is not counted as this source arriving`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6, accountId = 2)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertFalse(result.arrived)
    }

    @Test
    fun `a declaration does not describe the months before it started`() {
        val fromJuly = source(startedOn = LocalDate.of(2026, 7, 29))

        assertFalse(IncomeExpectations.covers(fromJuly, YearMonth.of(2026, 6)))
        assertTrue(IncomeExpectations.covers(fromJuly, YearMonth.of(2026, 7)))
        assertTrue(IncomeExpectations.covers(fromJuly, august))
    }

    @Test
    fun `an ended era stops describing the months after it`() {
        val cash = source(
            startedOn = LocalDate.of(2024, 1, 1),
            endedOn = LocalDate.of(2026, 6, 30),
        )

        assertTrue(IncomeExpectations.covers(cash, YearMonth.of(2026, 6)))
        assertFalse(IncomeExpectations.covers(cash, YearMonth.of(2026, 7)))
    }

    /** A wallet that has not been added yet has no account to match against, and says so quietly. */
    @Test
    fun `a source without an account never claims an arrival`() {
        val result = IncomeExpectations.of(
            listOf(source(accountId = null)),
            listOf(arrival(6)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertFalse(result.arrived)
    }
}
