package dev.whekin.whfin.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.preferences.UiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CredoSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as WhfinApp
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = credoSmsBody(intent)
        // Login OTP is a process-only handoff and does not depend on the transaction monitoring
        // toggle. The Credo setup enables monitoring before requesting this same permission.
        app.credoOtpInbox.accept(body)
        val pending = goAsync()
        app.appScope.launch {
            try {
                if (!UiPreferences(app).smsImportEnabled.first()) return@launch
                if (!CredoSmsParser.isCredoCandidate(body)) return@launch
                val receivedAt = messages.minOfOrNull { it.timestampMillis }
                    ?: System.currentTimeMillis()
                SmsTransactionImporter(app.userDb).import(body, receivedAt)
            } finally {
                pending.finish()
            }
        }
    }
}
