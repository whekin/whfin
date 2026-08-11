package dev.whekin.whfin.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.io.Closeable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The four-digit MyCredo login template; payment confirmation codes are deliberately excluded. */
object CredoLoginOtp {
    private val loginCode = Regex("""(?m)^# SMS Code:\s*(\d{4})(?!\d)""")

    fun extract(body: String): String? = loginCode.find(body)?.groupValues?.get(1)
}

/**
 * Process-only handoff from the SMS receiver to an active Credo login screen.
 *
 * There is no replay: a code received before the user starts a challenge must never fill a later
 * challenge. Neither the body nor the extracted code is persisted.
 */
class CredoOtpInbox {
    private val _codes = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val codes: SharedFlow<String> = _codes.asSharedFlow()
    @Volatile private var challengeActive = false

    /** Opens a fresh, foreground-only handoff window and rejects any code from an older login. */
    fun beginChallenge() {
        clearBufferedCode()
        challengeActive = true
    }

    /** Restores delivery after a configuration-driven route recreation without discarding a code. */
    fun ensureChallenge() {
        challengeActive = true
    }

    fun endChallenge() {
        challengeActive = false
        clearBufferedCode()
    }

    fun accept(body: String): Boolean {
        if (!challengeActive) return false
        val code = CredoLoginOtp.extract(body) ?: return false
        return _codes.tryEmit(code)
    }

    /** The collector owns the code now; a recreated screen must not refill a rejected/used OTP. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearBufferedCode() = _codes.resetReplayCache()
}

/**
 * Foreground fallback for OEMs that omit a sideloaded app from the manifest SMS broadcast.
 * It exists only while a MyCredo login is connecting or waiting for its OTP.
 */
internal class CredoOtpForegroundReceiver(
    private val inbox: CredoOtpInbox,
    private val bodyFrom: (Intent) -> String = ::credoSmsBody,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        inbox.accept(bodyFrom(intent))
    }
}

internal fun registerCredoOtpReceiver(context: Context, inbox: CredoOtpInbox): Closeable {
    val appContext = context.applicationContext
    val receiver = CredoOtpForegroundReceiver(inbox)
    ContextCompat.registerReceiver(
        appContext,
        receiver,
        IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
        ContextCompat.RECEIVER_EXPORTED,
    )
    return Closeable { runCatching { appContext.unregisterReceiver(receiver) } }
}

internal fun credoSmsBody(intent: Intent): String =
    Telephony.Sms.Intents.getMessagesFromIntent(intent)
        .joinToString("") { it.messageBody.orEmpty() }
