package dev.whekin.whfin.ui.analytics

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.YearMonth

internal enum class AnalyticsScale { MONTH, YEAR }

/**
 * The stretch of time every statistics number is about.
 *
 * A month and a calendar year are the same question asked at two distances, so they share one
 * anchor: switching scale keeps the month the user was looking at, and drilling from a year bar
 * back into a month needs no separate state. Everything downstream — totals, comparison, trend,
 * category window, drill-down — reads [start] and [end] instead of a bare `YearMonth`.
 */
@Immutable
internal data class AnalyticsPeriod(
    val scale: AnalyticsScale,
    val month: YearMonth,
) {
    val year: Int get() = month.year

    val start: YearMonth
        get() = when (scale) {
            AnalyticsScale.MONTH -> month
            AnalyticsScale.YEAR -> YearMonth.of(month.year, 1)
        }

    val end: YearMonth
        get() = when (scale) {
            AnalyticsScale.MONTH -> month
            AnalyticsScale.YEAR -> YearMonth.of(month.year, 12)
        }

    fun contains(value: YearMonth): Boolean = value >= start && value <= end

    fun shiftedBack(periods: Int): AnalyticsPeriod = when (scale) {
        AnalyticsScale.MONTH -> copy(month = month.minusMonths(periods.toLong()))
        AnalyticsScale.YEAR -> copy(month = month.minusYears(periods.toLong()))
    }

    fun previous(): AnalyticsPeriod = shiftedBack(1)

    fun next(): AnalyticsPeriod = when (scale) {
        AnalyticsScale.MONTH -> copy(month = month.plusMonths(1))
        AnalyticsScale.YEAR -> copy(month = month.plusYears(1))
    }

    fun withScale(value: AnalyticsScale): AnalyticsPeriod = copy(scale = value)

    /** True while the period is still running, which is the only case a projection makes sense in. */
    fun isCurrent(today: LocalDate): Boolean = when (scale) {
        AnalyticsScale.MONTH -> month == YearMonth.from(today)
        AnalyticsScale.YEAR -> month.year == today.year
    }

    fun daysElapsed(today: LocalDate): Int = when (scale) {
        AnalyticsScale.MONTH -> today.dayOfMonth
        AnalyticsScale.YEAR -> today.dayOfYear
    }

    fun daysTotal(today: LocalDate): Int = when (scale) {
        AnalyticsScale.MONTH -> month.lengthOfMonth()
        AnalyticsScale.YEAR -> if (LocalDate.of(month.year, 1, 1).isLeapYear) 366 else 365
    }

    /**
     * How many finished periods the "typical spending" comparison averages over.
     *
     * Three months is a stable baseline that still tracks a changing life. Three years is not a
     * baseline anyone has, and most users will not even have two, so a year compares with the year
     * before it and nothing else.
     */
    val comparisonPeriods: Int
        get() = when (scale) {
            AnalyticsScale.MONTH -> 3
            AnalyticsScale.YEAR -> 1
        }

    companion object {
        fun month(value: YearMonth) = AnalyticsPeriod(AnalyticsScale.MONTH, value)
        fun year(value: YearMonth) = AnalyticsPeriod(AnalyticsScale.YEAR, value)
    }
}

/** The latest period of this scale that has already begun. */
internal fun currentPeriod(scale: AnalyticsScale, today: YearMonth) = AnalyticsPeriod(scale, today)
