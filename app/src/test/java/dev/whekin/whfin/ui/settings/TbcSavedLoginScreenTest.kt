package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.whekin.whfin.ui.theme.WhfinTheme
import androidx.compose.runtime.*
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcSavedLoginScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun connectedBankCanReturnToPersonalSetupWithoutBackGesture() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var completed = false
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected), true,
            onDone = { completed = true }) } }
        compose.onNodeWithText(context.getString(R.string.action_done)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(completed) }
    }
    @Test fun leavingWithUnloadedAccountsRequiresConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val remote = dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
        var completed = false
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected,
            syncResult = dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = listOf(remote))), true,
            onDone = { completed = true }) } }
        compose.onNodeWithText(context.getString(R.string.action_done)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tbc_balance_setup_later)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tbc_result_options)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.tbc_balance_setup_later)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(completed) }
        compose.onNodeWithText(context.getString(R.string.tbc_continue_later_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertFalse(completed) }
    }
    @Test fun savedSessionDoesNotAskForPasswordAgain() {
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(hasSaved = true), true) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun passwordFormRequiresAnExplicitChoiceAndCanReturnToSavedSignIn() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(hasSaved = true), true) } }
        compose.onNodeWithText(context.getString(R.string.tbc_use_password)).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.tbc_back_to_saved)).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun rememberLabelAndThumbShareOneSwitchTarget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var calls = 0
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            WhfinTheme { TbcLoginScreen(TbcLoginState(remember = checked), true,
                onRemember = { calls++; checked = it }) }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.tbc_remember)).performScrollTo().performClick()
        compose.onNode(isToggleable()).assertIsOn()
        assertEquals(1, calls)
    }

    @Test fun aCodeThatArrivedByItselfSubmitsItselfLikeCredo() {
        var submitted: String? = null
        compose.setContent {
            var incoming by remember { mutableStateOf<String?>("246810") }
            WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Code), true,
                incomingOtp = incoming, onOtpConsumed = { incoming = null }, onCode = { submitted = it }) }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertEquals("246810", submitted) }
    }

    @Test fun anAppGeneratedCodeIsNeverSubmittedForTheOwner() {
        var submitted: String? = null
        compose.setContent {
            WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Code, otpApp = true), true,
                incomingOtp = "246810", onCode = { submitted = it }) }
        }
        compose.runOnIdle { assertNull(submitted) }
    }

    @Test fun anAppliedBalanceMovesTheAccountFromWaitingToReporting() {
        val remote = dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
        val before = dev.whekin.whfin.data.importer.TbcSyncResult(
            needsStatement = listOf(remote),
            initialHistories = listOf(dev.whekin.whfin.data.importer.TbcInitialHistory(
                remote, java.time.LocalDate.now(), java.time.LocalDate.now(), emptyList())),
            reports = listOf(dev.whekin.whfin.data.importer.TbcSyncReport(remote.label, 7, waitingForBalance = true)))
        val after = before.afterInitialBalance(remote, dev.whekin.whfin.data.importer.TbcInitializationResult(7, 1))
        assertTrue(after.needsStatement.isEmpty())
        assertTrue(after.initialHistories.isEmpty())
        assertEquals(7, after.inserted)
        assertEquals(1, after.matched)
        val report = after.reports.single()
        assertFalse(report.waitingForBalance)
        assertEquals(7, report.inserted)
        assertEquals(1, report.matched)
    }

    @Test fun fourZeroBalancesNeedOneExplicitBatchConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val remotes = listOf("GEL", "USD", "EUR", "GBP").map { currency ->
            dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", currency, "Everyday", balanceMinor = 0)
        }
        var confirmed: List<Pair<String, Long>>? = null
        compose.setContent {
            WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected,
                syncResult = dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = remotes,
                    initialHistories = remotes.map { dev.whekin.whfin.data.importer.TbcInitialHistory(
                        it, java.time.LocalDate.now(), java.time.LocalDate.now(), emptyList()) })), true,
                onConfirmBalances = { confirmed = it }) }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.tbc_balance_save_and_load)).assertDoesNotExist()
        repeat(3) {
            compose.onNodeWithText(context.getString(R.string.tbc_balance_next)).performClick()
        }
        compose.onNodeWithText(context.getString(R.string.tbc_balance_save_and_load)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(remotes.map { it.key to 0L }, confirmed) }
    }

    @Test fun manualOtpUsesBuiltInKeypadAndExplicitConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var submitted: String? = null
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Code), true,
            onCode = { submitted = it }) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        "2468".forEach { compose.onNodeWithText(it.toString()).performScrollTo().performClick() }
        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_delete_digit)).performScrollTo().performClick()
        compose.onNodeWithText("0").performScrollTo().performClick()
        assertNull(submitted)
        compose.onNodeWithText(context.getString(R.string.tbc_confirm)).performScrollTo().performClick()
        assertEquals("2460", submitted)
    }

    @Test fun theBanksOwnFigureIsOfferedForCheckingRatherThanTypedFromScratch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val known = dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL",
            "Everyday", balanceMinor = -1234L)
        val silent = dev.whekin.whfin.data.tbc.TbcLedgerAccount("11", "GE00TB0000000000000002", "USD", "Travel")
        var confirmed: List<Pair<String, Long>>? = null
        val remotes = listOf(known, silent)
        compose.setContent {
            WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected,
                syncResult = dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = remotes,
                    initialHistories = remotes.map { dev.whekin.whfin.data.importer.TbcInitialHistory(
                        it, java.time.LocalDate.now(), java.time.LocalDate.now(), emptyList()) })), true,
                onConfirmBalances = { confirmed = it }) }
        }
        compose.onNodeWithText("-12.34").assertExists()
        // Nothing is asserted about an account the bank said nothing about.
        compose.onAllNodesWithText(context.getString(R.string.tbc_balance_prefilled)).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.tbc_balance_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.tbc_balance_save_and_load)).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("0")
        compose.onNodeWithText(context.getString(R.string.tbc_balance_save_and_load)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(known.key to -1234L, silent.key to 0L), confirmed) }
    }

    @Test fun bookedBalanceAcceptsZeroAndDebtWithoutTruncationOrOverflow() {
        listOf("0", "0.00", "0,00", " 0 ").forEach { assertEquals(0L, parseBookedBalance(it)) }
        assertEquals(-1234L, parseBookedBalance("-12,34"))
        listOf("", "-", "1.234", "9223372036854775808", "1.2.3").forEach { assertNull(parseBookedBalance(it)) }
        assertNull(dev.whekin.whfin.ui.parseToMinor("0")) // Transaction forms still reject zero.
    }

    @Test fun reviewingPreviousCurrencyKeepsDraftAndNeverWritesBeforeFinalConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val remotes = listOf("GEL", "USD").map { currency ->
            dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", currency,
                "Everyday", balanceMinor = 0)
        }
        var calls = 0
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(stage = TbcLoginStage.Connected,
            syncResult = dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = remotes,
                initialHistories = remotes.map { dev.whekin.whfin.data.importer.TbcInitialHistory(
                    it, java.time.LocalDate.now(), java.time.LocalDate.now(), emptyList()) })), true,
            onConfirmBalances = { calls++ }) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("-12.34")
        compose.onNodeWithText(context.getString(R.string.tbc_balance_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.tbc_balance_previous_short)).performScrollTo().performClick()
        compose.onNodeWithText("-12.34").assertExists()
        assertEquals(0, calls)
        compose.onNodeWithText(context.getString(R.string.tbc_balance_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.tbc_balance_save_and_load)).performClick()
        assertEquals(1, calls)
    }

}
