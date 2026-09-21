package dev.whekin.whfin.data.sync

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.ui.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SyncPhase { AUTHORIZING, CONFIRMATION, READING, MATCHING, HISTORY, COMPLETE, ATTENTION, INTERRUPTED }
data class BankSyncStatus(val bank: String, val runId: Long, val active: Boolean, val phase: SyncPhase,
    val current: Int = 0, val total: Int = 0, val canReturnHome: Boolean = false)
internal data class BankSyncRun(val id: Long, val count: Int, val interrupted: Boolean = false, val dataStarted: Boolean = false)

/** Process-owned bank work. No Activity, OTP, password or session is serialized here. */
class BankSyncRuntime(private val app: WhfinApp, private val startForeground: () -> Unit = { BankSyncService.start(app) }) {
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private val provider by lazy { ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app)) }
    val credo: CredoSyncViewModel get() = provider[CredoSyncViewModel::class.java]
    val tbc: TbcLoginViewModel get() = provider[TbcLoginViewModel::class.java]
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("bank_sync_runtime", 0)
    private var nextId = 0L
    private val runs = MutableStateFlow(prefs.getStringSet("in_flight", emptySet()).orEmpty()
        .associateWith { BankSyncRun(++nextId, 0, interrupted = true) })
    private val jobs = mutableMapOf<Job, String>()
    private val handoffs = mutableSetOf<Long>()
    val statuses: StateFlow<List<BankSyncStatus>> by lazy {
        combine(runs, credo.state, tbc.state) { running, c, t ->
            running.map { (bank, run) ->
                val phase = when {
                    run.interrupted -> SyncPhase.INTERRUPTED
                    bank == "Credo" -> when {
                        c.stage == CredoSyncStage.AwaitingOtp && !c.isBusy -> SyncPhase.CONFIRMATION
                        c.stage == CredoSyncStage.Syncing -> when (c.extraPhase) {
                            CredoSyncExtraPhase.CHECKING_HISTORY -> SyncPhase.HISTORY
                            CredoSyncExtraPhase.LINKING_CARDS -> SyncPhase.MATCHING
                            null -> if (c.currentPhase == dev.whekin.whfin.data.importer.StatementImporter.Phase.RECONCILING) SyncPhase.MATCHING else SyncPhase.READING
                        }
                        run.count > 0 -> SyncPhase.AUTHORIZING
                        c.errorCode != null || c.results.any { it.errorCode != null || it.detail != null || it.balanceNeedsReview } -> SyncPhase.ATTENTION
                        c.stage == CredoSyncStage.Disconnected -> SyncPhase.CONFIRMATION
                        else -> SyncPhase.COMPLETE
                    }
                    t.stage == TbcLoginStage.Code -> SyncPhase.CONFIRMATION
                    run.count > 0 -> if (t.sessionVerified && t.syncProgress != null) SyncPhase.READING else SyncPhase.AUTHORIZING
                    t.error != null || t.syncResult?.let { it.errors.isNotEmpty() || it.needsStatement.isNotEmpty() } == true -> SyncPhase.ATTENTION
                    t.stage == TbcLoginStage.Login -> SyncPhase.CONFIRMATION
                    else -> SyncPhase.COMPLETE
                }
                BankSyncStatus(bank, run.id, run.count > 0, phase,
                    if (bank == "Credo") c.currentAccount else t.syncProgress?.first ?: 0,
                    if (bank == "Credo") c.currentAccountTotal else t.syncProgress?.second ?: 0, run.dataStarted)
            }
        }.stateIn(scope, SharingStarted.Eagerly, runs.value.map { (bank, run) ->
            BankSyncStatus(bank, run.id, run.count > 0,
                if (run.interrupted) SyncPhase.INTERRUPTED else if (run.count > 0) SyncPhase.AUTHORIZING else SyncPhase.COMPLETE,
                canReturnHome = run.dataStarted)
        })
    }

    @Synchronized fun consumeHomeHandoff(bank: String): Boolean = runs.value[bank]?.let { handoffs.add(it.id) } ?: false
    @Synchronized fun markDataStarted(bank: String) {
        runs.value[bank]?.let { runs.value = runs.value + (bank to it.copy(dataStarted = true)) }
    }
    fun hasActiveWork(): Boolean = runs.value.values.any { it.count > 0 }
    @Synchronized fun dismissIdle(bank: String) {
        if (runs.value[bank]?.count == 0) runs.value = runs.value - bank
    }

    fun launch(bank: String, dispatcher: CoroutineDispatcher, interrupted: () -> Unit,
        block: suspend CoroutineScope.() -> Unit): Job {
        val lease = try { dev.whekin.whfin.data.backup.LedgerRestoreState.beginRead() }
        catch (_: dev.whekin.whfin.data.backup.LedgerBusyException) {
            return scope.launch { interrupted() }
        }
        val first: Boolean
        synchronized(this) {
            first = runs.value.values.none { it.count > 0 }
            val previous = runs.value[bank]
            val run = if (previous == null || previous.count == 0) BankSyncRun(++nextId, 1)
                else previous.copy(count = previous.count + 1)
            runs.value = runs.value + (bank to run)
            saveInFlight()
        }
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            try {
                if (first) withContext(Dispatchers.Main.immediate) { startForeground() }
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                markInterrupted(bank)
                interrupted()
            }
        }
        synchronized(this) { jobs[job] = bank }
        job.invokeOnCompletion { cause ->
            try {
            if (cause is CancellationException) { markInterrupted(bank); interrupted() }
            synchronized(this) {
                runs.value[bank]?.let { run -> runs.value = runs.value + (bank to run.copy(count = (run.count - 1).coerceAtLeast(0))) }
                jobs.remove(job)
                saveInFlight()
            }
            } finally { lease.close() }
        }
        job.start()
        return job
    }

    @Synchronized private fun markInterrupted(bank: String) {
        runs.value[bank]?.let { runs.value = runs.value + (bank to it.copy(interrupted = true)) }
    }
    @Synchronized fun cancel(bank: String? = null) {
        jobs.filterValues { bank == null || it == bank }.keys.toList().forEach { it.cancel() }
    }
    private fun saveInFlight() {
        prefs.edit().putStringSet("in_flight", runs.value.filterValues { it.count > 0 }.keys.toSet()).apply()
    }
    /** A restored ledger invalidates account choices and retained initial-balance reads. */
    fun resetAfterRestore() {
        check(!hasActiveWork())
        credo.resetForRestoredLedger()
        tbc.leave()
        runs.value = emptyMap()
        handoffs.clear()
        saveInFlight()
    }

    internal fun close() { cancel(); scope.cancel(); owner.viewModelStore.clear() }
}
