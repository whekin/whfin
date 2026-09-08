package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Review all" has to lead to what Home just listed.
 *
 * It used to open the whole record, so a heading that had named three things handed over several
 * hundred and left the reader to find them again. The filter it opens now and the list it was
 * pressed from are two readings of one question, so they are checked against each other rather than
 * described twice.
 */
class ReviewAllTest {

    @Test
    fun `the needs-review filter holds exactly what Home listed`() {
        val pending = item(1, TxStatus.PENDING)
        val confirmed = item(2, TxStatus.CONFIRMED)
        val manual = item(3, TxStatus.MANUAL)

        val listedOnHome = homeAttention(listOf(pending, confirmed, manual), emptyList())
            .mapNotNull { (it as? FeedTimelineEntry.Transaction)?.item?.tx?.id }

        val keptByFilter = listOf(pending, confirmed, manual)
            .filter { matchesFeedFilter(it, FeedFilter.NEEDS_REVIEW) }
            .map { it.tx.id }

        assertEquals(listedOnHome, keptByFilter)
        assertEquals(listOf(1L), keptByFilter)
    }

    @Test
    fun `an unrouted message counts as something to answer`() {
        // It cannot be confirmed — it needs an account first — but Home lists it under the same
        // heading, so a filter that dropped it would hand over a shorter list than the one the
        // reader pressed from.
        assertTrue(matchesFeedFilter(item(1, TxStatus.PENDING), FeedFilter.NEEDS_REVIEW))
        assertFalse(matchesFeedFilter(item(2, TxStatus.CONFIRMED), FeedFilter.NEEDS_REVIEW))
    }

    @Test
    fun `the other filters are unchanged by it`() {
        val expense = item(1, TxStatus.CONFIRMED, amountMinor = -500)
        val income = item(2, TxStatus.CONFIRMED, amountMinor = 500)

        assertTrue(matchesFeedFilter(expense, FeedFilter.EXPENSES))
        assertFalse(matchesFeedFilter(income, FeedFilter.EXPENSES))
        assertTrue(matchesFeedFilter(income, FeedFilter.INCOME))
        assertTrue(matchesFeedFilter(expense, FeedFilter.ALL))
    }

    private fun item(id: Long, status: TxStatus, amountMinor: Long = -1_000) = FeedItem(
        tx = TransactionEntity(
            id = id,
            accountId = 1,
            amountMinor = amountMinor,
            currency = "GEL",
            occurredAt = id,
            status = status,
            source = TxSource.SMS,
        ),
        merchant = null,
        category = null,
        account = null,
        cardHint = null,
        day = LocalDate.of(2026, 9, 8),
    )
}
