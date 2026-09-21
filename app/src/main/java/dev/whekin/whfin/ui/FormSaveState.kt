package dev.whekin.whfin.ui

import androidx.compose.runtime.*
import dev.whekin.whfin.data.backup.LedgerBusyException
import dev.whekin.whfin.data.backup.LedgerRestoreState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FormSaveState(val busy: Boolean = false, val failed: Boolean = false, val completed: Long = 0)

/** Reserve the submit synchronously; closing a form is a consequence of a committed write. */
class FormSaver(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(FormSaveState())
    val state = mutable.asStateFlow()

    fun save(block: suspend () -> Unit) {
        val before = mutable.value
        if (before.busy || !mutable.compareAndSet(before, before.copy(busy = true, failed = false))) return
        val lease = try { LedgerRestoreState.beginRead() }
        catch (_: LedgerBusyException) {
            mutable.value = mutable.value.copy(busy = false, failed = true)
            return
        }
        scope.launch {
            try {
                block()
                mutable.value = mutable.value.copy(completed = mutable.value.completed + 1)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = mutable.value.copy(failed = true) }
        }.invokeOnCompletion {
            // Also runs if the scope was cancelled before the coroutine started.
            lease.close()
            mutable.value = mutable.value.copy(busy = false)
        }
    }
}

@Composable
fun OnFormSaved(state: FormSaveState, onSaved: () -> Unit) {
    var seen by remember { mutableLongStateOf(state.completed) }
    val latest by rememberUpdatedState(onSaved)
    LaunchedEffect(state.completed) {
        if (state.completed != seen) { seen = state.completed; latest() }
    }
}
