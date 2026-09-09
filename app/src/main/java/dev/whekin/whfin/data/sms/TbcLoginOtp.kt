package dev.whekin.whfin.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.io.Closeable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TbcLoginOtp {
    private val labelled = Regex("(?imu)^\\s*(?:<#>\\s*)?TBC\\s+SMS\\s+code:\\s*([0-9]{4,8})(?![0-9])")
    private fun bankTemplate(body: String): String? {
        if (payment.containsMatchIn(body)) return null
        if (!body.contains("TBC mobilebank", true) && !body.contains("TBC mobailbank", true)) return null
        return labelled.findAll(body).toList().singleOrNull()?.groupValues?.get(1)
    }
    private val digits = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")
    private val login = Regex("(?iu)log[ -]?in|sign[ -]?in|authori[sz]|авторизац|вход|ავტორიზ|შესვლ")
    private val secret = Regex("(?iu)code|password|otp|код|парол|კოდ|პაროლ")
    private val payment = Regex("(?iu)payment|transfer|purchase|transaction|cvv|\\bpin\\b|пин|პინ|плат[её]ж|перевод|покупк|გადახდ|გადარიცხ")

    /** Unattended delivery needs a named login and a trusted sender; a payment code is never used. */
    fun extract(body: String): String? = bankTemplate(body)
        ?: if (login.containsMatchIn(body) && secret.containsMatchIn(body)) singleCode(body) else null

    /** The owner explicitly approved this exact message in Android's SMS consent dialog. */
    fun fromConsentedMessage(body: String): String? {
        if (CredoLoginOtp.extract(body) != null) return null
        return bankTemplate(body) ?: if (secret.containsMatchIn(body)) singleCode(body) else null
    }

    private fun singleCode(body: String): String? {
        if (payment.containsMatchIn(body)) return null
        return digits.findAll(body).map { it.value }.toList().singleOrNull()
    }
}

/** One foreground challenge; neither SMS bodies nor codes enter preferences, Room or backup. */
class TbcOtpInbox {
    private val events = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val codes = events.asSharedFlow()
    var challengeSince: Long = 0
        private set
    private var accepted: String? = null
    val hasCode: Boolean get() = accepted != null
    fun beginChallenge(now: Long = System.currentTimeMillis()) {
        clearBufferedCode(); accepted = null; challengeSince = now
    }
    fun endChallenge() { challengeSince = 0; accepted = null; clearBufferedCode() }
    fun accept(body: String, sender: String?, receivedAt: Long = System.currentTimeMillis()): Boolean {
        if (BankSmsBank.fromSender(sender) != BankSmsBank.TBC) return false
        return deliver(TbcLoginOtp.extract(body), receivedAt)
    }
    fun acceptConsented(body: String): Boolean = deliver(TbcLoginOtp.fromConsentedMessage(body), System.currentTimeMillis())
    private fun deliver(code: String?, receivedAt: Long): Boolean {
        // SMSC timestamps have one-second precision; do not drop a fast OTP in the same second.
        if (challengeSince == 0L || receivedAt / 1000 < challengeSince / 1000 || System.currentTimeMillis() - challengeSince > 5 * 60_000L ||
            code == null || code == accepted) return false
        accepted = code
        return events.tryEmit(code)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearBufferedCode() = events.resetReplayCache()
}

internal fun registerTbcOtpReceiver(context: Context, inbox: TbcOtpInbox): Closeable {
    val app = context.applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || inbox.challengeSince == 0L) return
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val sender = messages.map { it.originatingAddress }.distinct().singleOrNull()
            inbox.accept(messages.joinToString("") { it.messageBody.orEmpty() }, sender,
                messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis())
        }
    }
    ContextCompat.registerReceiver(app, receiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), ContextCompat.RECEIVER_EXPORTED)
    return Closeable { runCatching { app.unregisterReceiver(receiver) } }
}
