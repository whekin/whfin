package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
    fun personalSetupCombinesSmsAndCredoInOneBankAction() {
        var bankRequested = false
        var skipped = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Bank,
                    state = PersonalSetupState(
                        bankLedgerCount = 0,
                        hasCredoImport = false,
                        unresolvedSmsCount = 0,
                        statementReviewCount = 0,
                    ),
                    onConnectBank = { bankRequested = true },
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onSkip = { skipped = true },
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNode(
            hasText(context.getString(R.string.personal_setup_connect_action)) and hasClickAction(),
        ).performClick()
        compose.runOnIdle { assertTrue(bankRequested) }
        compose.onNodeWithText(context.getString(R.string.personal_setup_skip_bank_action)).performClick()
        compose.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun bankStepKeepsStatementAndBackupAsASeparateChoice() {
        var alternatives = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Bank,
                    state = PersonalSetupState(
                        bankLedgerCount = 0,
                        hasCredoImport = false,
                        unresolvedSmsCount = 0,
                        statementReviewCount = 0,
                    ),
                    onConnectBank = {},
                    onShowAlternatives = { alternatives = true },
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onSkip = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_other_action)).performClick()
        compose.runOnIdle { assertTrue(alternatives) }
    }

    @Test
    fun optionalAccountsAreTheirOwnShortStep() {
        var accountRequested = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Accounts,
                    state = PersonalSetupState(
                        accountCount = 3,
                        bankLedgerCount = 2,
                        hasCredoImport = true,
                        smsMonitoringEnabled = true,
                        hasSmsPermission = true,
                        unresolvedSmsCount = 2,
                        statementReviewCount = 0,
                    ),
                    onConnectBank = {},
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = { accountRequested = true },
                    onRestoreBackup = {},
                    onSkip = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_accounts_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.personal_setup_add_account_action)).performClick()
        compose.runOnIdle { assertTrue(accountRequested) }
    }

    @Test
    fun alternativeSetupCanContinueWithoutMyCredo() {
        var continued = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Alternative,
                    state = PersonalSetupState(hasCredoImport = false),
                    onConnectBank = {},
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onSkip = { continued = true },
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.personal_setup_continue_without_bank_action),
        ).performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun completedSetupShowsAReadyOutcome() {
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Ready,
                    state = PersonalSetupState(
                        accountCount = 3,
                        bankLedgerCount = 2,
                        hasCredoImport = true,
                        smsMonitoringEnabled = true,
                        hasSmsPermission = true,
                        unresolvedSmsCount = 0,
                        statementReviewCount = 0,
                    ),
                    onConnectBank = {},
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = {},
                    onRestoreBackup = {},
                    onSkip = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_ready_title))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.personal_setup_continue_action))
            .assertIsDisplayed()
    }

    @Test
    fun cashIsASeparateOptionalStep() {
        var addCash = false
        var skipped = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Cash,
                    state = PersonalSetupState(),
                    onConnectBank = {},
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = { addCash = true },
                    onRestoreBackup = {},
                    onSkip = { skipped = true },
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_cash_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.personal_setup_cash_add_action)).performClick()
        compose.runOnIdle { assertTrue(addCash) }
        compose.onNodeWithText(context.getString(R.string.personal_setup_skip_optional_action)).performClick()
        compose.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun salaryIsASeparateOptionalStep() {
        var addSalary = false
        compose.setContent {
            WhfinTheme {
                PersonalSetupScreen(
                    step = PersonalSetupStep.Salary,
                    state = PersonalSetupState(),
                    onConnectBank = {},
                    onShowAlternatives = {},
                    onImportStatement = {},
                    onCreateAccount = { addSalary = true },
                    onRestoreBackup = {},
                    onSkip = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.personal_setup_salary_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.personal_setup_salary_add_action)).performClick()
        compose.runOnIdle { assertTrue(addSalary) }
    }
}
