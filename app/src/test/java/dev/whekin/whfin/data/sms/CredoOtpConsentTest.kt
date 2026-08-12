package dev.whekin.whfin.data.sms

import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Play Services sends back is a dialog, not a code. Anything else it sends — a timeout, an
 * unexpected shape — must produce nothing at all: the user is mid-login, and a dialog raised for
 * the wrong reason is worse than no help.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoOtpConsentTest {

    private fun broadcast(status: Status?, consent: Intent?) = Intent(SmsRetriever.SMS_RETRIEVED_ACTION)
        .apply {
            status?.let { putExtra(SmsRetriever.EXTRA_STATUS, it) }
            consent?.let { putExtra(SmsRetriever.EXTRA_CONSENT_INTENT, it) }
        }

    @Test
    fun `a matched message hands over the dialog to launch`() {
        val consent = Intent("show-the-user-the-message")

        val result = CredoOtpConsent.consentIntentFrom(
            broadcast(Status(CommonStatusCodes.SUCCESS), consent),
        )

        assertEquals(consent.action, result?.action)
    }

    @Test
    fun `a timeout is not a code`() {
        assertNull(
            CredoOtpConsent.consentIntentFrom(
                broadcast(Status(CommonStatusCodes.TIMEOUT), Intent("unused")),
            ),
        )
    }

    @Test
    fun `a broadcast without a status or a dialog is ignored`() {
        assertNull(CredoOtpConsent.consentIntentFrom(broadcast(null, Intent("unused"))))
        assertNull(CredoOtpConsent.consentIntentFrom(broadcast(Status(CommonStatusCodes.SUCCESS), null)))
        assertNull(CredoOtpConsent.consentIntentFrom(Intent("some.other.action")))
    }
}
