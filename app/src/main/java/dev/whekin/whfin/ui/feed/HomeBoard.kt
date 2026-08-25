package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth

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
 * Month totals read straight off the feed, used until the full analytics pass has an answer.
 *
 * A foreign-currency operation enters at its booked GEL value — the rate of its own day. While that
 * day has no quote the row is left out rather than counted as zero, which is why this is a fallback
 * and Statistics stays the source of record.
 */
internal fun homeMonthFlow(items: List<FeedItem>, month: YearMonth): HomeMonthFlow {
    val monthGelValues = items.asSequence()
        .filter { item ->
            YearMonth.from(item.day) == month &&
                !item.tx.isTransfer && item.tx.transferGroupId == null && !item.isDebt &&
                item.tx.source != TxSource.ADJUSTMENT && item.category?.isSystem != true
        }
        .mapNotNull { item ->
            if (item.tx.currency == "GEL") item.tx.amountMinor else item.tx.gelValueMinor
        }
        .toList()
    return HomeMonthFlow(
        incomeMinor = monthGelValues.sumOf { it.coerceAtLeast(0) },
        expenseMinor = -monthGelValues.sumOf { it.coerceAtMost(0) },
    )
}

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

/** Settled rows Home closes with: today when the day has any, otherwise the last few. */
internal data class HomeRecent(val items: List<FeedItem>, val isToday: Boolean)

internal fun homeRecent(items: List<FeedItem>, today: LocalDate): HomeRecent {
    val settled = items.filter { it.tx.status != TxStatus.PENDING }
    val todayItems = settled.filter { it.day == today }
    return if (todayItems.isNotEmpty()) {
        HomeRecent(todayItems.take(TODAY_ROWS), isToday = true)
    } else {
        HomeRecent(settled.take(RECENT_ROWS), isToday = false)
    }
}

private const val MAX_VISIBLE_NOTICES = 2
private const val TODAY_ROWS = 5
private const val RECENT_ROWS = 3
