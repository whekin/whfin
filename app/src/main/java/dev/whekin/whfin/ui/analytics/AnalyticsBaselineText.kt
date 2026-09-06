package dev.whekin.whfin.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinThemeTokens
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
    val standalone = namedSpan(periods, locale, standalone = true)
    if (comparisonDays == null) return standalone
    val scale = periods.firstOrNull()?.scale ?: AnalyticsScale.MONTH
    return when (scale) {
        AnalyticsScale.MONTH -> stringResource(
            R.string.analytics_baseline_days,
            comparisonDays,
            namedSpan(periods, locale, standalone = false),
        )
        // A day index inside a year is a number nobody carries in their head, so the year names
        // itself and says only that the same part of it was measured.
        AnalyticsScale.YEAR -> stringResource(R.string.analytics_baseline_days_year, standalone)
    }
}

/**
 * Consecutive periods read as a range, everything else as a list.
 *
 * "May, June, July" spends three names on a fact one dash carries, and the caption has to stay one
 * line. A gap in the middle is not a range, so a set with holes in it is still listed in full.
 */
@Composable
private fun namedSpan(
    periods: List<AnalyticsPeriod>,
    locale: Locale,
    standalone: Boolean,
): String {
    val names = periods.map { periodName(it, locale, standalone) }
    if (names.size < 3 || !isConsecutive(periods)) return names.joinToString(", ")
    return stringResource(R.string.analytics_baseline_range, names.first(), names.last())
}

private fun isConsecutive(periods: List<AnalyticsPeriod>): Boolean =
    periods.zipWithNext().all { (earlier, later) -> earlier.next().month == later.month }

/**
 * The one line under a stated difference: what the average is of, and that it may not be all of it.
 *
 * It used to be two lines, the second a whole sentence about statement coverage repeated under every
 * comparison on two screens. The caveat is permanent and therefore cheap to say once and short; the
 * reasoning behind it is worth reading once and belongs behind a tap.
 */
@Composable
internal fun baselineCaption(baseline: AnalyticsBaseline, comparisonDays: Int?): String? {
    if (!baseline.isKnown) return null
    val span = baselineSpanText(baseline.periods, comparisonDays)
    if (baseline.isComplete) return stringResource(R.string.analytics_baseline_usual, span)
    // A count of what was found already says the history is short; saying it twice on one line is
    // what turned the caption into a paragraph.
    val month = baseline.periods.first().scale == AnalyticsScale.MONTH
    return stringResource(
        if (month) R.string.analytics_baseline_incomplete else R.string.analytics_baseline_incomplete_year,
        span,
        baseline.periods.size,
        baseline.requestedPeriods,
    )
}

/**
 * The base, as a line that can be asked to explain itself.
 *
 * [wholePeriods] tells it to describe the whole-period window a projection is read against rather
 * than the day window the difference above it uses.
 */
@Composable
internal fun BaselineNote(
    baseline: AnalyticsBaseline,
    comparisonDays: Int?,
    modifier: Modifier = Modifier,
    wholePeriods: Boolean = false,
) {
    val window = if (wholePeriods) null else comparisonDays
    val caption = baselineCaption(baseline, window) ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        modifier = modifier.testTag("analytics-difference-base"),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
    ) {
        Row(
            Modifier
                .heightIn(min = WhfinThemeTokens.sizes.minTouchTarget)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.analytics_baseline_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
    if (open) BaselineDetailsSheet(baseline, window) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaselineDetailsSheet(
    baseline: AnalyticsBaseline,
    comparisonDays: Int?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WhfinThemeTokens.spacing.rail, vertical = 4.dp)
                .testTag("analytics-baseline-details"),
            verticalArrangement = Arrangement.spacedBy(WhfinThemeTokens.spacing.md),
        ) {
            Text(
                stringResource(R.string.analytics_baseline_details),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(
                    if (comparisonDays == null) R.string.analytics_baseline_details_window_whole
                    else R.string.analytics_baseline_details_window,
                    baselineSpanText(baseline.periods, comparisonDays),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.analytics_baseline_details_found,
                    baseline.requestedPeriods,
                    baseline.periods.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.analytics_baseline_details_limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhfinButton(
                label = stringResource(R.string.analytics_baseline_details_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                style = WhfinActionStyle.Secondary,
            )
        }
    }
}

/**
 * The one sentence every block on both analytics screens uses to state a difference.
 *
 * No percentage lives here on purpose: a percentage of a recorded average that happens to be zero
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
