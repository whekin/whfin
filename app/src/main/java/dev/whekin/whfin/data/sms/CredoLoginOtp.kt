package dev.whekin.whfin.data.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.io.Closeable
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
    private val _codes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val codes: SharedFlow<String> = _codes.asSharedFlow()

    fun accept(body: String): Boolean {
        val code = CredoLoginOtp.extract(body) ?: return false
        return _codes.tryEmit(code)
    }
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
        Manifest.permission.BROADCAST_SMS,
        null,
        ContextCompat.RECEIVER_EXPORTED,
    )
    return Closeable { runCatching { appContext.unregisterReceiver(receiver) } }
}

internal fun credoSmsBody(intent: Intent): String =
    Telephony.Sms.Intents.getMessagesFromIntent(intent)
        .joinToString("") { it.messageBody.orEmpty() }
