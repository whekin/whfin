package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun welcomeOffersExactlyTheTwoProductChoices() {
        var personal = false
        var demo = false
        compose.setContent {
            WhfinTheme {
                WelcomeChoiceScreen(
                    busy = false,
                    problem = null,
                    onSetUpPersonal = { personal = true },
                    onExploreDemo = { demo = true },
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.welcome_personal_action))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertTrue(personal)
            assertFalse(demo)
        }
        compose.onNodeWithText(context.getString(R.string.welcome_demo_action)).performClick()
        compose.runOnIdle { assertTrue(demo) }
    }

    @Test
    fun personalSetupLeadsWithSmsAndAllowsDeliberateSkip() {
        var smsRequested = false
        var continued = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    state = PersonalSetupState(
                        bankLedgerCount = 0,
                        hasCredoImport = false,
                        unresolvedSmsCount = 0,
                    ),
                    onConnectCredo = {},
                    onEnableSmsMonitoring = { smsRequested = true },
                    onOpenBankSms = {},
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onContinue = { continued = true },
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_enable_sms_action)).performClick()
        compose.runOnIdle { assertTrue(smsRequested) }
        compose.onNodeWithText(context.getString(R.string.personal_setup_skip_action)).performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun credoBecomesThePrimaryNextActionAfterSmsIsReady() {
        var connected = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    state = PersonalSetupState(
                        bankLedgerCount = 0,
                        hasCredoImport = false,
                        smsMonitoringEnabled = true,
                        hasSmsPermission = true,
                        unresolvedSmsCount = 0,
                    ),
                    onConnectCredo = { connected = true },
                    onEnableSmsMonitoring = {},
                    onOpenBankSms = {},
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onContinue = {},
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_connect_and_sync_action)).performClick()
        compose.runOnIdle { assertTrue(connected) }
    }

    @Test
    fun unresolvedSmsBecomesTheLastPrimaryAction() {
        var openedBankSms = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    state = PersonalSetupState(
                        accountCount = 3,
                        bankLedgerCount = 2,
                        hasCredoImport = true,
                        smsMonitoringEnabled = true,
                        hasSmsPermission = true,
                        unresolvedSmsCount = 2,
                    ),
                    onConnectCredo = {},
                    onEnableSmsMonitoring = {},
                    onOpenBankSms = { openedBankSms = true },
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onContinue = {},
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_review_action, 2)).performClick()
        compose.runOnIdle { assertTrue(openedBankSms) }
    }
}
