package dev.whekin.whfin.ui.analytics

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h850dp")
class AnalyticsEmptyPeriodTest {
    @get:Rule val compose = createComposeRule()
    @Test fun analyticsDoesNotCallUnvaluedExpensesAnEmptyMonth() = check(false)
    @Test fun spendingDoesNotCallUnvaluedExpensesAnEmptyMonth() = check(true)
    private fun check(expenses: Boolean) {
        val data = calculateAnalytics(listOf(TransactionEntity(accountId = 1, amountMinor = -1000, currency = "USD",
            occurredAt = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            source = TxSource.MANUAL, status = TxStatus.MANUAL)), emptyList(), emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 8)), AnalyticsTrendFilter.All,
            zoneId = ZoneOffset.UTC, today = LocalDate.of(2026, 9, 1))
        val model = AnalyticsUiModel(data.period, true, true, AnalyticsUiState.Content(data))
        compose.setContent { WhfinTheme {
            if (expenses) ExpenseAnalysisContent(model, {}, {}, {}, {}, {}, {}, {}, {})
            else AnalyticsContent(model, null, {}, {}, {}, {}, {}, {}, {})
        } }
        val emptyTag = if (expenses) "expense-analysis-categories" else "analytics-empty-spending"
        compose.onNodeWithTag(if (expenses) "expense-analysis-list" else "analytics-list")
            .performScrollToNode(hasTestTag(emptyTag))
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithTag(emptyTag)
            .assert(hasAnyDescendant(hasText(context.getString(R.string.analytics_other_currencies))))
        compose.onNodeWithText(context.getString(R.string.analytics_expenses_empty_title)).assertDoesNotExist()
    }
}
