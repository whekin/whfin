package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
                        nextIncome = NextIncomeWindow(
                            LocalDate.of(2026, 9, 5),
                            LocalDate.of(2026, 9, 10),
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
                    ),
                    onOpenAccounts = {},
                )
            }
        }

        compose.onNodeWithText("May be 1,300.00 ₾ short").assertExists()
        compose.onNodeWithText("Day-to-day: ~100.00 ₾ a day", substring = true).assertExists()
        compose.onNodeWithText("Landlord 1,200.00 ₾ on 3 Sep", substring = true).assertExists()
        compose.onNodeWithText("payday 5–10 Sep", substring = true).assertExists()
        compose.onNodeWithContentDescription("Calculation details").performClick()
        compose.onNodeWithText("~1,200.00 ₾ · expected 3 Sep").assertExists()
        compose.onNodeWithText("Future one-off purchases are not predicted.", substring = true).assertExists()
    }
}
