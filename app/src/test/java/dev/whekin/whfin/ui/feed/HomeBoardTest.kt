package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoardTest {

    @Test
    fun `few conditions all stay visible`() {
        val triage = triageHomeNotices(setOf(HomeNotice.CREDO_SYNC, HomeNotice.CARD_BALANCE))

        assertEquals(listOf(HomeNotice.CARD_BALANCE, HomeNotice.CREDO_SYNC), triage.visible)
        assertEquals(0, triage.foldable)
    }

    @Test
    fun `a single overflowing condition is shown rather than folded`() {
        val triage = triageHomeNotices(
            setOf(HomeNotice.CARD_BALANCE, HomeNotice.INTEGRITY, HomeNotice.CREDO_SYNC),
        )

        assertEquals(3, triage.visible.size)
        assertEquals(0, triage.foldable)
    }

    @Test
    fun `money that cannot be spent outranks setup, truth, freshness and offers`() {
        val triage = triageHomeNotices(
            setOf(
                HomeNotice.SMS_ONBOARDING,
                HomeNotice.CREDO_SYNC,
                HomeNotice.INTEGRITY,
                HomeNotice.SETUP,
                HomeNotice.CARD_BALANCE,
            ),
        )

        assertEquals(listOf(HomeNotice.CARD_BALANCE, HomeNotice.SETUP), triage.visible)
        assertEquals(3, triage.foldable)
    }

    @Test
    fun `expanding reveals the folded conditions in the same order`() {
        val present = setOf(
            HomeNotice.SMS_ONBOARDING,
            HomeNotice.CREDO_SYNC,
            HomeNotice.INTEGRITY,
            HomeNotice.CARD_BALANCE,
        )

        val triage = triageHomeNotices(present, expanded = true)

        assertEquals(
            listOf(
                HomeNotice.CARD_BALANCE,
                HomeNotice.INTEGRITY,
                HomeNotice.CREDO_SYNC,
                HomeNotice.SMS_ONBOARDING,
            ),
            triage.visible,
        )
        assertEquals(2, triage.foldable)
    }

    @Test
    fun `month flow counts own money only`() {
        val month = YearMonth.of(2026, 8)
        val items = listOf(
            item(id = 1, amountMinor = -4_000, day = month.atDay(3)),
            item(id = 2, amountMinor = 120_000, day = month.atDay(5)),
            // Transfers, debts, balance corrections and last month never belong to a month result.
            item(id = 3, amountMinor = -50_000, day = month.atDay(6), isTransfer = true),
            item(id = 4, amountMinor = -20_000, day = month.atDay(7), isDebt = true),
            item(id = 5, amountMinor = -900, day = month.atDay(8), source = TxSource.ADJUSTMENT),
            item(id = 6, amountMinor = -700, day = month.atDay(9), systemCategory = true),
            item(id = 7, amountMinor = -30_000, day = month.minusMonths(1).atDay(20)),
        )

        assertEquals(HomeMonthFlow(120_000, 4_000), homeMonthFlow(items, month))
    }

    @Test
    fun `an unvalued foreign row waits instead of counting as zero`() {
        val month = YearMonth.of(2026, 8)
        val items = listOf(
            item(id = 1, amountMinor = -4_000, day = month.atDay(3)),
            item(id = 2, amountMinor = -2_000, day = month.atDay(4), currency = "USD"),
            item(id = 3, amountMinor = -3_000, day = month.atDay(5), currency = "EUR", gelValueMinor = -9_000),
        )

        assertEquals(HomeMonthFlow(0, 13_000), homeMonthFlow(items, month))
    }

    @Test
    fun `drafts and unrouted messages form one queue, newest first`() {
        val drafts = listOf(
            item(id = 1, amountMinor = -1_000, day = LocalDate.of(2026, 8, 20), occurredAt = 200),
            item(id = 2, amountMinor = -1_000, day = LocalDate.of(2026, 8, 22), status = TxStatus.PENDING, occurredAt = 400),
        )
        val unrouted = listOf(unroutedOperation(id = 9, occurredAt = 300))

        val queue = homeAttention(drafts, unrouted)

        assertEquals(2, queue.size)
        assertEquals(400L, queue[0].occurredAt)
        assertEquals(300L, queue[1].occurredAt)
    }

    @Test
    fun `today wins over recent history, and drafts are left to the queue above`() {
        val today = LocalDate.of(2026, 8, 25)
        val items = listOf(
            item(id = 1, amountMinor = -1_000, day = today, status = TxStatus.PENDING),
            item(id = 2, amountMinor = -2_000, day = today),
            item(id = 3, amountMinor = -3_000, day = today.minusDays(2)),
        )

        val recent = homeRecent(items, today)

        assertTrue(recent.isToday)
        assertEquals(listOf(2L), recent.items.map { it.tx.id })
    }

    @Test
    fun `a quiet day falls back to the last settled rows`() {
        val today = LocalDate.of(2026, 8, 25)
        val items = (1L..5L).map { id ->
            item(id = id, amountMinor = -1_000, day = today.minusDays(id))
        }

        val recent = homeRecent(items, today)

        assertEquals(false, recent.isToday)
        assertEquals(listOf(1L, 2L, 3L), recent.items.map { it.tx.id })
    }

    private fun item(
        id: Long,
        amountMinor: Long,
        day: LocalDate,
        currency: String = "GEL",
        gelValueMinor: Long? = null,
        status: TxStatus = TxStatus.CONFIRMED,
        source: TxSource = TxSource.STATEMENT,
        isTransfer: Boolean = false,
        isDebt: Boolean = false,
        systemCategory: Boolean = false,
        occurredAt: Long = id,
    ) = FeedItem(
        tx = TransactionEntity(
            id = id,
            accountId = 1,
            amountMinor = amountMinor,
            currency = currency,
            gelValueMinor = gelValueMinor,
            occurredAt = occurredAt,
            status = status,
            source = source,
            isTransfer = isTransfer,
        ),
        merchant = null,
        category = if (systemCategory) {
            CategoryEntity(
                id = 1,
                name = "Unaccounted",
                kind = CategoryKind.EXPENSE,
                icon = "help",
                color = 0,
                sortOrder = 0,
                isSystem = true,
            )
        } else null,
        account = null,
        cardHint = null,
        isDebt = isDebt,
        day = day,
    )

    private fun unroutedOperation(id: Long, occurredAt: Long) = UnroutedOperation(
        diagnostic = SmsDiagnosticEntity(
            id = id,
            externalKey = "sms-$id",
            receivedAt = occurredAt,
            occurredAt = occurredAt,
            kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
            updatedAt = occurredAt,
        ),
        day = LocalDate.of(2026, 8, 21),
    )
}
