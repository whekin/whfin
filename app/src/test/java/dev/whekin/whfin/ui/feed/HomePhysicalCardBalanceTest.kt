package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.whekin.whfin.ui.bank.SupportedBankApp
import dev.whekin.whfin.ui.demo.DemoWorkspaceProvider
import org.junit.Assert.assertEquals
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

        compose.onNodeWithText("Low balance · a payment may be declined").assertIsDisplayed()
        compose.onNodeWithText("Everyday · ••0001").assertIsDisplayed()
        compose.onNodeWithText("99.99 ₾").assertIsDisplayed()
        compose.onAllNodesWithText("Top up").assertCountEquals(0)
    }

    @Test fun bankLaunchAndNotificationsAreIndependentFromAccountNavigation() {
        var accounts = 0
        var alerts = 0
        var opened: SupportedBankApp? = null
        compose.setContent {
            WhfinTheme {
                HomePhysicalCardBalance(
                    listOf(PhysicalCardHomeBalance(1, "Everyday", 8_600, listOf("0001"), "Credo", SupportedBankApp.CREDO)),
                    notificationsEnabled = false,
                    onOpenAccounts = { accounts++ },
                    onEnableNotifications = { alerts++ },
                    isBankLaunchable = { true },
                    onOpenBank = { opened = it; true },
                )
            }
        }
        compose.onNodeWithText("Open MyCredo").performClick()
        compose.runOnIdle { assertEquals(SupportedBankApp.CREDO, opened); assertEquals(0, accounts); assertEquals(0, alerts) }
        compose.onNodeWithTag("home-card-1").performClick()
        compose.runOnIdle { assertEquals(1, accounts); assertEquals(0, alerts) }
        compose.onNodeWithText("Enable low-balance alerts").performClick()
        compose.runOnIdle { assertEquals(1, alerts) }
    }

    @Test fun demoHidesRealBankAndPermissionActionsEvenWhenInstalled() {
        compose.setContent {
            DemoWorkspaceProvider(active = true, busy = false, problem = null, onUsePersonal = {}) {
                WhfinTheme {
                    HomePhysicalCardBalance(
                        listOf(PhysicalCardHomeBalance(1, "Everyday", 8_600, listOf("0001"), "Credo", SupportedBankApp.CREDO)),
                        notificationsEnabled = false, onOpenAccounts = {}, onEnableNotifications = {},
                        isBankLaunchable = { true }, onOpenBank = { error("Demo must never launch") },
                    )
                }
            }
        }
        compose.onAllNodesWithText("Open MyCredo").assertCountEquals(0)
        compose.onAllNodesWithText("Enable low-balance alerts").assertCountEquals(0)
    }

    @Test fun unavailableBankIsHiddenAndFailedLaunchIsExplained() {
        compose.setContent {
            WhfinTheme {
                HomePhysicalCardBalance(
                    listOf(PhysicalCardHomeBalance(1, "Everyday", 8_600, listOf("0001"), "Credo", SupportedBankApp.CREDO),
                        PhysicalCardHomeBalance(2, "Card", 12_000, listOf("0002"), "TBC", SupportedBankApp.TBC)),
                    notificationsEnabled = true, onOpenAccounts = {}, onEnableNotifications = {},
                    isBankLaunchable = { it == SupportedBankApp.CREDO }, onOpenBank = { false },
                )
            }
        }
        compose.onAllNodesWithText("Open TBC Bank").assertCountEquals(0)
        compose.onNodeWithText("Open MyCredo").performClick()
        compose.onNodeWithText("Bank app could not be opened. Your balance is unchanged.").assertExists()
    }

    @Test fun enoughBalanceDoesNotWarnOrOfferBankAction() {
        compose.setContent {
            WhfinTheme {
                HomePhysicalCardBalance(
                    balances = listOf(PhysicalCardHomeBalance(1, "Everyday", 15_000, listOf("0001"))),
                    notificationsEnabled = true,
                    onOpenAccounts = {},
                    onEnableNotifications = {},
                )
            }
        }
        compose.onNodeWithTag("home-card-balance").assertDoesNotExist()
    }
}
