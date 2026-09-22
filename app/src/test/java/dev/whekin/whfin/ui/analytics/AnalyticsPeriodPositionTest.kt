package dev.whekin.whfin.ui.analytics

import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyticsPeriodPositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingNeighbouringMonthKeepsTheChartUnderTheFinger() = switchingMonths(false)
    @Test fun expenseChartStaysPutWhenMerchantRowsChange() = switchingMonths(true)

    @Test fun topArrowsKeepThePeriodControlsVisibleWhenAShortMonthGrows() {
        var month by mutableStateOf(YearMonth.of(2026, 6))
        val rows = AnalyticsScenario.transactions(AnalyticsScenario.Shape.DEARER).filter {
            java.time.Instant.ofEpochMilli(it.occurredAt).atZone(AnalyticsScenario.zone).monthValue != 6
        }
        compose.setContent { WhfinTheme { Surface {
            val data = calculateAnalytics(rows, AnalyticsScenario.categories, emptyList(), AnalyticsPeriod.month(month), AnalyticsTrendFilter.All,
                zoneId = AnalyticsScenario.zone, today = AnalyticsScenario.insideSelectedMonth, merchants = AnalyticsScenario.merchants)
            ExpenseAnalysisContent(AnalyticsUiModel(data.period, true, true, AnalyticsUiState.Content(data)), {}, {},
                { month = month.plusMonths(1) }, {}, {}, {}, {}, {})
        } } }
        val before = compose.onNodeWithTag("analytics-period-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithContentDescription(context.getString(dev.whekin.whfin.R.string.analytics_next_period)).performClick()
        compose.onNodeWithTag("analytics-period-title", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("analytics-period-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun anIntermediateCalculationDoesNotDiscardTheLatestTapAnchor() {
        var shown by mutableStateOf(AnalyticsScenario.selectedMonth)
        val requests = mutableListOf<YearMonth>()
        val rows = AnalyticsScenario.transactions(AnalyticsScenario.Shape.DEARER)
        compose.setContent { WhfinTheme { Surface {
            val data = calculateAnalytics(rows, AnalyticsScenario.categories, emptyList(), AnalyticsPeriod.month(shown), AnalyticsTrendFilter.All,
                zoneId = AnalyticsScenario.zone, today = AnalyticsScenario.insideSelectedMonth, merchants = AnalyticsScenario.merchants)
            AnalyticsContent(AnalyticsUiModel(data.period, true, true, AnalyticsUiState.Content(data)), null, {}, {}, {},
                { requests += it }, {}, {}, {})
        } } }
        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-trend"))
        val before = compose.onNodeWithTag("whfin-monthly-bar-6").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("whfin-monthly-bar-6").performClick()
        compose.onNodeWithTag("whfin-monthly-bar-4").performClick()
        compose.runOnIdle { assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 5)), requests) }
        listOf(7, 5).forEach { month ->
            compose.runOnIdle { shown = YearMonth.of(2026, month) }
            assertEquals(before, compose.onNodeWithTag("whfin-monthly-bar-6").fetchSemanticsNode().boundsInRoot.top, 2f)
        }
    }

    private fun switchingMonths(expenses: Boolean) {
        var month by mutableStateOf(AnalyticsScenario.selectedMonth)
        val list = androidx.compose.foundation.lazy.LazyListState()
        val rows = AnalyticsScenario.transactions(AnalyticsScenario.Shape.DEARER)
        compose.setContent { WhfinTheme { Surface {
            val data = calculateAnalytics(rows, AnalyticsScenario.categories,
                allocations = emptyList(), trendFilter = AnalyticsTrendFilter.All,
                period = AnalyticsPeriod.month(month), today = AnalyticsScenario.insideSelectedMonth,
                zoneId = AnalyticsScenario.zone, merchants = AnalyticsScenario.merchants)
            val model = AnalyticsUiModel(data.period, true, true, AnalyticsUiState.Content(data))
            if (expenses) ExpenseAnalysisContent(model,
                onBack = {}, onPreviousPeriod = { month = month.minusMonths(1) },
                onNextPeriod = { month = month.plusMonths(1) }, onScaleChange = {},
                onSelectMonth = { month = it }, onShowAllTrend = {}, onShowCategoryTrend = {}, onOpenTransactions = {}, listState = list)
            else AnalyticsContent(model,
                onBack = null, onPreviousPeriod = { month = month.minusMonths(1) },
                onNextPeriod = { month = month.plusMonths(1) }, onScaleChange = {},
                onSelectMonth = { month = it }, onShowAllTrend = {}, onOpenExpenses = {}, onOpenTransactions = {}, listState = list)
        } } }
        compose.onNodeWithTag(if (expenses) "expense-analysis-list" else "analytics-list")
            .performScrollToNode(hasTestTag(if (expenses) "expense-analysis-trend" else "analytics-trend"))
        listOf(7, 8, 5, 1, 8).forEach { selected ->
            val before = compose.onNodeWithTag("whfin-monthly-bar-6").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithTag("whfin-monthly-bar-${selected - 1}").performClick()
            compose.runOnIdle { assertEquals(YearMonth.of(2026, selected), month) }
            val after = compose.onNodeWithTag("whfin-monthly-bar-6").fetchSemanticsNode().boundsInRoot.top
            if (kotlin.math.abs(before - after) > 2f) {
                // A shorter-than-viewport empty period may reach the physical start of the list.
                // It must never invent blank space above the heading just to preserve an offset.
                compose.runOnIdle {
                    if (after < before) assertTrue("Only the start boundary can move the chart up", !list.canScrollBackward)
                    else assertTrue("Only the end boundary can move the chart down", !list.canScrollForward)
                }
            } else assertEquals(before, after, 2f)
        }
    }
}
