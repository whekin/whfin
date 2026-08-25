package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.IncomeExpectations
import dev.whekin.whfin.ui.analytics.AnalyticsData
import dev.whekin.whfin.ui.analytics.AnalyticsScale
import java.time.LocalDate
import java.time.YearMonth

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
 * money they marked available, the pace Statistics already computes, and a payday they declared.
 */
internal data class HomeRunway(
    val daysLeft: Int,
    val dailyBurnMinor: Long,
    val nextIncome: NextIncomeWindow?,
    /** True when the money runs out before the declared window can close. */
    val shortOfIncome: Boolean,
)

/**
 * Reads a runway, or stays silent when it would be inventing one.
 *
 * Silence is the default in three cases: nothing spendable to divide, no honest daily rate yet, and
 * a runway long enough that saying it out loud is noise rather than news. The pace comes from the
 * same projection Statistics shows, which already separates a realised one-off purchase from the
 * ordinary rate, so a single large payment cannot make the runway look like a crisis.
 */
internal fun homeRunway(
    spendablePivotMinor: Long?,
    analytics: AnalyticsData?,
    incomeSources: List<IncomeSourceEntity>,
    today: LocalDate,
    quietAboveDays: Int = QUIET_ABOVE_DAYS,
): HomeRunway? {
    if (spendablePivotMinor == null || spendablePivotMinor <= 0L) return null
    val dailyBurn = dailyBurnMinor(analytics) ?: return null
    val daysLeft = (spendablePivotMinor / dailyBurn).toInt()
    val nextIncome = nextIncomeWindow(incomeSources, today)
    val shortOfIncome = nextIncome != null &&
        today.plusDays(daysLeft.toLong()) < nextIncome.to
    if (daysLeft > quietAboveDays && !shortOfIncome) return null
    return HomeRunway(
        daysLeft = daysLeft,
        dailyBurnMinor = dailyBurn,
        nextIncome = nextIncome,
        shortOfIncome = shortOfIncome,
    )
}

/**
 * The ordinary spend of one day, taken from the running month's projection.
 *
 * The projection is used rather than the days already elapsed because it is the number Statistics
 * stands behind, and because it treats a purchase far above the person's typical transaction as
 * already spent instead of repeating it every remaining day. A month too young to project has no
 * rate at all: guessing from three days of shopping would move the runway by weeks.
 */
private fun dailyBurnMinor(analytics: AnalyticsData?): Long? {
    if (analytics == null || analytics.period.scale != AnalyticsScale.MONTH) return null
    val pace = analytics.pace ?: return null
    if (pace.daysElapsed < MIN_DAYS_FOR_RATE || pace.daysTotal <= 0) return null
    return (pace.projectedExpenseMinor / pace.daysTotal).takeIf { it > 0L }
}

/**
 * When money is next declared to arrive.
 *
 * A window that has not closed yet is still the answer, because a payment inside its own window is
 * not late. Once it has closed, the next month's window is what the person is waiting for. A source
 * whose era has ended describes months it no longer covers and is skipped.
 */
private fun nextIncomeWindow(
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

private const val MIN_DAYS_FOR_RATE = 5
private const val QUIET_ABOVE_DAYS = 45
