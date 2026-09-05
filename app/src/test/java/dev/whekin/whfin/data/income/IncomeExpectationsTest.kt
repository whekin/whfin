package dev.whekin.whfin.data.income

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.IncomeSourcePaymentEntity
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

    @Test fun `a source only counts its receiving era and never balance corrections`() {
        val era = source(startedOn = LocalDate.of(2026, 8, 10), endedOn = LocalDate.of(2026, 8, 20))
        val rows = listOf(arrival(5), arrival(15), arrival(25), arrival(16).copy(source = TxSource.ADJUSTMENT))
        val result = IncomeExpectations.of(listOf(era), rows, august, LocalDate.of(2026, 8, 21), zone,
            confirming(5, 15, 25, 16)).single()
        assertEquals(1, result.receivedCount)
        assertEquals(270_000L, result.receivedMinor)
    }

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
        expectedDayTo = 5,
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
            confirming(6),
        ).single()

        assertEquals(270_000L, result.receivedMinor)
        assertTrue(result.arrived)
        assertTrue(result.fulfilled)
        assertFalse(result.awaiting)
    }

    /** On the estimate itself nothing is late yet: the day is not over. */
    @Test
    fun `the estimate day itself is not called late`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            emptyList(),
            august,
            LocalDate.of(2026, 8, 5),
            zone,
        ).single()

        assertFalse(result.arrived)
        assertFalse(result.awaiting)
    }

    /** Part of the money is not the money: the rest is still expected. */
    @Test
    fun `a partial payment keeps the remainder expected`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6, amount = 100_000)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
            confirming(6),
        ).single()

        assertTrue(result.arrived)
        assertFalse(result.fulfilled)
        assertEquals(170_000L, result.remainingMinor)
        assertTrue(result.awaiting)
    }

    /** Several credits add up to the declared amount instead of each being judged on its own. */
    @Test
    fun `partial payments accumulate until the declaration is met`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6, amount = 100_000), arrival(9, amount = 170_000)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
            confirming(6, 9),
        ).single()

        assertTrue(result.fulfilled)
        assertEquals(0L, result.remainingMinor)
        assertFalse(result.awaiting)
    }

    @Test
    fun `an estimate that passed with nothing received is awaited`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            emptyList(),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertTrue(result.awaiting)
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
            confirming(6),
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

    /**
     * A credit on the receiving account proves nothing on its own. It is offered back as a question
     * so the owner answers it, rather than being counted and quietly closing the month.
     */
    @Test
    fun `an unanswered credit is a candidate and not a receipt`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6)),
            august,
            LocalDate.of(2026, 8, 15),
            zone,
        ).single()

        assertFalse(result.arrived)
        assertEquals(0L, result.receivedMinor)
        assertEquals(listOf(6L), result.candidates.map { it.id })
        assertTrue(result.awaiting)
    }

    /** Answering once teaches the sender; the next month's pay from them needs no second answer. */
    @Test
    fun `a confirmed sender carries later payments on its own`() {
        val employer = "TExampleEmployerAddress0000000000"
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(
                arrival(6).copy(rawCounterparty = employer),
                arrival(20, amount = 30_000).copy(rawCounterparty = employer),
            ),
            august,
            LocalDate.of(2026, 8, 25),
            zone,
            confirming(6),
        ).single()

        assertEquals(300_000L, result.receivedMinor)
        assertEquals(setOf(6L), result.confirmedIds)
        assertTrue(result.candidates.isEmpty())
    }

    /** Learning one sender must not sweep in every other credit that happens to land there. */
    @Test
    fun `a different sender stays a question`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(
                arrival(6).copy(rawCounterparty = "TExampleEmployerAddress0000000000"),
                arrival(20, amount = 30_000).copy(rawCounterparty = "TExampleFriendAddress000000000000"),
            ),
            august,
            LocalDate.of(2026, 8, 25),
            zone,
            confirming(6),
        ).single()

        assertEquals(270_000L, result.receivedMinor)
        assertEquals(listOf(20L), result.candidates.map { it.id })
    }

    /** A named merchant is the bank-side form of the same identity. */
    @Test
    fun `a confirmed merchant carries later payments on its own`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(arrival(6).copy(merchantId = 3), arrival(20, amount = 30_000).copy(merchantId = 3)),
            august,
            LocalDate.of(2026, 8, 25),
            zone,
            confirming(6),
        ).single()

        assertEquals(300_000L, result.receivedMinor)
    }

    /** Own movements never become income, however they were answered elsewhere. */
    @Test
    fun `transfers and voided rows are not even candidates`() {
        val result = IncomeExpectations.of(
            listOf(source()),
            listOf(
                arrival(6).copy(id = 30, isTransfer = true),
                arrival(7).copy(id = 31, transferGroupId = 4),
                arrival(8).copy(id = 32, isVoided = true),
                arrival(9).copy(id = 33, source = TxSource.ADJUSTMENT),
                arrival(10).copy(id = 34, amountMinor = -270_000),
            ),
            august,
            LocalDate.of(2026, 8, 25),
            zone,
        ).single()

        assertTrue(result.candidates.isEmpty())
        assertEquals(0L, result.receivedMinor)
    }

    /** Two sources on one account each ask their own question; neither answers the other's. */
    @Test
    fun `two sources on the same account do not share a confirmation`() {
        val second = source().copy(id = 2, label = "Freelance", amountMinor = 50_000)
        val results = IncomeExpectations.of(
            listOf(source(), second),
            listOf(arrival(6)),
            august,
            LocalDate.of(2026, 8, 25),
            zone,
            confirming(6),
        )

        assertEquals(270_000L, results.first { it.source.id == 1L }.receivedMinor)
        assertEquals(0L, results.first { it.source.id == 2L }.receivedMinor)
        assertEquals(listOf(6L), results.first { it.source.id == 2L }.candidates.map { it.id })
    }

    private fun confirming(vararg transactionIds: Int) = transactionIds.map {
        IncomeSourcePaymentEntity(transactionId = it.toLong(), incomeSourceId = 1, createdAt = 0)
    }
}
