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
        val pending = goAsync()
        val app = context.applicationContext as WhfinApp
        app.appScope.launch {
            try {
                if (!UiPreferences(app).smsImportEnabled.first()) return@launch
                val body = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    .joinToString("") { it.messageBody.orEmpty() }
                if (CredoSmsParser.parse(body) == null) return@launch
                SmsTransactionImporter(app.db).import(body)
            } finally {
                pending.finish()
            }
        }
    }
}
