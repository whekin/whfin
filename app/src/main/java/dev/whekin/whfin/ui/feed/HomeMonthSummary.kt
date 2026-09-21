package dev.whekin.whfin.ui.feed

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.ui.analytics.AnalyticsCurrencyValue
import dev.whekin.whfin.ui.currencySymbol
import dev.whekin.whfin.ui.formatMinor
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun MonthlyFlowSummary(
    income: Long, expenses: Long, onClick: () -> Unit,
    insights: List<HomeInsight> = emptyList(), unconverted: List<AnalyticsCurrencyValue> = emptyList(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val month = YearMonth.now()
    val monthName = month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }
    val pace = insights.filterIsInstance<HomeInsight.SpendingPace>().firstOrNull()
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.large, color = WhfinThemeTokens.raisedSurface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(monthName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.analytics_open), tint = MaterialTheme.colorScheme.primary)
            }
            val measurer = rememberTextMeasurer()
            val moneyStyle = MaterialTheme.typography.headlineSmall
            val density = LocalDensity.current
            val widest = maxOf(measurer.measure(formatMinor(income, "GEL"), moneyStyle).size.width,
                measurer.measure(formatMinor(expenses, "GEL"), moneyStyle).size.width)
            BoxWithConstraints {
                val stacked = maxWidth < 260.dp || density.fontScale > 1.3f || widest > with(density) { ((maxWidth - 16.dp) / 2).toPx() }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MonthMetric(stringResource(R.string.home_month_spent), expenses, true)
                    MonthMetric(stringResource(R.string.home_month_received), income, false)
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MonthMetric(stringResource(R.string.home_month_spent), expenses, true, Modifier.weight(1f))
                    MonthMetric(stringResource(R.string.home_month_received), income, false, Modifier.weight(1f))
                }
            }
            if (pace != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.home_month_projected,
                        month.atEndOfMonth().format(DateTimeFormatter.ofPattern("d MMM", locale))),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WhfinAmount(formatMinor(pace.projectedExpenseMinor, "GEL"), symbol = currencySymbol("GEL"),
                        style = MaterialTheme.typography.titleLarge)
                    WhfinPaceTrack(expenses, pace.projectedExpenseMinor, pace.typicalMonthExpenseMinor)
                    Text(stringResource(R.string.home_insight_typical_month, formatMinor(pace.typicalMonthExpenseMinor, "GEL")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            insights.filterIsInstance<HomeInsight.CategoryDriver>().firstOrNull()?.let { driver ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (pace == null) stringResource(R.string.home_month_projected_category, driver.name ?: stringResource(R.string.analytics_uncategorized))
                        else driver.name ?: stringResource(R.string.analytics_uncategorized),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WhfinAmount(formatMinor(driver.projectedExpenseMinor, "GEL"), symbol = currencySymbol("GEL"),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_insight_typical_month, formatMinor(driver.typicalMonthExpenseMinor, "GEL")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (unconverted.isNotEmpty()) Text(stringResource(R.string.home_month_unconverted,
                unconverted.joinToString(" · ") { formatMinor(it.expenseMinor, it.currency) }),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthMetric(label: String, amount: Long, expense: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WhfinAmount(formatMinor(amount, "GEL"), symbol = currencySymbol("GEL"),
            style = MaterialTheme.typography.headlineSmall, maxLines = 2,
            color = if (expense) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Month light", widthDp = 400)
@Preview(name = "Month dark", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Month RU large", widthDp = 360, heightDp = 600, locale = "ru", fontScale = 1.5f)
@Composable
internal fun MonthSummaryPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    MonthlyFlowSummary(230000, 72754, {}, listOf(HomeInsight.SpendingPace(99210, 205394), HomeInsight.CategoryDriver("Eating out", 42203, 16344)))
}
