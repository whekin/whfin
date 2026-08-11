package dev.whekin.whfin.data.sms

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `SMS received receivers do not reject OEM system brokers with a sender permission filter`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        registerCredoOtpReceiver(app, CredoOtpInbox()).use {
            val foreground = shadowOf(app).registeredReceivers.first {
                it.broadcastReceiver is CredoOtpForegroundReceiver
            }
            assertNull(foreground.broadcastPermission)
        }

        val manifest = app.packageManager.getReceiverInfo(
            ComponentName(app, CredoSmsReceiver::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertNull(manifest.permission)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `foreground receiver forwards the current MyCredo template to the active inbox`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val inbox = CredoOtpInbox()
        val received = async(UnconfinedTestDispatcher(testScheduler)) { inbox.codes.first() }
        val receiver = CredoOtpForegroundReceiver(inbox) {
            "# SMS Code: 0519 Please make sure to enter this authorization code at www.mycredo.ge " +
                "or in the Mycredo mobile application. 9C0QEHAcSxJ"
        }

        receiver.onReceive(app, android.content.Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        assertEquals("0519", received.await())
    }
}
