package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashRunwayTest {
    private val today = LocalDate.of(2026, 8, 28)
    private val salary = IncomeSourceEntity(1, "Salary", 420_000, "GEL", 1, 5, 10,
        LocalDate.of(2026, 1, 1).toEpochDay(), null, 0)
    private val merchants = listOf(MerchantEntity(1, "demo rent", "Demo rent"))
    private val ordinary = (1..28).map { tx(it.toLong(), 7_200, today.withDayOfMonth(it)) }
    private val rent = (4..8).map { tx(100L + it, 30_000, today.withMonth(it).withDayOfMonth(3), 1) }

    @Test fun `paid small recurring bill is excluded from daily rate but next occurrence is forecast`() {
        val result = forecast(ordinary + rent)
        assertEquals(7_200L, result.runway!!.dailyBurnMinor)
        assertEquals(123_600L, result.runway.expectedExpenseMinor)
        assertEquals(8_600L, result.runway.shortfallMinor)
        assertEquals(listOf(LocalDate.of(2026, 9, 3)), result.runway.recurringOccurrences.map { it.dueDate })
    }

    @Test fun `several realised large purchases leave daily pace and future expenses unchanged`() {
        val baseline = forecast(ordinary + rent)
        val result = forecast(ordinary + rent + listOf(tx(200, 400_000, today), tx(201, 160_000, today)))
        assertEquals(baseline, result)
    }

    @Test fun `large rent is not discarded as a one off`() {
        val result = forecast(ordinary + rent.map { it.copy(amountMinor = -120_000) })
        assertEquals(7_200L, result.runway!!.dailyBurnMinor)
        assertEquals(213_600L, result.runway.expectedExpenseMinor)
    }

    @Test fun `unknown current expense value suppresses an optimistic runway`() {
        assertNull(forecast(ordinary + tx(200, 1000, today).copy(currency = "USD")).runway)
    }

    @Test fun `debt only payments cannot establish recurring obligations`() {
        val allocations = rent.map { TransactionAllocationEntity(transactionId = it.id,
            amountMinor = it.amountMinor, purpose = AllocationPurpose.LOAN) }
        val result = forecast(ordinary + rent, allocations)
        assertTrue(result.runway!!.recurringOccurrences.isEmpty())
        assertEquals(7_200L, result.runway.dailyBurnMinor)
    }

    @Test fun `voided transfers and balance adjustments do not increase future spending`() {
        val baseline = forecast(ordinary)
        val ignored = listOf(tx(200, 10_000, today).copy(isVoided = true),
            tx(201, 10_000, today).copy(isTransfer = true),
            tx(202, 10_000, today).copy(source = TxSource.ADJUSTMENT))
        assertEquals(baseline, forecast(ordinary + ignored))
    }

    @Test fun `small sample stays unknown even late in the month`() {
        assertNull(forecast(ordinary.take(4)).runway)
    }

    @Test fun `beginning of the month does not extrapolate three days`() {
        assertNull(cashForecast(115_000, ordinary.take(3), emptyList(), merchants, emptyList(),
            listOf(salary), today.withDayOfMonth(3), ZoneOffset.UTC).runway)
    }

    @Test fun `partial debt split uses only the spending share for recurring evidence`() {
        val allocations = rent.flatMap { listOf(
            TransactionAllocationEntity(transactionId = it.id, amountMinor = -15_000, purpose = AllocationPurpose.LOAN),
            TransactionAllocationEntity(transactionId = it.id, amountMinor = -15_000, purpose = AllocationPurpose.PERSONAL)) }
        val result = forecast(ordinary + rent, allocations).runway!!
        assertEquals(7_200L, result.dailyBurnMinor)
        assertEquals(15_000L, result.recurringOccurrences.single().amountMinor)
    }

    @Test fun `a booked foreign bill is forecast at its own historical lari valuation`() {
        val result = forecast(ordinary + rent.map { it.copy(currency = "USD", amountMinor = -10_000,
            gelValueMinor = -30_000) }).runway!!
        assertEquals(7_200L, result.dailyBurnMinor)
        assertEquals(30_000L, result.recurringOccurrences.single().amountMinor)
    }

    @Test fun `conversion funded purchase and its debt share use actual funding proportion`() {
        val purchase = tx(200, 10_000, today).copy(accountId = 2, currency = "USD")
        val funding = listOf(tx(201, 30_000, today).copy(isTransfer = true, transferGroupId = 1),
            tx(202, -10_000, today).copy(accountId = 2, currency = "USD", isTransfer = true, transferGroupId = 1))
        val allocations = listOf(
            TransactionAllocationEntity(transactionId = 200, amountMinor = -5_000, purpose = AllocationPurpose.LOAN),
            TransactionAllocationEntity(transactionId = 200, amountMinor = -5_000, purpose = AllocationPurpose.PERSONAL))
        val result = forecast(ordinary + purchase + funding, allocations).runway!!
        assertEquals((28 * 7_200L + 15_000) / 28, result.dailyBurnMinor)
    }

    @Test fun `foreign SMS awaiting settlement cannot be mistaken for no expense`() {
        val purchase = tx(200, 0, today).copy(source = TxSource.SMS, origAmountMinor = 1000, origCurrency = "USD")
        assertNull(forecast(ordinary + purchase).runway)
    }

    private fun forecast(rows: List<TransactionEntity>, allocations: List<TransactionAllocationEntity> = emptyList()) =
        cashForecast(115_000, rows, emptyList(), merchants, allocations, listOf(salary), today, ZoneOffset.UTC)

    private fun tx(id: Long, expense: Long, day: LocalDate, merchant: Long? = null) = TransactionEntity(
        id = id, accountId = 1, amountMinor = -expense, currency = "GEL",
        occurredAt = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED, source = TxSource.STATEMENT, merchantId = merchant)
}
