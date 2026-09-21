package dev.whekin.whfin.ui.setup

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SetupAccountReviewTest {
    private val account = AccountEntity(id = 1, name = "Current", type = AccountType.BANK, currency = "GEL")
    private val day = LocalDate.of(2026, 9, 20)
    private fun at(offset: Long) = day.plusDays(offset).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
    private fun row(id: Long, amount: Long, offset: Long = 0, source: TxSource = TxSource.STATEMENT) =
        TransactionEntity(id = id, accountId = 1, amountMinor = amount, currency = "GEL", occurredAt = at(offset),
            source = source, status = TxStatus.CONFIRMED)
    private fun bank(closing: Long) = StatementImportEntity(accountId = 1, periodFrom = day.toEpochDay(),
        periodTo = day.toEpochDay(), openingBalanceMinor = 0, closingBalanceMinor = closing,
        totalRows = 2, inserted = 2, duplicates = 0, reconciled = 0, importedAt = at(1))

    @Test fun `bank comparison uses end of day not current balance or within day order`() {
        val review = setupAccountReview(account, "Bank", listOf(row(3, 10000), row(1, -2000), row(2, -1000, 1)), listOf(bank(8000)))
        assertEquals(7000L, review.balance)
        assertEquals(0L, review.difference)
        assertEquals(day.toEpochDay(), review.bankDay)
    }
    @Test fun `holds and rows posted after cutoff cannot contradict bank closing`() {
        val review = setupAccountReview(account, null, listOf(row(1, 10000), row(2, -1000, source = TxSource.BANK_HOLD),
            row(3, -2000).copy(postedAt = at(1))), listOf(bank(10000)))
        assertEquals(0L, review.difference)
        assertEquals(1, review.pending)
    }
    @Test fun `user opening is not independent bank evidence`() {
        assertNull(setupAccountReview(account, null, listOf(row(1, 10000)),
            listOf(bank(10000).copy(origin = StatementImportOrigin.USER_OPENING))).bankBalance)
    }
    @Test fun `categorisation preserves check but changed amount or evidence invalidates it`() {
        fun review(row: TransactionEntity, closing: Long = 10000) = setupAccountReview(account, null, listOf(row), listOf(bank(closing))).fingerprint
        val original = row(1, 10000)
        assertEquals(review(original), review(original.copy(categoryId = 8, note = "Groceries")))
        assertNotEquals(review(original), review(original.copy(amountMinor = 9000)))
        assertNotEquals(review(original), review(original, 9000))
    }
    @Test fun `voided and other account rows do not change the checked balance`() {
        val original = setupAccountReview(account, null, listOf(row(1, 100)), emptyList())
        val changed = setupAccountReview(account, null, listOf(row(1, 100), row(2, 200).copy(isVoided = true),
            row(3, 300).copy(accountId = 2)), emptyList())
        assertEquals(original.fingerprint, changed.fingerprint)
        assertEquals(100L, changed.balance)
    }
}
