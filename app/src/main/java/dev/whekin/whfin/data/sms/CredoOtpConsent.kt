package dev.whekin.whfin.data.sms

import android.content.Context
import android.content.Intent

/** Compatibility name for the shared, permission-free SMS consent transport. */
object CredoOtpConsent {
    const val WINDOW_MILLIS = SmsOtpConsent.WINDOW_MILLIS
    fun startListening(context: Context) = SmsOtpConsent.startListening(context)
    fun register(context: Context, onConsentRequested: (Intent) -> Unit) = SmsOtpConsent.register(context, onConsentRequested)
    internal fun consentIntentFrom(intent: Intent): Intent? = SmsOtpConsent.consentIntentFrom(intent)
}
