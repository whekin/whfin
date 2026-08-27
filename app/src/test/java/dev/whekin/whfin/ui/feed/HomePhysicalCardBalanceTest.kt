package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomePhysicalCardBalanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun criticalBalanceWarnsWithoutOfferingAnInternalTopUp() {
        compose.setContent {
            WhfinTheme {
                HomePhysicalCardBalance(
                    balances = listOf(PhysicalCardHomeBalance(1, "Everyday", 9_999, listOf("0001"))),
                    notificationsEnabled = true,
                    onOpenAccounts = {},
                    onEnableNotifications = {},
                )
            }
        }

        compose.onNodeWithText("Your next grocery payment may fail").assertIsDisplayed()
        compose.onAllNodesWithText("Top up").assertCountEquals(0)
    }
}
