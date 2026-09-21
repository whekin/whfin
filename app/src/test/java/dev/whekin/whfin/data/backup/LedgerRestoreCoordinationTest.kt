package dev.whekin.whfin.data.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LedgerRestoreCoordinationTest {
    @Test fun `a reserved bank or crypto read prevents restore until completion`() = runTest {
        val lease = LedgerRestoreState.beginRead()
        try {
            assertTrue(runCatching { LedgerRestoreState.during { } }.exceptionOrNull() is LedgerBusyException)
            assertFalse(LedgerRestoreState.active.value)
        } finally { lease.close(); lease.close() }
        LedgerRestoreState.during { assertTrue(LedgerRestoreState.active.value) }
    }

    @Test fun `a restore prevents new network work and a failed read releases its lease`() = runTest {
        LedgerRestoreState.during {
            assertTrue(runCatching { LedgerRestoreState.beginRead() }.exceptionOrNull() is LedgerBusyException)
        }
        runCatching { LedgerRestoreState.reading { error("network failed") } }
        LedgerRestoreState.during { assertTrue(LedgerRestoreState.active.value) }
    }

    @Test fun `a second restore cannot clear the busy state of the first`() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = launch { LedgerRestoreState.during { release.await() } }
        runCurrent()
        assertTrue(LedgerRestoreState.active.value)
        assertTrue(runCatching { LedgerRestoreState.during { } }.isFailure)
        assertTrue(LedgerRestoreState.active.value)
        release.complete(Unit)
        first.join()
        assertFalse(LedgerRestoreState.active.value)
    }
}
