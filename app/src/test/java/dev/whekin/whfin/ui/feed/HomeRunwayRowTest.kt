package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class HomeRunwayRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortfall_namesOrdinaryRateRecurringChargeAndPayday() {
        compose.setContent {
            WhfinTheme {
                HomeRunwayRow(
                    runway = HomeRunway(
                        daysLeft = 9,
                        dailyBurnMinor = 10_000,
                        nextIncome = NextPayday(
                            usual = LocalDate.of(2026, 9, 5),
                            expected = LocalDate.of(2026, 9, 4),
                            weekendAdjusted = true,
                        ),
                        shortOfIncome = true,
                        shortfallMinor = 130_000,
                        recurringOccurrences = listOf(
                            RecurringOccurrence(
                                charge = RecurringCharge(
                                    key = "iban:landlord",
                                    label = "Landlord",
                                    typicalMinor = 120_000,
                                    expectedDay = 3,
                                    lastSeen = LocalDate.of(2026, 8, 3),
                                ),
                                dueDate = LocalDate.of(2026, 9, 3),
                            ),
                        ),
                        expectedExpenseMinor = 250_000,
                        remainingMinor = -130_000,
                    ),
                    onOpenAccounts = {},
                )
            }
        }

        compose.onNodeWithText("May be 1,300.00 ₾ short by 4 Sep").assertExists()
        compose.onNodeWithText("Day-to-day: ~100.00 ₾ a day", substring = true).assertExists()
        compose.onNodeWithText("Landlord 1,200.00 ₾ on 3 Sep", substring = true).assertExists()
        // The dates are marks on the rule now, so the sentence naming the whole payday window
        // waits inside the calculation instead of repeating what the drawing already says.
        compose.onAllNodesWithText("usual date 5 Sep · with weekends 4 Sep", substring = true)
            .assertCountEquals(0)
        compose.onNodeWithContentDescription("Calculation details").performClick()
        compose.onNodeWithText("usual date 5 Sep · with weekends 4 Sep", substring = true).assertExists()
        compose.onNodeWithText("~1,200.00 ₾ · expected 3 Sep").assertExists()
        compose.onNodeWithText("Future one-off purchases are not predicted.", substring = true).assertDoesNotExist()
    }

    @Test fun passedPaydayLeavesTheCashReadingRatherThanAnUnclearPaymentBanner() {
        var openedAccounts = false
        compose.setContent { WhfinTheme {
            HomeRunwayRow(HomeRunway(daysLeft = 4, dailyBurnMinor = 10_000,
                nextIncome = NextPayday(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5),
                    weekendAdjusted = false, passed = true), shortOfIncome = false),
                onOpenAccounts = { openedAccounts = true })
        } }
        compose.onNodeWithText("Lasts 4 days").assertExists()
        compose.onNodeWithText("Waiting for the payment").assertDoesNotExist()
        compose.onNodeWithText("Lasts 4 days").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(openedAccounts) }
    }
}
