package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.IncomeExpectations
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** The window a declared source says money arrives in, resolved to actual dates. */
internal data class NextIncomeWindow(
    val from: LocalDate,
    val to: LocalDate,
)

/**
 * How long the spendable money lasts at the current rate, and whether that reaches the next payday.
 *
 * This is the one reading on Home that changes a decision on an ordinary day. It is deliberately
 * built from things the app already knows rather than a budget the person would have to maintain:
 * money they marked available, ordinary spending, proven monthly bills and a declared payday.
 */
internal data class HomeRunway(
    val daysLeft: Int?,
    val dailyBurnMinor: Long,
    val nextIncome: NextIncomeWindow?,
    /** True when the money runs out before the declared window can close. */
    val shortOfIncome: Boolean,
    /** Expected gap at the end of the declared payday window. */
    val shortfallMinor: Long? = null,
    /** Proven monthly payments scheduled inside the same forward-looking horizon. */
    val recurringOccurrences: List<RecurringOccurrence> = emptyList(),
    val expectedExpenseMinor: Long? = null,
    val remainingMinor: Long? = null,
)

/**
 * Reads a runway, or stays silent when it would be inventing one.
 *
 * Missing balance/rate is not a forecast. Without a payday, readings beyond 45 days stay quiet;
 * with one, answer whether the current money covers its window, not an unsupported distant runway.
 * The rate excludes monthly bills (scheduled separately) and realised one-offs. Expenses already
 * paid have affected today's balance and must not be spread into future days a second time.
 */
internal fun homeRunway(
    spendablePivotMinor: Long?,
    ordinaryDailyMinor: Long?,
    incomeSources: List<IncomeSourceEntity>,
    today: LocalDate,
    recurringOccurrences: List<RecurringOccurrence> = emptyList(),
    quietAboveDays: Int = QUIET_ABOVE_DAYS,
): HomeRunway? {
    if (spendablePivotMinor == null) return null
    val dailyBurn = ordinaryDailyMinor?.takeIf { it >= 0L } ?: return null
    val nextIncome = nextIncomeWindow(incomeSources, today)
    val horizonOccurrences = recurringOccurrences
        .filter { occurrence -> nextIncome == null || occurrence.dueDate <= nextIncome.to }
        .sortedBy(RecurringOccurrence::dueDate)
    val daysLeft = daysUntilExhausted(
        spendableMinor = spendablePivotMinor,
        dailyBurnMinor = dailyBurn,
        occurrences = horizonOccurrences,
        today = today,
    )
    val expectedExpense = nextIncome?.let { window ->
        val daysToWindowEnd = ChronoUnit.DAYS.between(today, window.to).coerceAtLeast(0L)
        val ordinary = saturatingMultiply(dailyBurn, daysToWindowEnd)
        val obligations = horizonOccurrences.sumOfSaturated { it.amountMinor }
        saturatingAdd(ordinary, obligations)
    }
    val remaining = expectedExpense?.let { expense ->
        runCatching { Math.subtractExact(spendablePivotMinor, expense) }.getOrElse { Long.MIN_VALUE }
    }
    val shortfall = remaining?.let { -it.coerceAtLeast(-Long.MAX_VALUE).coerceAtMost(0L) }
    val shortOfIncome = shortfall?.let { it > 0L } ?: false
    if (nextIncome == null && (daysLeft == null || daysLeft > quietAboveDays)) return null
    return HomeRunway(
        daysLeft = daysLeft,
        dailyBurnMinor = dailyBurn,
        nextIncome = nextIncome,
        shortOfIncome = shortOfIncome,
        shortfallMinor = shortfall?.takeIf { it > 0L },
        recurringOccurrences = horizonOccurrences,
        expectedExpenseMinor = expectedExpense,
        remainingMinor = remaining,
    )
}

/** Walks only proven payment dates; ordinary days between them remain one arithmetic step. */
private fun daysUntilExhausted(
    spendableMinor: Long,
    dailyBurnMinor: Long,
    occurrences: List<RecurringOccurrence>,
    today: LocalDate,
): Int? {
    if (spendableMinor <= 0L) return 0
    var balance = spendableMinor
    var cursor = today
    var elapsed = 0L
    occurrences
        .groupBy { maxOf(it.dueDate, today) }
        .toSortedMap()
        .forEach { (dueDate, due) ->
            val ordinaryDays = ChronoUnit.DAYS.between(cursor, dueDate).coerceAtLeast(0L)
            val affordableOrdinaryDays = if (dailyBurnMinor == 0L) Long.MAX_VALUE else balance / dailyBurnMinor
            if (affordableOrdinaryDays <= ordinaryDays) {
                return (elapsed + affordableOrdinaryDays).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            balance -= dailyBurnMinor * ordinaryDays
            elapsed += ordinaryDays
            val dueCost = saturatingAdd(
                dailyBurnMinor,
                due.sumOfSaturated { it.amountMinor },
            )
            if (balance < dueCost) {
                return elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            balance -= dueCost
            elapsed += 1L
            cursor = dueDate.plusDays(1)
        }
    if (dailyBurnMinor == 0L) return null
    return (elapsed + balance / dailyBurnMinor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun saturatingMultiply(value: Long, factor: Long): Long =
    runCatching { Math.multiplyExact(value, factor) }.getOrElse { Long.MAX_VALUE }

private fun saturatingAdd(left: Long, right: Long): Long =
    runCatching { Math.addExact(left, right) }.getOrElse { Long.MAX_VALUE }

private inline fun <T> Iterable<T>.sumOfSaturated(value: (T) -> Long): Long =
    fold(0L) { total, item -> saturatingAdd(total, value(item)) }

/**
 * When money is next declared to arrive.
 *
 * A window that has not closed yet is still the answer, because a payment inside its own window is
 * not late. Once it has closed, the next month's window is what the person is waiting for. A source
 * whose era has ended describes months it no longer covers and is skipped.
 */
internal fun nextIncomeWindow(
    sources: List<IncomeSourceEntity>,
    today: LocalDate,
): NextIncomeWindow? = sources
    .asSequence()
    .flatMap { source ->
        val month = YearMonth.from(today)
        sequenceOf(month, month.plusMonths(1))
            .filter { IncomeExpectations.covers(source, it) }
            .map { source.windowIn(it) }
    }
    .filter { it.to >= today }
    .minByOrNull { it.from }

private fun IncomeSourceEntity.windowIn(month: YearMonth): NextIncomeWindow {
    val length = month.lengthOfMonth()
    val from = expectedDayFrom.coerceIn(1, length)
    val to = expectedDayTo.coerceIn(from, length)
    return NextIncomeWindow(month.atDay(from), month.atDay(to))
}

private const val QUIET_ABOVE_DAYS = 45
