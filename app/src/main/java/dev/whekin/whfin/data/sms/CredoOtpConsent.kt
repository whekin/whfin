package dev.whekin.whfin.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.io.Closeable

/**
 * The login code obtained the way a bank app does it: by asking the user, not the system.
 *
 * `RECEIVE_SMS` turned out to be the weaker path — it is granted and One UI still may not deliver
 * the broadcast — and it is also a restricted permission that a store release has to justify. This
 * asks Play Services to watch for one code instead: no permission at all, a single system dialog
 * naming the message, and the text handed over only if the user agrees.
 *
 * It is a fallback-friendly extra, never a requirement. Where Play Services is missing or the user
 * declines, the broadcast and the inbox read still stand, and nothing here is stored.
 */
object CredoOtpConsent {

    /** The window Play Services keeps watching for; a login OTP arrives long before it closes. */
    const val WINDOW_MILLIS = 5L * 60 * 1000

    /**
     * Asks for one upcoming code from any sender.
     *
     * The sender is deliberately not pinned: Credo's SMS arrives from an alphanumeric id that the
     * API cannot filter on, and the user sees the exact message in the dialog either way.
     */
    fun startListening(context: Context) {
        runCatching { SmsRetriever.getClient(context.applicationContext).startSmsUserConsent(null) }
    }

    /**
     * Registers the receiver Play Services talks back through.
     *
     * @param onConsentRequested handed the Intent that shows the system dialog. The caller launches
     *   it for a result and reads [SmsRetriever.EXTRA_SMS_MESSAGE] from what comes back.
     */
    fun register(context: Context, onConsentRequested: (Intent) -> Unit): Closeable {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context, intent: Intent) {
                consentIntentFrom(intent)?.let(onConsentRequested)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            // Only Play Services may send this; without the sender check any app could offer us a
            // dialog of its own making.
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        return Closeable { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    /** The dialog Intent of a successful match, or null for a timeout or anything unexpected. */
    internal fun consentIntentFrom(intent: Intent): Intent? {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return null
        val status = IntentCompat.getParcelableExtra(intent, SmsRetriever.EXTRA_STATUS, Status::class.java)
        if (status?.statusCode != CommonStatusCodes.SUCCESS) return null
        return IntentCompat.getParcelableExtra(intent, SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
    }
}
