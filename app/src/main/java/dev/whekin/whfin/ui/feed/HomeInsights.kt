package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.ui.analytics.AnalyticsCategoryChange
import dev.whekin.whfin.ui.analytics.AnalyticsData
import kotlin.math.abs

internal sealed interface HomeInsight {
    data class SpendingPace(
        val projectedExpenseMinor: Long,
        val previousMonthExpenseMinor: Long,
    ) : HomeInsight

    data class CategoryDriver(
        val name: String?,
        val projectedExpenseMinor: Long,
        val previousMonthExpenseMinor: Long,
    ) : HomeInsight
}

/**
 * Converts accounting facts into a deliberately small Home reading.
 *
 * Early-month projections and small fluctuations are suppressed: an insight should explain a
 * meaningful change, not make normal daily variance look urgent. The detailed Statistics screen
 * remains the source for every number behind these two short conclusions.
 */
internal fun deriveHomeInsights(data: AnalyticsData): List<HomeInsight> {
    val pace = data.pace ?: return emptyList()
    if (pace.daysElapsed < MIN_DAYS_FOR_PROJECTION || data.expenseMinor <= 0L) return emptyList()

    val result = mutableListOf<HomeInsight>()
    if (isMeaningfulChange(pace.projectedExpenseMinor, pace.previousMonthExpenseMinor)) {
        result += HomeInsight.SpendingPace(
            projectedExpenseMinor = pace.projectedExpenseMinor,
            previousMonthExpenseMinor = pace.previousMonthExpenseMinor,
        )
    }

    data.categoryChanges
        .asSequence()
        .map { change -> change to projectCategory(change, pace.daysElapsed, pace.daysInMonth) }
        .firstOrNull { (change, projected) ->
            isMeaningfulChange(projected, change.previousExpenseMinor)
        }
        ?.let { (change, projected) ->
            result += HomeInsight.CategoryDriver(
                name = change.name,
                projectedExpenseMinor = projected,
                previousMonthExpenseMinor = change.previousExpenseMinor,
            )
        }

    return result.take(MAX_HOME_INSIGHTS)
}

private fun projectCategory(
    change: AnalyticsCategoryChange,
    daysElapsed: Int,
    daysInMonth: Int,
): Long = change.expenseMinor * daysInMonth / daysElapsed.coerceAtLeast(1)

private fun isMeaningfulChange(current: Long, previous: Long): Boolean {
    val difference = abs(current - previous)
    if (difference < MIN_ABSOLUTE_CHANGE_MINOR) return false
    if (previous == 0L) return current >= MIN_NEW_SPENDING_MINOR
    return difference * 100L / previous >= MIN_PERCENT_CHANGE
}

private const val MIN_DAYS_FOR_PROJECTION = 5
private const val MIN_ABSOLUTE_CHANGE_MINOR = 2_000L
private const val MIN_NEW_SPENDING_MINOR = 5_000L
private const val MIN_PERCENT_CHANGE = 10L
private const val MAX_HOME_INSIGHTS = 2
