package dev.whekin.whfin.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LedgerBusyException : IllegalStateException("Finish the current import or refresh before restoring the ledger.")

/** Coordinates long-running reads that will write back with whole-ledger replacement. */
object LedgerRestoreState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private var readers = 0

    /** Reserve before launching asynchronous work, not after its coroutine happens to start. */
    @Synchronized
    fun beginRead(): AutoCloseable {
        if (_active.value) throw LedgerBusyException()
        readers++
        var closed = false
        return AutoCloseable {
            synchronized(this) {
                if (!closed) { closed = true; readers-- }
            }
        }
    }

    suspend fun <T> reading(block: suspend () -> T): T {
        val lease = beginRead()
        return try { block() } finally { lease.close() }
    }

    internal suspend fun <T> during(block: suspend () -> T): T {
        synchronized(this) {
            if (_active.value || readers != 0) throw LedgerBusyException()
            _active.value = true
        }
        return try { block() } finally {
            synchronized(this) { _active.value = false }
        }
    }
}
