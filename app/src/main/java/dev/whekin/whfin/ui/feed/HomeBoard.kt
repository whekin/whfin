package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.DebtCaseEntity
import dev.whekin.whfin.data.db.DebtDirection
import dev.whekin.whfin.data.db.DebtEventEntity
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.isOpeningBalanceAnchor
import dev.whekin.whfin.data.recurring.RecurringCharge
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A standing condition Home can raise, ordered by how much it deserves the first screenful.
 *
 * The order is the whole point of the type. Home used to render every condition it knew about as its
 * own block, so one bad day — an empty card, an unfinished setup, a stale reconciliation, a
 * permission never granted — turned the screen into a notification inbox and pushed the actual money
 * below the fold. Money that cannot be spent right now comes first, then an app that is not finished
 * being set up, then data that contradicts itself, then data that is merely old, then an offer.
 */
internal enum class HomeNotice {
    CARD_BALANCE,
    SETUP,
    INTEGRITY,
    CREDO_SYNC,
    SMS_ONBOARDING,
}

internal data class HomeNoticeTriage(
    val visible: List<HomeNotice>,
    /**
     * How many conditions sit behind the fold while collapsed; zero when nothing is worth folding.
     *
     * Zero for a single overflowing notice on purpose: hiding one block behind a row that says one
     * block is hidden costs the reader the same glance and adds a tap.
     */
    val foldable: Int,
)

internal fun triageHomeNotices(
    present: Set<HomeNotice>,
    expanded: Boolean = false,
    limit: Int = MAX_VISIBLE_NOTICES,
): HomeNoticeTriage {
    val ordered = HomeNotice.entries.filter { it in present }
    val foldable = (ordered.size - limit).takeIf { it > 1 } ?: 0
    val visible = if (foldable == 0 || expanded) ordered else ordered.take(limit)
    return HomeNoticeTriage(visible, foldable)
}

/** The running month's own money: what the person earned and spent, transfers and debts aside. */
internal data class HomeMonthFlow(val incomeMinor: Long, val expenseMinor: Long)

/**
 * Month totals read straight from the ledger, used until the full analytics pass has an answer.
 *
 * Taken from the queries themselves rather than from the feed window, because the answer doubles as
 * Home's readiness signal: a `StateFlow` hands its placeholder to whatever combines it, so a total
 * built on shared feed state exists — as zero — before any query has returned, and a zero that means
 * "not asked yet" is indistinguishable from a zero that means "nothing happened".
 *
 * A foreign-currency operation enters at its booked GEL value — the rate of its own day. While that
 * day has no quote the row is left out rather than counted as zero, which is why this is a fallback
 * and Statistics stays the source of record.
 */
internal fun homeMonthFlow(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    allocations: List<TransactionAllocationEntity>,
    month: YearMonth,
    zone: ZoneId,
): HomeMonthFlow {
    val systemCategoryIds = categories.filter(CategoryEntity::isSystem).mapTo(mutableSetOf()) { it.id }
    val debtTransactionIds = allocations
        .filter { it.purpose == AllocationPurpose.LOAN || it.purpose == AllocationPurpose.REPAYMENT }
        .mapTo(mutableSetOf()) { it.transactionId }
    val monthGelValues = transactions.asSequence()
        .filter { transaction ->
            YearMonth.from(Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)) == month &&
                isOwnMoney(transaction, systemCategoryIds, debtTransactionIds)
        }
        .mapNotNull(::pivotValueMinor)
        .toList()
    return HomeMonthFlow(
        incomeMinor = monthGelValues.sumOf { it.coerceAtLeast(0) },
        expenseMinor = -monthGelValues.sumOf { it.coerceAtMost(0) },
    )
}

/** The person's own earning and spending: transfers, debts and corrections move money they had. */
private fun isOwnMoney(
    transaction: TransactionEntity,
    systemCategoryIds: Set<Long>,
    debtTransactionIds: Set<Long>,
): Boolean = !transaction.isVoided && !transaction.isOpeningBalanceAnchor() &&
    !transaction.isTransfer && transaction.transferGroupId == null &&
    transaction.id !in debtTransactionIds &&
    transaction.source != TxSource.ADJUSTMENT &&
    transaction.categoryId !in systemCategoryIds

private fun isOwnMoney(item: FeedItem): Boolean =
    !item.tx.isTransfer && item.tx.transferGroupId == null && !item.isDebt &&
        item.tx.source != TxSource.ADJUSTMENT && item.category?.isSystem != true

private fun pivotValueMinor(transaction: TransactionEntity): Long? =
    if (transaction.currency == "GEL") transaction.amountMinor else transaction.gelValueMinor

private fun pivotValueMinor(item: FeedItem): Long? = pivotValueMinor(item.tx)

/**
 * Rows waiting on a decision: a draft the person has not confirmed and a message with no account.
 *
 * The two are one queue because they ask the same thing of the reader — look at this and say what it
 * is — even though only one of them is in the ledger.
 */
internal fun homeAttention(
    items: List<FeedItem>,
    unrouted: List<UnroutedOperation>,
): List<FeedTimelineEntry> = (
    unrouted.map(FeedTimelineEntry::Unrouted) +
        items.filter { it.tx.status == TxStatus.PENDING }.map(FeedTimelineEntry::Transaction)
    ).sortedByDescending(FeedTimelineEntry::occurredAt)

/**
 * Settled rows Home closes with: today when the day has any, otherwise the last few.
 *
 * [expenseMinor] is today's own spending, counted over the whole day rather than the rows shown, so
 * the number never contradicts the list it sits above by being the sum of the first few. It is the
 * cheapest habit signal on the screen and reads against the ordinary daily rate the runway row
 * already states. A day whose foreign rows have no quote yet keeps them out instead of counting zero.
 */
internal data class HomeRecent(
    val items: List<FeedItem>,
    val isToday: Boolean,
    val expenseMinor: Long? = null,
)

internal fun homeRecent(items: List<FeedItem>, today: LocalDate): HomeRecent {
    val settled = items.filter { it.tx.status != TxStatus.PENDING }
    val todayItems = settled.filter { it.day == today }
    if (todayItems.isEmpty()) return HomeRecent(settled.take(RECENT_ROWS), isToday = false)
    val spent = -todayItems.filter(::isOwnMoney)
        .mapNotNull(::pivotValueMinor)
        .sumOf { it.coerceAtMost(0) }
    return HomeRecent(
        items = todayItems.take(TODAY_ROWS),
        isToday = true,
        expenseMinor = spent.takeIf { it > 0L },
    )
}

/**
 * Borrowed money still sitting in the balance, per currency.
 *
 * Money owed to the person is already missing from their accounts, so it needs no line. Money the
 * person owes is the opposite: it is counted as theirs by every balance on the screen while belonging
 * to somebody else. The claim is named rather than quietly subtracted from what can be spent —
 * a borrowed sum may already be gone, and a headline that moved without saying why is worse than one
 * that needs a second line to be read correctly.
 */
internal data class HomeDebt(
    val currency: String,
    val outstandingMinor: Long,
    val people: List<String>,
)

internal fun homeDebtsOwed(
    cases: List<DebtCaseEntity>,
    events: List<DebtEventEntity>,
    people: List<PersonEntity>,
): List<HomeDebt> {
    val personById = people.associateBy { it.id }
    val settledByCase = events.filterNot(DebtEventEntity::isVoided)
        .groupBy(DebtEventEntity::debtCaseId)
        .mapValues { (_, rows) -> rows.sumOf(DebtEventEntity::debtValueMinor) }
    return cases
        .filter { it.status == DebtStatus.OPEN && it.direction == DebtDirection.I_OWE_THEM }
        .mapNotNull { case ->
            val outstanding = (case.originalAmountMinor - (settledByCase[case.id] ?: 0L))
            if (outstanding <= 0L) return@mapNotNull null
            Triple(case.currency.uppercase(), outstanding, personById[case.personId]?.name)
        }
        .groupBy { it.first }
        .map { (currency, rows) ->
            HomeDebt(
                currency = currency,
                outstandingMinor = rows.sumOf { it.second },
                people = rows.mapNotNull { it.third }.distinct(),
            )
        }
        .sortedByDescending(HomeDebt::outstandingMinor)
}

/**
 * Whether Home may say that nothing is recorded.
 *
 * "Nothing here yet" is a claim about the database, and a `StateFlow` placeholder is not evidence for
 * it: until the ledger answers, an empty list only means nobody has asked. The other money-bearing
 * blocks are part of the test as well — a screen that names an obligation and a debt, and then offers
 * to help the person get started, contradicts itself in the same screenful.
 */
internal fun homeNothingRecorded(
    feedLoaded: Boolean,
    items: List<FeedItem>,
    unrouted: List<UnroutedOperation>,
    recurringDue: List<RecurringCharge>,
    debtsOwed: List<HomeDebt>,
): Boolean = feedLoaded && items.isEmpty() && unrouted.isEmpty() &&
    recurringDue.isEmpty() && debtsOwed.isEmpty()

private const val MAX_VISIBLE_NOTICES = 2
private const val TODAY_ROWS = 5
private const val RECENT_ROWS = 3
