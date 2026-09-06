package dev.whekin.whfin.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.formatMinor
import java.time.format.TextStyle
import java.util.Locale

/**
 * Names the stretch of time a comparison was made against.
 *
 * "88% less than the previous month" on the fifth compared five days with thirty. That was fixed by
 * cutting the arithmetic to the same stretch, but the sentence then had to carry the qualification,
 * and "by this day" is not a date — it names nothing a reader can check. So the base is spelled out:
 * the months it averages, and the days of them it counted.
 */
@Composable
internal fun baselineSpanText(
    periods: List<AnalyticsPeriod>,
    comparisonDays: Int?,
): String {
    val locale = baselineLocale()
    val standalone = periods.joinToString(", ") { periodName(it, locale, standalone = true) }
    if (comparisonDays == null) return standalone
    val scale = periods.firstOrNull()?.scale ?: AnalyticsScale.MONTH
    return when (scale) {
        AnalyticsScale.MONTH -> stringResource(
            R.string.analytics_baseline_days,
            comparisonDays,
            periods.joinToString(", ") { periodName(it, locale, standalone = false) },
        )
        // A day index inside a year is a number nobody carries in their head, so the year names
        // itself and says only that the same part of it was measured.
        AnalyticsScale.YEAR -> stringResource(R.string.analytics_baseline_days_year, standalone)
    }
}

/** The full caption under a comparison: what "usual" averages, and how much of it is known. */
@Composable
internal fun baselineCaption(baseline: AnalyticsBaseline, comparisonDays: Int?): String? {
    if (!baseline.isKnown) return null
    val span = stringResource(
        R.string.analytics_baseline_usual,
        baselineSpanText(baseline.periods, comparisonDays),
    )
    if (baseline.isComplete) return span
    val month = baseline.periods.first().scale == AnalyticsScale.MONTH
    return stringResource(
        if (month) R.string.analytics_baseline_incomplete else R.string.analytics_baseline_incomplete_year,
        span,
        baseline.periods.size,
        baseline.requestedPeriods,
    )
}

/**
 * The one sentence every block on both analytics screens uses to state a difference.
 *
 * No percentage lives here on purpose: a percentage of an ordinary level that happens to be zero
 * means nothing, and the money difference means the same thing in every case.
 */
@Composable
internal fun differenceText(current: Long, typical: Long): String {
    val difference = current - typical
    if (difference == 0L) return stringResource(R.string.analytics_difference_same)
    val amount = formatMinor(kotlin.math.abs(difference), "GEL")
    return stringResource(
        if (difference > 0L) R.string.analytics_difference_more else R.string.analytics_difference_less,
        amount,
    )
}

/**
 * What the base cannot promise.
 *
 * WHFIN records statement periods per imported account, and nothing at all for cash, manual entries
 * or an account nobody has imported — so no month can be shown to be complete, and a month holding
 * one row must not be read as a month that held one payment. The average is therefore never called
 * usual or normal; it is called what it provably is, an average of what was recorded, and this line
 * says the rest quietly rather than in an alarm.
 */
@Composable
internal fun baselineLimitation(): String = stringResource(R.string.analytics_baseline_limitation)

/** The same naming for a single period, used where a chart compares with the bar before it. */
@Composable
internal fun periodSpanText(period: AnalyticsPeriod, comparisonDays: Int?): String =
    baselineSpanText(listOf(period), comparisonDays)

private fun periodName(period: AnalyticsPeriod, locale: Locale, standalone: Boolean): String =
    when (period.scale) {
        AnalyticsScale.MONTH -> period.month.month
            // Russian declines a month named after a day of it: "1–6 июня", not "1–6 июнь".
            // TextStyle.FULL is that form; FULL_STANDALONE is the one a list of months wants.
            // The locale already capitalises where its own writing does: "June" but "июнь".
            .getDisplayName(if (standalone) TextStyle.FULL_STANDALONE else TextStyle.FULL, locale)
        AnalyticsScale.YEAR -> period.year.toString()
    }

@Composable
private fun baselineLocale(): Locale = LocalConfiguration.current.locales[0]
