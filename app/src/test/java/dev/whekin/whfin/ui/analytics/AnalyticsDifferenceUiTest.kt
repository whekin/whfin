package dev.whekin.whfin.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The block that answers "why did this month cost more" on the real calculation.
 *
 * The data here is produced by [AnalyticsScenario] through `calculateAnalytics`, not typed in, so a
 * change to the arithmetic cannot leave these screens asserting numbers the app no longer computes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsDifferenceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun namesTheMainCausesAndKeepsTheRestVisibleAsARemainder() {
        content(AnalyticsScenario.Shape.DEARER)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-changes"))
        compose.onNodeWithText("443.22 ₾ more than usual").assertIsDisplayed()
        // The base is spelled out: three named months, not "by this day".
        compose.onNodeWithText("Usual: May, June, July").assertIsDisplayed()
        listOf("analytics-change-4", "analytics-change-5", "analytics-change-6").forEach { tag ->
            compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        // Groceries and Eating out are not shown, so what they add is stated rather than dropped.
        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-changes-rest"))
        compose.onNodeWithTag("analytics-changes-rest").assertIsDisplayed()
        compose.onNodeWithText("-170.11 ₾").assertIsDisplayed()
    }

    @Test
    fun expandingShowsEveryChangeAndLeavesNoRemainder() {
        content(AnalyticsScenario.Shape.DEARER)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-changes-expand"))
        compose.onNodeWithTag("analytics-changes-expand").performClick()
        compose.waitForIdle()

        listOf("analytics-change-2", "analytics-change-3").forEach { tag ->
            compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        // Nothing is left over once every contribution is on screen.
        compose.onNodeWithTag("analytics-changes-rest").assertDoesNotExist()
    }

    @Test
    fun aContributionOpensThePaymentsBehindIt() {
        var opened: AnalyticsTransactionsRequest? = null
        content(AnalyticsScenario.Shape.DEARER, onOpenTransactions = { opened = it })

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-change-4"))
        compose.onNodeWithTag("analytics-change-4").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(YearMonth.of(2026, 8), opened?.period?.month)
            assertEquals(true, opened?.categoryFilterEnabled)
            assertEquals(4L, opened?.categoryId)
            assertEquals("Health", opened?.filterName)
            assertEquals(550_00L, opened?.expectedExpenseMinor)
        }
    }

    @Test
    fun withoutARecordedHistoryItSaysSoInsteadOfComparing() {
        content(AnalyticsScenario.Shape.NO_HISTORY)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-no-baseline"))
        compose.onNodeWithTag("analytics-no-baseline").assertIsDisplayed()
        compose.onNodeWithTag("analytics-difference").assertDoesNotExist()
    }

    @Test
    fun aRecordedZeroBaselineStatesMoneyAndNeverAPercentage() {
        content(AnalyticsScenario.Shape.ZERO_BASELINE)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference"))
        compose.onNodeWithText("2,650.00 ₾ more than usual").assertIsDisplayed()
    }

    @Test
    fun aQuietMonthReadsAsLessThanUsual() {
        content(AnalyticsScenario.Shape.CHEAPER)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference"))
        compose.onNodeWithText("451.78 ₾ less than usual").assertIsDisplayed()
    }

    @Test
    fun theShortHistoryNamesHowMuchOfItIsKnown() {
        content(AnalyticsScenario.Shape.SHORT_HISTORY)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference-base"))
        compose.onNodeWithText("Usual: July · 1 of 3 months recorded").assertIsDisplayed()
    }

    @Test
    fun aRunningMonthNamesTheDaysItCompared() {
        content(AnalyticsScenario.Shape.DEARER, running = true)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference-base"))
        compose.onNodeWithText("Usual: days 1–10 of May, June, July").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru")
    fun theRussianBlockKeepsItsRowsAtALargeTextSize() {
        content(AnalyticsScenario.Shape.DEARER, fontScale = 1.5f)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-changes"))
        compose.onNodeWithTag("analytics-difference").assertIsDisplayed()
        listOf("analytics-change-4", "analytics-change-5", "analytics-change-6").forEach { tag ->
            compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    private fun content(
        shape: AnalyticsScenario.Shape,
        running: Boolean = false,
        fontScale: Float = 1f,
        onOpenTransactions: (AnalyticsTransactionsRequest) -> Unit = {},
    ) {
        val data = AnalyticsScenario.analytics(
            shape,
            today = if (running) AnalyticsScenario.insideSelectedMonth
            else AnalyticsScenario.afterSelectedMonth,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale),
            ) {
                WhfinTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AnalyticsContent(
                            model = AnalyticsUiModel(
                                period = AnalyticsPeriod.month(AnalyticsScenario.selectedMonth),
                                canSelectPrevious = true,
                                canSelectNext = true,
                                state = if (data.hasAnyTransactions) AnalyticsUiState.Content(data)
                                else AnalyticsUiState.Empty,
                            ),
                            onBack = null,
                            onPreviousPeriod = {},
                            onNextPeriod = {},
                            onScaleChange = {},
                            onSelectMonth = {},
                            onShowAllTrend = {},
                            onOpenExpenses = {},
                            onOpenTransactions = onOpenTransactions,
                        )
                    }
                }
            }
        }
    }
}
