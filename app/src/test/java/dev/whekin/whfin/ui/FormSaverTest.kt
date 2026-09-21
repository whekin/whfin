package dev.whekin.whfin.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import dev.whekin.whfin.data.backup.LedgerRestoreState
import dev.whekin.whfin.data.backup.LedgerBusyException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FormSaverTest {
    @Test fun `double submit is reserved before coroutine dispatch and closes only after commit`() = runTest {
        val saver = FormSaver(this)
        val commit = CompletableDeferred<Unit>()
        var writes = 0
        saver.save { writes++; commit.await() }
        saver.save { writes++ }
        assertTrue(saver.state.value.busy)
        runCurrent()
        assertEquals(1, writes)
        assertEquals(0L, saver.state.value.completed)
        commit.complete(Unit)
        runCurrent()
        assertEquals(1L, saver.state.value.completed)
        assertFalse(saver.state.value.busy)
    }
    @Test fun `reserved form write blocks restore before dispatch and releases after cancellation`() = runTest {
        val scope = CoroutineScope(coroutineContext + Job())
        val saver = FormSaver(scope)
        saver.save { error("must not start") }
        try { LedgerRestoreState.during { fail("Restore must wait for the reserved submit") }; fail("Expected busy") }
        catch (_: LedgerBusyException) { }
        scope.cancel()
        runCurrent()
        LedgerRestoreState.during { }
        assertFalse(saver.state.value.busy)
    }
    @Test fun `active restore refuses a submit without touching the ledger`() = runTest {
        val saver = FormSaver(this)
        var wrote = false
        LedgerRestoreState.during { saver.save { wrote = true } }
        runCurrent()
        assertFalse(wrote)
        assertTrue(saver.state.value.failed)
        assertFalse(saver.state.value.busy)
    }

    @Test fun `failure leaves form open and permits a later successful retry`() = runTest {
        val saver = FormSaver(this)
        saver.save { error("Disk full") }
        runCurrent()
        assertTrue(saver.state.value.failed)
        assertFalse(saver.state.value.busy)
        assertEquals(0L, saver.state.value.completed)
        saver.save { }
        runCurrent()
        assertFalse(saver.state.value.failed)
        assertEquals(1L, saver.state.value.completed)
    }
}
