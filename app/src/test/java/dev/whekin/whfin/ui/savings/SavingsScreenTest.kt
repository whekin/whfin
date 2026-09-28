package dev.whekin.whfin.ui.savings

import dev.whekin.whfin.ui.bank.SupportedBankApp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyReserveOpensAccountsInsteadOfEndingAtAnExplanation() {
        var opened = false
        compose.setContent {
            WhfinTheme {
                SavingsScreen(
                    data = data(plan = null).copy(hasReserve = false),
                    onSavePlan = { _, _, _ -> },
                    onClearPlan = {},
                    onOpenAccounts = { opened = true },
                )
            }
        }

        compose.onNodeWithText("Go to Accounts").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun noPlanExplainsTheDeclarationAndOpensItsEditor() {
        compose.setContent { content(data(plan = null)) }

        compose.onNodeWithText("Set a steady pace").assertExists()
        compose.onNodeWithText("Set monthly plan").performClick()
        compose.onNodeWithText("Amount per month").assertExists()
    }

    @Test
    fun paceChartKeepsCurrentPlanAndSignedHistoryTogether() {
        compose.setContent { content(data(plan = plan())) }

        compose.onNodeWithTag("savings-list").performScrollToIndex(2)
        compose.onNodeWithText("Pace").performClick()
        compose.onNodeWithTag("savings-list").performScrollToIndex(3)
        compose.onNodeWithTag("whfin-savings-pace-bar-11").assertExists()
        compose.onNodeWithContentDescription("December 2026, +240.00 ₾, Current monthly plan 1,000.00 ₾")
            .assertExists()
    }

    @Test
    fun editorRecalculatesGoalDateBeforeSavingAndOffersACalendar() {
        var writes = 0
        compose.setContent {
            WhfinTheme { SavingsPlanEditorContent(
                plan(), "GEL", 1_800_000, LocalDate.of(2026, 8, 27), {},
                { _, _, _ -> writes++ }, null,
            ) }
        }
        compose.onNodeWithTag("savings-projected-goal-date").performScrollTo()
            .assertTextEquals("Goal around Aug 27, 2027 at this pace")
        compose.onNodeWithContentDescription("Amount per month").performScrollTo().performTextReplacement("2000")
        compose.onNodeWithTag("savings-projected-goal-date").performScrollTo()
            .assertTextEquals("Goal around Feb 27, 2027 at this pace")
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("savings-open-calendar").performScrollTo().performClick()
        compose.onNodeWithTag("savings-date-picker").assertExists()
        compose.onNodeWithTag("savings-projected-at-date").assertTextEquals("30,000.00 ₾")
        compose.onNodeWithText("Monday, February 15, 2027", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("savings-projected-at-date").assertTextEquals("28,000.00 ₾")
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test
    fun savedPlanShowsFutureGrowthEvenWithoutAGoal() {
        compose.setContent { content(data(plan = plan().copy(goalMinor = null))) }
        compose.onNodeWithTag("savings-list").performScrollToIndex(3)
        compose.onNodeWithTag("savings-projection").assertExists()
        compose.onNodeWithContentDescription("Explore future reserve by month").assertExists()
        compose.onNodeWithContentDescription("Explore future reserve by month")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("18,400.00 ₾")
    }

    @Test
    fun chartTouchAndAccessibilityShareTheExactForecastSelection() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            WhfinTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                SavingsProjectionPanel(1_800_000, 100_000, "GEL", null, null, LocalDate.of(2026, 9, 29))
            } }
        }
        val plot = compose.onNodeWithTag("whfin-savings-balance-plot", useUnmergedTree = true)
        plot.performScrollTo().performTouchInput { click(Offset(0f, centerY)) }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("18,000.00 ₾")
        plot.performTouchInput { swipe(Offset(0f, centerY), Offset(width.toFloat(), centerY), 500) }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("30,000.00 ₾")
        compose.onNodeWithTag("savings-forecast-chart")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("24,000.00 ₾")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("24,000.00 ₾")
    }

    @Test
    fun historicalBalanceTouchUpdatesTheExistingExactReading() {
        compose.setContent { content(data(plan())) }
        compose.onNodeWithTag("savings-list").performScrollToIndex(2)
        compose.onNodeWithText("Balance").performClick()
        compose.onNodeWithTag("whfin-savings-balance-plot", useUnmergedTree = true)
            .performScrollTo().performTouchInput { click(Offset(0f, centerY)) }
        compose.onNodeWithText("10,200.00 ₾").assertExists()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("10,400.00 ₾").assertExists()
    }

    @Test
    fun verticalGestureOverPlotScrollsThePageWithoutChangingTheMonth() {
        compose.setContent {
            WhfinTheme { Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                SavingsProjectionPanel(1_800_000, 100_000, "GEL", null, null, LocalDate.of(2026, 9, 29))
            } }
        }
        val plot = compose.onNodeWithTag("whfin-savings-balance-plot", useUnmergedTree = true)
        plot.performScrollTo()
        val before = plot.fetchSemanticsNode().boundsInRoot.top
        plot.performTouchInput { swipeUp() }
        assertTrue(plot.fetchSemanticsNode().boundsInRoot.top < before)
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("30,000.00 ₾")
    }

    @Test
    fun changingDeadlineSelectsTheLastContributionNotAnInventedPartialPayment() {
        val today = LocalDate.of(2026, 9, 29)
        val deadline = mutableStateOf(today.plusMonths(5).plusDays(4))
        compose.setContent {
            WhfinTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                SavingsProjectionPanel(1_800_000, 100_000, "GEL", 3_000_000, deadline.value, today)
            } }
        }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("23,000.00 ₾")
        compose.runOnIdle { deadline.value = today.plusMonths(2).plusDays(4) }
        compose.onNodeWithTag("savings-projection-selected-amount").assertTextEquals("20,000.00 ₾")
    }

    @Test
    fun matchingInstalledBankOpensFromSavingsNotHome() {
        var opened: SupportedBankApp? = null
        compose.setContent {
            WhfinTheme { SavingsScreen(
                data(plan()).copy(bankApps = listOf(SupportedBankApp.CREDO)),
                { _, _, _ -> }, {}, onOpenBank = { opened = it; true },
            ) }
        }
        compose.onNodeWithText("Open MyCredo").performClick()
        compose.runOnIdle { assertEquals(SupportedBankApp.CREDO, opened) }
    }

    @Test
    fun deadlineCanExplicitlySetThePaceWithoutChangingTheGoal() {
        val today = LocalDate.of(2026, 8, 27)
        val deadline = today.plusMonths(6)
        var savedAmount: Long? = null
        var savedDate: LocalDate? = null
        compose.setContent {
            WhfinTheme { SavingsPlanEditorContent(
                plan().copy(goalBy = deadline.toEpochDay()), "GEL", 1_800_000, today, {},
                { amount, goal, date -> savedAmount = amount; savedDate = date; assertEquals(3_000_000L, goal) }, null,
            ) }
        }
        compose.onNodeWithText("Use 2,000.00 ₾ per month").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(200_000L, savedAmount)
            assertEquals(deadline, savedDate)
        }
    }

    @Test
    fun editorDraftSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            WhfinTheme { SavingsPlanEditorContent(plan(), "GEL", 1_800_000,
                LocalDate.of(2026, 8, 27), {}, { _, _, _ -> }, null) }
        }
        compose.onNodeWithContentDescription("Amount per month").performTextReplacement("2000")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Amount per month").assertTextContains("2000")
        compose.onNodeWithTag("savings-projected-goal-date").performScrollTo()
            .assertTextEquals("Goal around Feb 27, 2027 at this pace")
    }

    @Test
    fun aRangeThatWouldRedrawTheSameChartIsNotOffered() {
        // Twelve months of history IS the last year: two pills, one chart, and the only visible
        // effect of pressing the second one is worse labels.
        compose.setContent { content(data(plan = plan())) }

        compose.onNodeWithTag("savings-list").performScrollToIndex(2)
        compose.onNodeWithText("Pace").performClick()
        compose.onNodeWithText("All time").assertDoesNotExist()
        compose.onNodeWithText("Year").assertDoesNotExist()
    }

    @Test
    fun longerHistoryOffersAllTimeAndMarksTheYearItStartsIn() {
        compose.setContent { content(data(plan = plan(), months = 18)) }

        compose.onNodeWithTag("savings-list").performScrollToIndex(2)
        compose.onNodeWithText("Pace").performClick()
        compose.onNodeWithText("All time").performClick()
        compose.onNodeWithTag("savings-list").performScrollToIndex(3)
        // Eighteen initials would be М, М, И, И, А, А twice over; the axis marks the year instead.
        compose.onNodeWithText("2026").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun content(value: SavingsScreenData) {
        WhfinTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                SavingsScreen(value, { _, _, _ -> }, {})
            }
        }
    }

    private fun data(plan: SavingsPlanEntity?, months: Int = 12): SavingsScreenData {
        val last = YearMonth.of(2026, 12)
        val months = (0 until months).map { back ->
            val month = last.minusMonths((months - 1 - back).toLong())
            SavingsMonthUi(
                month = month,
                reserveBalanceMinor = 1_000_000L + (back + 1) * 20_000L,
                paceMinor = if (month.monthValue == 4) -10_000L else month.monthValue * 2_000L,
                targetMinor = plan?.monthlyTargetMinor,
            )
        }
        return SavingsScreenData(
            currency = "GEL",
            months = months,
            currentPlan = plan,
            currentReserveMinor = months.last().reserveBalanceMinor,
            currentPaceMinor = months.last().paceMinor,
            rollingThreeMonthMinor = 22_000L,
            monthsOnPace = 0,
            evaluatedMonths = 11,
            hasReserve = true,
        )
    }

    private fun plan() = SavingsPlanEntity(
        id = 1,
        currency = "GEL",
        monthlyTargetMinor = 100_000L,
        goalMinor = 3_000_000L,
        startedOn = LocalDate.of(2026, 1, 1).toEpochDay(),
        createdAt = 0L,
    )
}
