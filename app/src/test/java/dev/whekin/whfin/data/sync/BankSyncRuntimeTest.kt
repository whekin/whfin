package dev.whekin.whfin.data.sync

import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.WhfinApp
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankSyncRuntimeTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: WhfinApp
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("bank_sync_runtime", 0).edit().clear().commit()
    }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun restoreIsBlockedEvenBeforeBankCoroutineStarts() = runTest(dispatcher) {
        val runtime = BankSyncRuntime(app) {}
        val job = runtime.launch("TBC", dispatcher, {}) { awaitCancellation() }
        assertTrue(runCatching { dev.whekin.whfin.data.backup.LedgerRestoreState.during {} }.isFailure)
        job.cancel()
        runCurrent()
        dev.whekin.whfin.data.backup.LedgerRestoreState.during {}
        runtime.close()
    }

    @Test fun restoreRejectsNewBankWorkWithoutCallingTheGateway() = runTest(dispatcher) {
        val runtime = BankSyncRuntime(app) {}
        var ran = false
        var interrupted = false
        dev.whekin.whfin.data.backup.LedgerRestoreState.during {
            runtime.launch("TBC", dispatcher, { interrupted = true }) { ran = true }
            runCurrent()
            assertFalse(ran)
            assertTrue(interrupted)
        }
        runtime.close()
    }

    @Test fun androidServiceTimeoutCancelsTheActiveRun() = runTest(dispatcher) {
        val runtime = app.bankSync
        val job = runtime.launch("Credo", dispatcher, {}) { awaitCancellation() }
        runCurrent()
        val controller = org.robolectric.Robolectric.buildService(BankSyncService::class.java).create()
        runCurrent()
        assertTrue(runtime.hasActiveWork())
        controller.get().onTimeout(1, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(runtime.hasActiveWork())
        controller.destroy()
        runtime.close()
    }

    @Test fun nestedHistoryWorkKeepsOneServiceAndOneHomeHandoff() = runTest(dispatcher) {
        var starts = 0
        val runtime = BankSyncRuntime(app) { starts++ }
        val gate = CompletableDeferred<Unit>()
        runtime.launch("Credo", dispatcher, {}) {
            runtime.markDataStarted("Credo")
            runtime.launch("Credo", dispatcher, {}) { gate.await() }
        }
        runCurrent()
        assertTrue(runtime.hasActiveWork())
        assertEquals(1, starts)
        assertTrue(runtime.consumeHomeHandoff("Credo"))
        assertFalse(runtime.consumeHomeHandoff("Credo"))
        gate.complete(Unit)
        runCurrent()
        assertFalse(runtime.hasActiveWork())
        assertTrue(runtime.statuses.value.single().canReturnHome)
        assertTrue(app.getSharedPreferences("bank_sync_runtime", 0).getStringSet("in_flight", emptySet())!!.isEmpty())
        runtime.close()
    }

    @Test fun cancellationBeforeDispatchDoesNotLeaveAStuckRun() = runTest(dispatcher) {
        var ran = false
        var interrupted = false
        val runtime = BankSyncRuntime(app) {}
        runtime.launch("Credo", dispatcher, { interrupted = true }) { ran = true }
        runtime.cancel()
        runCurrent()
        assertFalse(ran)
        assertFalse(runtime.hasActiveWork())
        assertTrue(interrupted)
        runtime.close()
    }

    @Test fun serviceRejectionStopsWorkWithoutCallingBank() = runTest(dispatcher) {
        var ran = false
        var interrupted = false
        val runtime = BankSyncRuntime(app) { error("synthetic service rejection") }
        runtime.launch("TBC", dispatcher, { interrupted = true }) { ran = true }
        runCurrent()
        assertFalse(ran)
        assertFalse(runtime.hasActiveWork())
        assertTrue(interrupted)
        runtime.close()
    }

    @Test fun processRestartReportsInterruptionWithoutStartingAnything() = runTest(dispatcher) {
        app.getSharedPreferences("bank_sync_runtime", 0).edit().putStringSet("in_flight", setOf("Credo")).commit()
        var starts = 0
        val runtime = BankSyncRuntime(app) { starts++ }
        assertEquals(SyncPhase.INTERRUPTED, runtime.statuses.value.single().phase)
        runCurrent()
        assertFalse(runtime.hasActiveWork())
        assertEquals(0, starts)
        runtime.close()
    }
}
