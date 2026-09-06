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
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertTrue
import kotlin.math.abs

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
        compose.onNodeWithText("443.22 ₾ above the recorded average").assertIsDisplayed()
        // The base is spelled out: three named months, not "by this day".
        compose.onNodeWithText("Averaged over May, June, July").assertIsDisplayed()
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
    fun theBaseNeverClaimsAMonthWasRecordedInFull() {
        content(AnalyticsScenario.Shape.DEARER)

        // A month holding one row is not a month that held one payment, and WHFIN cannot show any
        // month complete: it records statement periods per imported account and nothing for cash,
        // manual entries or an account nobody imported. So the average is called what it provably
        // is, and the rest is said quietly next to it.
        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference-limit"))
        compose.onNodeWithText(
            "Counted from recorded spending; those months may be incomplete",
        ).assertIsDisplayed()
    }

    @Test
    fun everyBarIsMeasuredFromOneAxis() {
        content(AnalyticsScenario.Shape.DEARER)

        // Scrolled so the remainder is on screen with the rows: it is the row most likely to drift,
        // having no icon of its own and a different trailing amount.
        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-changes-rest"))
        val axes = compose.onAllNodesWithTag("analytics-change-axis", useUnmergedTree = true)
            .fetchSemanticsNodes()
            // A row scrolled past the bottom of a short test window is composed but never placed,
            // and an unplaced node reports an empty rect rather than a position.
            .filterNot { it.boundsInRoot.isEmpty }
            .map { it.boundsInRoot.left }
        // The bars used to live inside the text column, whose width followed the width of the
        // amount printed beside it, so each row put its zero somewhere else and the column of bars
        // compared nothing. Rows may differ in height; their zero may not differ in place.
        assertTrue("expected several bars, found ${axes.size}", axes.size >= 3)
        assertTrue("zero marks drift: $axes", axes.all { abs(it - axes.first()) < 0.5f })
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
        compose.onNodeWithText("2,650.00 ₾ above the recorded average").assertIsDisplayed()
    }

    @Test
    fun aQuietMonthReadsAsLessThanUsual() {
        content(AnalyticsScenario.Shape.CHEAPER)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference"))
        compose.onNodeWithText("451.78 ₾ below the recorded average").assertIsDisplayed()
    }

    @Test
    fun theShortHistoryNamesHowMuchOfItIsKnown() {
        content(AnalyticsScenario.Shape.SHORT_HISTORY)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference-base"))
        compose.onNodeWithText("Averaged over July · 1 of 3 months recorded").assertIsDisplayed()
    }

    @Test
    fun aRunningMonthNamesTheDaysItCompared() {
        content(AnalyticsScenario.Shape.DEARER, running = true)

        compose.onNodeWithTag("analytics-list").performScrollToNode(hasTestTag("analytics-difference-base"))
        compose.onNodeWithText("Averaged over days 1–10 of May, June, July").assertIsDisplayed()
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
