package dev.whekin.whfin.data.sms

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredoLoginOtpTest {
    @Test
    fun `MyCredo four digit login code is extracted without accepting payment codes`() {
        assertEquals(
            "4821",
            CredoLoginOtp.extract("# SMS Code: 4821 Please make sure to enter it only in the app"),
        )
        assertNull(CredoLoginOtp.extract("CODE: 123456 confirms card ***0002 payment"))
        assertNull(CredoLoginOtp.extract("Transaction completed, OTP: 4821 Name: Example"))
        assertNull(CredoLoginOtp.extract("Your delivery code is 4821"))
    }

    @Test
    fun `active Credo screen receives the code without replay storage`() = runTest {
        val inbox = CredoOtpInbox()
        val received = async(UnconfinedTestDispatcher(testScheduler)) { inbox.codes.first() }

        assertTrue(inbox.accept("# SMS Code: 4821 Please make sure to enter it only in the app"))

        assertEquals("4821", received.await())
    }
}
