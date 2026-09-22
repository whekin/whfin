package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.expectedPayday
import dev.whekin.whfin.data.income.IncomeExpectations
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The one date a declared source points at this month.
 *
 * A payday is an estimate, not a contract: the owner names the day money usually lands and how the
 * employer treats weekends, and nothing else is asked of them. There is deliberately no outer bound
 * — an app that holds a "latest by" date starts accusing a real payment of being late on a day the
 * owner never agreed to, and the only honest reading once the estimate passes is that we are still
 * waiting.
 */
internal data class NextPayday(
    /** The day of month the owner named, before the weekend rule moved it. */
    val usual: LocalDate,
    /** [usual] after the weekend rule; the date shown everywhere. */
    val expected: LocalDate,
    val weekendAdjusted: Boolean,
    /** The estimate is behind us and this month's money has not arrived in full. */
    val passed: Boolean = false,
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
    val nextIncome: NextPayday?,
    /** True when the money runs out before the payday it is meant to reach. */
    val shortOfIncome: Boolean,
    /** Expected gap on the expected payday. */
    val shortfallMinor: Long? = null,
    /** Proven monthly payments scheduled inside the same forward-looking horizon. */
    val recurringOccurrences: List<RecurringOccurrence> = emptyList(),
    val expectedExpenseMinor: Long? = null,
    val remainingMinor: Long? = null,
)

/**
 * The runway as a shape: one window, the point the money reaches, the day it is refilled.
 *
 * The window runs from today to whichever comes last — the expected payday or the day the money
 * runs out — so both marks always have somewhere to sit. Without a payday still ahead there is no
 * window to draw and the card keeps its sentence: an estimate that already passed cannot anchor a
 * rule, because the next one is not known yet.
 */
internal data class RunwayShape(
    val fundedFraction: Float,
    val runsOut: LocalDate?,
    val payday: LocalDate,
    val paydayFraction: Float,
    val shortOfIncome: Boolean,
)

internal fun runwayShape(runway: HomeRunway, today: LocalDate): RunwayShape? {
    val income = runway.nextIncome?.takeUnless { it.passed } ?: return null
    val runsOut = runway.daysLeft?.let { today.plusDays(it.toLong()) }
    val end = maxOf(income.expected, runsOut ?: income.expected)
    val span = ChronoUnit.DAYS.between(today, end).toFloat()
    // A window that is over today cannot be divided; the card falls back to its sentence.
    if (span <= 0f) return null
    return RunwayShape(
        // No burn rate means nothing is being spent, so the money covers the whole window.
        fundedFraction = runsOut
            ?.let { (ChronoUnit.DAYS.between(today, it) / span).coerceIn(0f, 1f) }
            ?: 1f,
        runsOut = runsOut,
        payday = income.expected,
        paydayFraction = (ChronoUnit.DAYS.between(today, income.expected) / span).coerceIn(0f, 1f),
        shortOfIncome = runway.shortOfIncome,
    )
}

/**
 * Reads a runway, or stays silent when it would be inventing one.
 *
 * Missing balance/rate is not a forecast. Without a payday, readings beyond 45 days stay quiet;
 * with one still ahead, answer whether the current money covers it. Once the estimate has passed
 * the question has no answer — the next payday is unknown — so the card drops back to plain days
 * left rather than promising the money reaches a date nobody named. The rate excludes monthly bills
 * (scheduled separately) and realised one-offs. Expenses already paid have affected today's balance
 * and must not be spread into future days a second time.
 */
internal fun homeRunway(
    spendablePivotMinor: Long?,
    ordinaryDailyMinor: Long?,
    incomeSources: List<IncomeSourceEntity>,
    today: LocalDate,
    recurringOccurrences: List<RecurringOccurrence> = emptyList(),
    arrivedSourceMonths: Set<Pair<Long, YearMonth>> = emptySet(),
    quietAboveDays: Int = QUIET_ABOVE_DAYS,
): HomeRunway? {
    if (spendablePivotMinor == null) return null
    val dailyBurn = ordinaryDailyMinor?.takeIf { it >= 0L } ?: return null
    val nextIncome = nextPayday(incomeSources, today, arrivedSourceMonths)
    // Bills keep arriving while a late payday is waited on, so the horizon falls back to the quiet
    // window instead of collapsing onto a date already behind us.
    val horizon = paydayHorizon(nextIncome, today, quietAboveDays)
    val horizonOccurrences = recurringOccurrences
        .filter { occurrence -> occurrence.dueDate <= horizon }
        .sortedBy(RecurringOccurrence::dueDate)
    val daysLeft = daysUntilExhausted(
        spendableMinor = spendablePivotMinor,
        dailyBurnMinor = dailyBurn,
        occurrences = horizonOccurrences,
        today = today,
    )
    val expectedExpense = nextIncome?.takeUnless { it.passed }?.let { payday ->
        expectedExpenseUntil(today, payday.expected, dailyBurn, horizonOccurrences)
    }
    val remaining = expectedExpense?.let { expense -> remaining(spendablePivotMinor, expense) }
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

/** A passed payday is no longer a future horizon; only a near cash limit earns a Home card. */
internal fun visibleRunway(runway: HomeRunway?): HomeRunway? = runway?.takeUnless {
    it.nextIncome?.passed == true && (it.daysLeft == null || it.daysLeft > QUIET_ABOVE_DAYS)
}

/** How far forward bills are worth listing: to the payday ahead, else the quiet window. */
internal fun paydayHorizon(
    payday: NextPayday?,
    today: LocalDate,
    quietAboveDays: Int = QUIET_ABOVE_DAYS,
): LocalDate = payday?.expected?.takeUnless { payday.passed }
    ?: today.plusDays(quietAboveDays.toLong())

private fun expectedExpenseUntil(
    today: LocalDate,
    through: LocalDate,
    dailyBurnMinor: Long,
    occurrences: List<RecurringOccurrence>,
): Long {
    val ordinaryDays = ChronoUnit.DAYS.between(today, through).coerceAtLeast(0L)
    val ordinary = saturatingMultiply(dailyBurnMinor, ordinaryDays)
    val obligations = occurrences
        .filter { it.dueDate <= through }
        .sumOfSaturated(RecurringOccurrence::amountMinor)
    return saturatingAdd(ordinary, obligations)
}

private fun remaining(spendableMinor: Long, expectedExpenseMinor: Long): Long =
    runCatching { Math.subtractExact(spendableMinor, expectedExpenseMinor) }.getOrElse { Long.MIN_VALUE }

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
 * When money is next expected to arrive.
 *
 * The estimate for this month stays the answer even after its date, because a payment a few days
 * late is still the payment being waited for — only [NextPayday.passed] changes, and it makes the
 * card say so instead of quietly promising next month. A month whose money has fully arrived is
 * skipped, so the next estimate is the following month's. A source whose era has ended describes
 * months it no longer covers and is skipped.
 */
internal fun nextPayday(
    sources: List<IncomeSourceEntity>,
    today: LocalDate,
    arrivedSourceMonths: Set<Pair<Long, YearMonth>> = emptySet(),
): NextPayday? = sources
    .asSequence()
    .flatMap { source ->
        val month = YearMonth.from(today)
        sequenceOf(month, month.plusMonths(1))
            .filter { IncomeExpectations.covers(source, it) }
            .filterNot { source.id to it in arrivedSourceMonths }
            .mapNotNull { source.paydayIn(it) }
    }
    .map { payday -> payday.copy(passed = payday.expected < today) }
    .minWithOrNull(compareBy(NextPayday::expected))

private fun IncomeSourceEntity.paydayIn(month: YearMonth): NextPayday? {
    val usual = month.atDay(expectedDayFrom.coerceIn(1, month.lengthOfMonth()))
    val started = LocalDate.ofEpochDay(startedOn)
    val ended = endedOn?.let(LocalDate::ofEpochDay)
    // A new/ended source whose first month does not reach its usual payday must not promise a
    // special first payment: the declaration explicitly says that first payments may not fit.
    if (started > usual || ended?.let { it < usual } == true) return null
    val expected = expectedPayday(month)
    return NextPayday(usual = usual, expected = expected, weekendAdjusted = expected != usual)
}

private const val QUIET_ABOVE_DAYS = 45
