package dev.whekin.whfin.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a restore is currently replacing the contents of a database.
 *
 * A restore empties every table before it writes the new rows, and the screens are alive while that
 * happens: a demo workspace being installed, a backup being brought back. Those screens ask Room what
 * is there, Room truthfully answers "nothing", and Home would state a month result of zero and offer
 * to help the person get started — over their own data, mid-restore.
 *
 * Process-wide on purpose. "The database is being replaced" is a fact about the process, not about one
 * screen or one manager instance, and [WhfinBackupManager] is created ad hoc wherever a restore is
 * needed, so a flag owned by an instance would tell nobody.
 */
object LedgerRestoreState {

    private val _active = MutableStateFlow(false)

    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal suspend fun <T> during(block: suspend () -> T): T {
        _active.value = true
        return try {
            block()
        } finally {
            _active.value = false
        }
    }
}
