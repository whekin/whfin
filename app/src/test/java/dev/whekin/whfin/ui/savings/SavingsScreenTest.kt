package dev.whekin.whfin.ui.savings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
    fun noPlanExplainsTheDeclarationAndOpensItsEditor() {
        compose.setContent { content(data(plan = null)) }

        compose.onNodeWithText("Set a steady pace").assertExists()
        compose.onNodeWithText("Set monthly plan").performClick()
        compose.onNodeWithText("Amount per month").assertExists()
    }

    @Test
    fun paceChartKeepsCurrentPlanAndSignedHistoryTogether() {
        compose.setContent { content(data(plan = plan())) }

        compose.onNodeWithTag("savings-list").performScrollToIndex(4)
        compose.onNodeWithTag("whfin-savings-pace-bar-11").assertExists()
        compose.onNodeWithContentDescription("December 2026, +240.00 ₾, Current monthly plan 1,000.00 ₾")
            .assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun content(value: SavingsScreenData) {
        WhfinTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                SavingsScreen(value, { _, _, _ -> }, {})
            }
        }
    }

    private fun data(plan: SavingsPlanEntity?): SavingsScreenData {
        val months = (1..12).map { month ->
            SavingsMonthUi(
                month = YearMonth.of(2026, month),
                reserveBalanceMinor = 1_000_000L + month * 20_000L,
                paceMinor = if (month == 4) -10_000L else month * 2_000L,
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
