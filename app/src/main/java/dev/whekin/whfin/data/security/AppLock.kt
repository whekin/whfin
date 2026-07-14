package dev.whekin.whfin.data.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dev.whekin.whfin.data.preferences.AppLockTimeout

const val WHFIN_BIOMETRIC_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

enum class BiometricAvailability {
    Available,
    EnrollmentRequired,
    TemporarilyUnavailable,
    Unsupported,
}

enum class AppLockProblem {
    Cancelled,
    LockedOut,
    Unavailable,
}

fun biometricAvailability(context: Context): BiometricAvailability =
    when (BiometricManager.from(context).canAuthenticate(WHFIN_BIOMETRIC_AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.EnrollmentRequired
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.TemporarilyUnavailable
        else -> BiometricAvailability.Unsupported
    }

class WhfinAuthenticator(activity: FragmentActivity) {
    private var success: (() -> Unit)? = null
    private var problem: ((AppLockProblem) -> Unit)? = null

    val isPromptVisible: Boolean get() = success != null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val callback = success
                clearCallbacks()
                callback?.invoke()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val callback = problem
                clearCallbacks()
                callback?.invoke(errorCode.toAppLockProblem())
            }
        },
    )

    fun authenticate(
        title: String,
        subtitle: String,
        useCodeLabel: String,
        onSuccess: () -> Unit,
        onProblem: (AppLockProblem) -> Unit,
    ) {
        if (isPromptVisible) return
        success = onSuccess
        problem = onProblem
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(WHFIN_BIOMETRIC_AUTHENTICATORS)
                .setNegativeButtonText(useCodeLabel)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    fun cancel() {
        prompt.cancelAuthentication()
        clearCallbacks()
    }

    private fun clearCallbacks() {
        success = null
        problem = null
    }
}

private fun Int.toAppLockProblem(): AppLockProblem = when (this) {
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> AppLockProblem.LockedOut
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
    -> AppLockProblem.Unavailable
    else -> AppLockProblem.Cancelled
}

internal class AppLockSession(private val elapsedRealtime: () -> Long) {
    var timeout: AppLockTimeout = AppLockTimeout.Disabled
        private set
    var locked: Boolean = true
        private set
    private var configured = false
    private var backgroundedAt: Long? = null

    fun configure(value: AppLockTimeout, hasPin: Boolean = true) {
        timeout = if (hasPin) value else AppLockTimeout.Disabled
        if (!configured) {
            locked = timeout.enabled
            configured = true
        } else if (!timeout.enabled) {
            locked = false
            backgroundedAt = null
        }
    }

    fun background() {
        if (timeout.enabled) backgroundedAt = elapsedRealtime()
    }

    fun foreground() {
        val leftAt = backgroundedAt ?: return
        val limit = timeout.timeoutMillis ?: return
        if ((elapsedRealtime() - leftAt).coerceAtLeast(0L) >= limit) locked = true
    }

    fun unlock() {
        locked = false
        backgroundedAt = null
    }
}

class AppLockViewModel : ViewModel() {
    private val session = AppLockSession(SystemClock::elapsedRealtime)

    var locked by mutableStateOf(true)
        private set
    var problem by mutableStateOf<AppLockProblem?>(null)
        private set
    val timeout: AppLockTimeout get() = session.timeout

    fun configure(timeout: AppLockTimeout, hasPin: Boolean = true) {
        session.configure(timeout, hasPin)
        sync()
    }

    fun background() = session.background()

    fun foreground() {
        session.foreground()
        sync()
    }

    fun unlock() {
        session.unlock()
        problem = null
        sync()
    }

    fun report(problem: AppLockProblem) {
        this.problem = problem
    }

    private fun sync() {
        locked = session.locked
    }
}
