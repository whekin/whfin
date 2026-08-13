package dev.whekin.whfin.ui.settings

import dev.whekin.whfin.data.credo.CredoRemoteAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredoGuidedSetupTest {
    private val account = CredoRemoteAccount(
        accountNumber = "GE00TEST",
        currency = "GEL",
        accountId = 1,
        category = null,
        type = "ACCOUNT",
    )

    @Test
    fun `guided setup starts full history once after first login`() {
        val connected = CredoSyncUiState(
            stage = CredoSyncStage.Connected,
            accounts = listOf(account),
            hasImportedHistory = false,
        )

        assertTrue(shouldAutoLoadFullHistory(true, false, connected))
        assertFalse(shouldAutoLoadFullHistory(true, true, connected))
        assertFalse(shouldAutoLoadFullHistory(false, false, connected))
    }

    @Test
    fun `guided setup advances only after a successful running pass`() {
        val successful = CredoSyncUiState(
            stage = CredoSyncStage.Connected,
            accounts = listOf(account),
            hasImportedHistory = true,
            unchanged = 1,
        )
        val failed = successful.copy(
            unchanged = 0,
            results = listOf(CredoSyncFileResult(account.maskedLabel, errorCode = "NETWORK_ERROR")),
            retryableFailures = 1,
        )

        assertTrue(guidedHistoryCompletedSuccessfully(true, true, true, successful))
        assertFalse(guidedHistoryCompletedSuccessfully(true, true, false, successful))
        assertFalse(guidedHistoryCompletedSuccessfully(true, true, true, failed))
    }
}
