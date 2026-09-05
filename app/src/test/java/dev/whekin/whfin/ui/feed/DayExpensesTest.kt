package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayExpensesTest {

    @Test
    fun `a day whose only row is an own transfer cost nothing`() {
        // An account's own ledger summed every negative row it held, so a day whose single row read
        // "Between your accounts" printed a spending total — the one thing a transfer is not.
        val expenses = dayExpenses(
            listOf(item(1, -20_000, isTransfer = true, transferGroupId = 7)),
        )

        assertTrue(expenses.byCurrency.isEmpty())
        assertEquals(0L, expenses.gelFromConversions)
    }

    @Test
    fun `borrowed money is not spending on any screen`() {
        val expenses = dayExpenses(
            listOf(item(1, -5_000), item(2, -12_000, isDebt = true)),
        )

        assertEquals(mapOf("GEL" to 5_000L), expenses.byCurrency)
    }

    @Test
    fun `an FX purchase is counted at the lari the conversion beside it took`() {
        val expenses = dayExpenses(
            listOf(
                item(1, -2_400, currency = "USD", fundedMinor = 6_346, fundedCurrency = "GEL"),
                item(2, -1_270),
            ),
        )

        assertEquals(mapOf("USD" to 2_400L, "GEL" to 1_270L), expenses.byCurrency)
        assertEquals(6_346L, expenses.gelFromConversions)
    }

    @Test
    fun `income leaves the day without a spending total`() {
        val expenses = dayExpenses(listOf(item(1, 2_705)))

        assertTrue(expenses.byCurrency.isEmpty())
    }

    private fun item(
        id: Long,
        amountMinor: Long,
        currency: String = "GEL",
        isTransfer: Boolean = false,
        transferGroupId: Long? = null,
        isDebt: Boolean = false,
        fundedMinor: Long? = null,
        fundedCurrency: String? = null,
    ) = FeedItem(
        tx = TransactionEntity(
            id = id,
            accountId = 1,
            amountMinor = amountMinor,
            currency = currency,
            occurredAt = 1_000,
            isTransfer = isTransfer,
            transferGroupId = transferGroupId,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
        ),
        merchant = null,
        category = null,
        account = null,
        cardHint = null,
        fundedByConversionMinor = fundedMinor,
        fundedByConversionCurrency = fundedCurrency,
        isDebt = isDebt,
        day = LocalDate.of(2026, 9, 1),
    )
}
