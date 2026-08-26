package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.DebtCaseEntity
import dev.whekin.whfin.data.db.DebtDirection
import dev.whekin.whfin.data.db.DebtEventEntity
import dev.whekin.whfin.data.db.DebtEventKind
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoardTest {

    private val SYSTEM_CATEGORY_ID = 99L

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
        val transactions = listOf(
            transaction(id = 1, amountMinor = -4_000, day = month.atDay(3)),
            transaction(id = 2, amountMinor = 120_000, day = month.atDay(5)),
            // Transfers, debts, balance corrections and last month never belong to a month result.
            transaction(id = 3, amountMinor = -50_000, day = month.atDay(6), isTransfer = true),
            transaction(id = 4, amountMinor = -20_000, day = month.atDay(7)),
            transaction(id = 5, amountMinor = -900, day = month.atDay(8), source = TxSource.ADJUSTMENT),
            transaction(id = 6, amountMinor = -700, day = month.atDay(9), categoryId = SYSTEM_CATEGORY_ID),
            transaction(id = 7, amountMinor = -30_000, day = month.minusMonths(1).atDay(20)),
        )
        val allocations = listOf(loanAllocation(transactionId = 4, amountMinor = -20_000))

        assertEquals(
            HomeMonthFlow(120_000, 4_000),
            homeMonthFlow(transactions, listOf(systemCategory()), allocations, month, ZoneOffset.UTC),
        )
    }

    @Test
    fun `an unvalued foreign row waits instead of counting as zero`() {
        val month = YearMonth.of(2026, 8)
        val transactions = listOf(
            transaction(id = 1, amountMinor = -4_000, day = month.atDay(3)),
            transaction(id = 2, amountMinor = -2_000, day = month.atDay(4), currency = "USD"),
            transaction(
                id = 3,
                amountMinor = -3_000,
                day = month.atDay(5),
                currency = "EUR",
                gelValueMinor = -9_000,
            ),
        )

        assertEquals(
            HomeMonthFlow(0, 13_000),
            homeMonthFlow(transactions, emptyList(), emptyList(), month, ZoneOffset.UTC),
        )
    }

    @Test
    fun `an opening balance anchor is not this month's income`() {
        val month = YearMonth.of(2026, 8)
        val transactions = listOf(
            transaction(
                id = 1,
                amountMinor = 500_000,
                day = month.atDay(1),
                source = TxSource.ADJUSTMENT,
                isTransfer = true,
            ),
            transaction(id = 2, amountMinor = -4_000, day = month.atDay(3)),
        )

        assertEquals(
            HomeMonthFlow(0, 4_000),
            homeMonthFlow(transactions, emptyList(), emptyList(), month, ZoneOffset.UTC),
        )
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
    fun `today's total covers the whole day, not the rows on screen`() {
        val today = LocalDate.of(2026, 8, 25)
        val items = (1L..7L).map { id ->
            item(id = id, amountMinor = -1_000, day = today)
        } + listOf(
            item(id = 8, amountMinor = 50_000, day = today),
            item(id = 9, amountMinor = -9_000, day = today, isTransfer = true),
            item(id = 10, amountMinor = -3_000, day = today, source = TxSource.ADJUSTMENT),
        )

        val recent = homeRecent(items, today)

        assertEquals(5, recent.items.size)
        assertEquals(7_000L, recent.expenseMinor)
    }

    @Test
    fun `a day that only earned has no spending to report`() {
        val today = LocalDate.of(2026, 8, 25)
        val recent = homeRecent(listOf(item(id = 1, amountMinor = 50_000, day = today)), today)

        assertTrue(recent.isToday)
        assertEquals(null, recent.expenseMinor)
    }

    @Test
    fun `only what is still owed to other people is named, per currency`() {
        val people = listOf(person(1, "Nino"), person(2, "Dato"))
        val cases = listOf(
            debtCase(id = 1, personId = 1, direction = DebtDirection.I_OWE_THEM, amountMinor = 30_000),
            debtCase(id = 2, personId = 2, direction = DebtDirection.I_OWE_THEM, amountMinor = 20_000),
            debtCase(
                id = 3,
                personId = 1,
                direction = DebtDirection.I_OWE_THEM,
                amountMinor = 10_000,
                currency = "USD",
            ),
            // Money owed to the person is already missing from their accounts.
            debtCase(id = 4, personId = 2, direction = DebtDirection.THEY_OWE_ME, amountMinor = 90_000),
            // A closed case is not a claim any more.
            debtCase(
                id = 5,
                personId = 1,
                direction = DebtDirection.I_OWE_THEM,
                amountMinor = 40_000,
                status = DebtStatus.CLOSED,
            ),
        )
        val events = listOf(
            settlement(debtCaseId = 1, debtValueMinor = 10_000),
            // A corrected settlement is an audit row, not a repayment.
            settlement(debtCaseId = 2, debtValueMinor = 20_000, voided = true),
        )

        assertEquals(
            listOf(
                HomeDebt("GEL", 40_000, listOf("Nino", "Dato")),
                HomeDebt("USD", 10_000, listOf("Nino")),
            ),
            homeDebtsOwed(cases, events, people),
        )
    }

    @Test
    fun `a fully repaid case that stayed open is not a claim`() {
        val cases = listOf(
            debtCase(id = 1, personId = 1, direction = DebtDirection.I_OWE_THEM, amountMinor = 30_000),
        )
        val events = listOf(settlement(debtCaseId = 1, debtValueMinor = 30_000))

        assertTrue(homeDebtsOwed(cases, events, listOf(person(1, "Nino"))).isEmpty())
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

    @Test
    fun `an unanswered ledger is not an empty one`() {
        assertEquals(
            false,
            homeNothingRecorded(
                feedLoaded = false,
                items = emptyList(),
                unrouted = emptyList(),
                recurringDue = emptyList(),
                debtsOwed = emptyList(),
            ),
        )
        assertTrue(
            homeNothingRecorded(
                feedLoaded = true,
                items = emptyList(),
                unrouted = emptyList(),
                recurringDue = emptyList(),
                debtsOwed = emptyList(),
            ),
        )
    }

    @Test
    fun `a screen that already names money never offers to get started`() {
        assertEquals(
            false,
            homeNothingRecorded(
                feedLoaded = true,
                items = emptyList(),
                unrouted = emptyList(),
                recurringDue = emptyList(),
                debtsOwed = listOf(HomeDebt("GEL", 18_000, listOf("Maya"))),
            ),
        )
    }

    private fun systemCategory() = CategoryEntity(
        id = SYSTEM_CATEGORY_ID,
        name = "Unaccounted",
        kind = CategoryKind.EXPENSE,
        icon = "help",
        color = 0,
        sortOrder = 0,
        isSystem = true,
    )

    private fun loanAllocation(transactionId: Long, amountMinor: Long) = TransactionAllocationEntity(
        transactionId = transactionId,
        amountMinor = amountMinor,
        purpose = AllocationPurpose.LOAN,
    )

    private fun transaction(
        id: Long,
        amountMinor: Long,
        day: LocalDate,
        currency: String = "GEL",
        gelValueMinor: Long? = null,
        source: TxSource = TxSource.STATEMENT,
        isTransfer: Boolean = false,
        categoryId: Long? = null,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = amountMinor,
        currency = currency,
        gelValueMinor = gelValueMinor,
        occurredAt = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED,
        source = source,
        isTransfer = isTransfer,
        categoryId = categoryId,
    )

    private fun person(id: Long, name: String) = PersonEntity(id = id, name = name, color = 0)

    private fun debtCase(
        id: Long,
        personId: Long,
        direction: DebtDirection,
        amountMinor: Long,
        currency: String = "GEL",
        status: DebtStatus = DebtStatus.OPEN,
    ) = DebtCaseEntity(
        id = id,
        personId = personId,
        direction = direction,
        originalAmountMinor = amountMinor,
        currency = currency,
        openedAt = 0,
        status = status,
    )

    private fun settlement(
        debtCaseId: Long,
        debtValueMinor: Long,
        voided: Boolean = false,
    ) = DebtEventEntity(
        debtCaseId = debtCaseId,
        kind = DebtEventKind.SETTLEMENT,
        debtValueMinor = debtValueMinor,
        occurredAt = 0,
        isVoided = voided,
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
