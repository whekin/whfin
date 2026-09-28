package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class BankSyncIndicatorTest {
    @Test fun completionRequiresEveryBankToHaveActuallyCompleted() {
        assertEquals(BankSyncIndicatorState.Idle, bankSyncIndicatorState(emptyList()))
        val complete = BankSyncStatus("Credo", 1, false, SyncPhase.COMPLETE)
        assertEquals(BankSyncIndicatorState.Complete, bankSyncIndicatorState(listOf(complete)))
        SyncPhase.entries.filter { it != SyncPhase.COMPLETE }.forEach { phase ->
            assertEquals(BankSyncIndicatorState.Attention, bankSyncIndicatorState(listOf(complete,
                BankSyncStatus("TBC", 2, false, phase))))
        }
        assertEquals(BankSyncIndicatorState.Active, bankSyncIndicatorState(listOf(complete,
            BankSyncStatus("TBC", 2, true, SyncPhase.READING))))
    }
}
