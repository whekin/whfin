package dev.whekin.whfin.data.sms

import android.app.Application
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoOtpReceiverRegistrationTest {
    @Test
    fun `active OTP flow context-registers an exported telephony receiver`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        registerCredoOtpReceiver(app, CredoOtpInbox()).use {
            assertTrue(
                shadowOf(app).registeredReceivers.any { registered ->
                    registered.broadcastReceiver is CredoOtpForegroundReceiver &&
                        registered.intentFilter.hasAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                },
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `foreground receiver forwards the current MyCredo template to the active inbox`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val inbox = CredoOtpInbox()
        val received = async(UnconfinedTestDispatcher(testScheduler)) { inbox.codes.first() }
        val receiver = CredoOtpForegroundReceiver(inbox) {
            "# SMS Code: 4821 Please make sure to enter this authorization code at www.mycredo.ge " +
                "or in the Mycredo mobile application. #C#QEHAcSxJ"
        }

        receiver.onReceive(app, android.content.Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        assertEquals("4821", received.await())
    }
}
