package dev.whekin.whfin.data.security

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Actions that do more than show money: they take the whole ledger off the device, replace it, or
 * hand over the bank login that produced it.
 *
 * The whole-app lock cannot stand in for this. By the time any of these is reachable the lock has
 * already passed, and with a long or disabled timeout an unlocked session stays open for as long as
 * the phone stays awake — including in someone else's hands.
 */
enum class SensitiveAction {
    /** A backup file is the entire ledger in one document, and the plain variant is unencrypted. */
    BackupExport,

    /** Restore replaces every table; the safety copy softens the mistake but does not prevent it. */
    BackupRestore,

    /** The saved MyCredo login is a bank credential, not a WHFIN one. */
    BankCredential,

    /** Changing the code or the timeout is how the other three stop being protected. */
    AppLockSettings,
}

interface SensitiveActions {
    /**
     * False when no code exists. There is then nothing to verify against, so the gate claims
     * nothing rather than pretending with the system credential.
     */
    val isProtected: Boolean

    fun require(action: SensitiveAction, onGranted: () -> Unit)

    object Unprotected : SensitiveActions {
        override val isProtected: Boolean = false
        override fun require(action: SensitiveAction, onGranted: () -> Unit) = onGranted()
    }
}

val LocalSensitiveActions = staticCompositionLocalOf<SensitiveActions> { SensitiveActions.Unprotected }

/**
 * A short grace so one flow does not ask twice — authenticate, then open a passphrase sheet or a
 * document picker — while staying too short for a later visit to inherit it.
 */
internal class SensitiveActionSession(
    private val elapsedRealtime: () -> Long,
    private val graceMillis: Long = GRACE_MILLIS,
) {
    private var grantedAt: Long? = null

    fun grant() {
        grantedAt = elapsedRealtime()
    }

    fun isGranted(): Boolean {
        val at = grantedAt ?: return false
        val age = elapsedRealtime() - at
        if (age < 0L || age >= graceMillis) {
            grantedAt = null
            return false
        }
        return true
    }

    /** Leaving the app ends it: the next hand holding the phone is not the one that authenticated. */
    fun clear() {
        grantedAt = null
    }

    companion object {
        const val GRACE_MILLIS = 60_000L
    }
}

/**
 * Holds the one request in flight. Cancelling is an ordinary outcome — the action does not happen,
 * and nothing is reported as an error.
 */
class SensitiveActionController(
    private val hasPin: () -> Boolean,
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : SensitiveActions {
    private val session = SensitiveActionSession(elapsedRealtime)
    private var granted: (() -> Unit)? = null

    var pending by mutableStateOf<SensitiveAction?>(null)
        private set
    var problem by mutableStateOf<AppLockProblem?>(null)
        private set

    override val isProtected: Boolean get() = hasPin()

    override fun require(action: SensitiveAction, onGranted: () -> Unit) {
        if (!isProtected || session.isGranted()) {
            onGranted()
            return
        }
        granted = onGranted
        problem = null
        pending = action
    }

    fun allow() {
        val callback = granted
        granted = null
        pending = null
        problem = null
        session.grant()
        callback?.invoke()
    }

    fun cancel() {
        granted = null
        pending = null
        problem = null
    }

    fun report(problem: AppLockProblem) {
        this.problem = problem
    }

    fun endGrace() = session.clear()
}
