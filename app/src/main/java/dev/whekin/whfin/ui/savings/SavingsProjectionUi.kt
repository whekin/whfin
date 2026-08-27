package dev.whekin.whfin.ui.savings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.savings.SavingsProjection
import dev.whekin.whfin.data.savings.projectSavings
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun savingsDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]),
)

@Composable
internal fun SavingsDeadlineReading(
    projection: SavingsProjection,
    date: LocalDate,
    currency: String,
    onUseRequired: ((Long) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.savings_projection_at_date, savingsDate(date)), style = MaterialTheme.typography.bodySmall)
        WhfinAmount(
            projection.balanceOnTargetDateMinor?.let { formatMinor(it, currency) } ?: "—",
            symbol = currencySymbol(currency), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("savings-projected-at-date"),
        )
        projection.requiredMonthlyMinor?.let { required ->
            if (required > 0L) {
                if (onUseRequired != null) WhfinButton(
                    stringResource(R.string.savings_projection_use_pace, formatMinor(required, currency)),
                    { onUseRequired(required) }, style = WhfinActionStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                ) else Text(stringResource(R.string.savings_goal_required, formatMinor(required, currency)),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (projection.requiredMonthlyMinor == null) Text(
            stringResource(R.string.savings_projection_no_installment),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SavingsProjectionPanel(
    balanceMinor: Long,
    monthlyMinor: Long,
    currency: String,
    goalMinor: Long?,
    targetDate: LocalDate?,
    today: LocalDate,
    history: List<SavingsMonthUi> = emptyList(),
    onUseRequired: ((Long) -> Unit)? = null,
) {
    var horizon by rememberSaveable { mutableIntStateOf(12) }
    val projection = remember(balanceMinor, monthlyMinor, goalMinor, targetDate, today, horizon) {
        projectSavings(balanceMinor, monthlyMinor, today, goalMinor, targetDate, horizon)
    }
    var selected by rememberSaveable { mutableIntStateOf(12) }
    val index = selected.coerceIn(0, projection.points.lastIndex)
    val point = projection.points[index]
    val dateFormatter = DateTimeFormatter.ofPattern("MMM yy", LocalConfiguration.current.locales[0])
    val projectionLabel = stringResource(R.string.savings_view_projection)
    val actualLabel = stringResource(R.string.savings_projection_actual)
    val recorded = history.filter { it.month.atEndOfMonth() < today }.takeLast(3).map {
        WhfinSavingsBalancePoint(it.month.atEndOfMonth().format(dateFormatter), it.reserveBalanceMinor,
            "$actualLabel ${formatMinor(it.reserveBalanceMinor, currency)}", position = it.month.atEndOfMonth().toEpochDay())
    }
    val future = projection.points.mapIndexed { i, value ->
        WhfinSavingsBalancePoint(
            value.date.format(dateFormatter), value.balanceMinor,
            "${if (i == 0) actualLabel else projectionLabel} ${formatMinor(value.balanceMinor, currency)}",
            periodDescription = savingsDate(value.date), isProjected = i > 0, position = value.date.toEpochDay(),
        )
    }
    Column(Modifier.fillMaxWidth().testTag("savings-projection"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinSectionHeader(stringResource(R.string.savings_projection_title))
        if (goalMinor != null) Text(
            when {
                goalMinor <= balanceMinor -> stringResource(R.string.savings_goal_reached)
                projection.goalReachedOn != null -> stringResource(R.string.savings_projection_goal_date, savingsDate(projection.goalReachedOn))
                else -> stringResource(R.string.savings_projection_no_pace)
            },
            modifier = Modifier.testTag("savings-projected-goal-date"),
            style = MaterialTheme.typography.titleMedium,
        )
        WhfinChoiceRail {
            listOf(12, 24, 60).forEach { months ->
                item {
                    WhfinFilterPill(stringResource(R.string.savings_projection_years, months / 12),
                        horizon == months, { horizon = months; selected = months })
                }
            }
        }
        WhfinSavingsBalanceChart(recorded + future, goalMinor = goalMinor,
            goalDescription = goalMinor?.let { stringResource(R.string.savings_goal_description, formatMinor(it, currency)) },
            selectedIndex = recorded.size + index)
        Text(stringResource(R.string.savings_projection_legend), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(savingsDate(point.date), style = MaterialTheme.typography.bodyMedium)
            WhfinAmount(formatMinor(point.balanceMinor, currency), symbol = currencySymbol(currency),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("savings-projection-selected-amount"))
        }
        WhfinTimelineScrubber(index, projection.points.size,
            stringResource(R.string.savings_projection_scrub), { selected = it })
        if (projection.exceedsMoneyRange) Text(stringResource(R.string.savings_projection_overflow), style = MaterialTheme.typography.bodySmall)
        if (targetDate != null && goalMinor != null) SavingsDeadlineReading(projection, targetDate, currency, onUseRequired)
        Text(stringResource(R.string.savings_projection_assumption, savingsDate(today.plusMonths(1))),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
