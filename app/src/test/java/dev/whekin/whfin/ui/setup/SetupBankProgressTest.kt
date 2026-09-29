package dev.whekin.whfin.ui.setup

import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupBankProgressTest {
    @Test fun bankHistoryRunsInBackgroundAndDoesNotClaimCompletionEarly() {
        val reading = BankSyncStatus("Credo", 9, active = true, phase = SyncPhase.HISTORY,
            current = 2, total = 5, canReturnHome = true)
        assertEquals(SetupBankProgress(SetupBankProgressKind.LOADING, current = 2, total = 5),
            setupBankProgress(reading, accountCount = 5, hasImportedHistory = true))
        assertTrue(shouldAdvanceAfterBankSignIn(SetupPage.Credo, SetupPage.Credo, 8, reading))
        assertFalse(shouldAdvanceAfterBankSignIn(SetupPage.Credo, SetupPage.Credo, 9, reading))
        assertFalse(shouldAdvanceAfterBankSignIn(SetupPage.Credo, SetupPage.Tbc, 8, reading))
    }

    @Test fun tbcBalanceReviewStaysVisibleEvenWhenSomeLedgersAlreadyLoaded() {
        val progress = setupBankProgress(null, accountCount = 5, hasImportedHistory = true, balancesToReview = 3)
        assertEquals(SetupBankProgressKind.REVIEW_BALANCES, progress.kind)
        assertTrue(progress.needsAction)
        assertEquals(3, progress.count)
    }

    @Test fun aFinishedRunWithFailuresDoesNotPretendToBeReady() {
        val status = BankSyncStatus("Credo", 12, active = false, phase = SyncPhase.ATTENTION)
        assertEquals(SetupBankProgressKind.NEEDS_ATTENTION,
            setupBankProgress(status, accountCount = 4, hasImportedHistory = true).kind)
    }

    @Test fun anOldImportDoesNotProveTheFullBankHistoryFinished() {
        val oldImport = setupBankProgress(null, accountCount = 5, hasImportedHistory = true)
        assertEquals(SetupBankProgressKind.HISTORY_AVAILABLE, oldImport.kind)
        assertFalse(oldImport.historyPending)
        val completed = BankSyncStatus("Credo", 12, active = false, phase = SyncPhase.COMPLETE,
            canReturnHome = true)
        assertEquals(SetupBankProgressKind.HISTORY_LOADED,
            setupBankProgress(completed, accountCount = 5, hasImportedHistory = true).kind)
    }

    @Test fun firstBankReturnsToBankSelectionAndASecondCallbackCannotPopAnotherPage() {
        val first = returnFromBankPage(listOf(SetupPage.Credo.savedKey), SetupStage.Banks,
            SetupPage.Credo)
        assertEquals(SetupBankReturn(emptyList(), SetupStage.Banks), first)
        assertEquals(first, returnFromBankPage(first.stack, first.stage, SetupPage.Credo))
        assertEquals(SetupBankReturn(emptyList(), SetupStage.Sms),
            returnFromBankPage(listOf(SetupPage.Tbc.savedKey), SetupStage.Sms,
                SetupPage.Tbc))
    }
    @Test fun tbcNeverLeavesBeforeTheOwnerCanReviewItsInitialBalances() {
        val reading = BankSyncStatus("TBC", 2, active = true, phase = SyncPhase.HISTORY, canReturnHome = true)
        assertFalse(shouldAdvanceAfterBankSignIn(SetupPage.Tbc, SetupPage.Tbc, 1, reading))
        assertFalse(shouldAdvanceAfterBankSignIn(SetupPage.Tbc, SetupPage.Tbc, 1,
            reading.copy(active = false, phase = SyncPhase.ATTENTION)))
    }

}
