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
        inbox.beginChallenge()
        val received = async(UnconfinedTestDispatcher(testScheduler)) { inbox.codes.first() }

        assertTrue(inbox.accept("# SMS Code: 4821 Please make sure to enter it only in the app"))

        assertEquals("4821", received.await())
    }

    @Test
    fun `the challenge window names the moment the inbox may be read from`() {
        val inbox = CredoOtpInbox()

        assertEquals(0L, inbox.challengeSince)

        inbox.beginChallenge(now = 1_000)
        assertEquals(1_000L, inbox.challengeSince)

        // A recreated route keeps looking at the same window rather than skipping a code that
        // arrived while it was gone.
        inbox.ensureChallenge(now = 5_000)
        assertEquals(1_000L, inbox.challengeSince)

        // A resend moves the window forward: the previous attempt's code is still in the inbox.
        inbox.beginChallenge(now = 9_000)
        assertEquals(9_000L, inbox.challengeSince)

        inbox.endChallenge()
        assertEquals(0L, inbox.challengeSince)
    }

    @Test
    fun `code survives the receiver to collector handoff`() {
        val inbox = CredoOtpInbox()
        inbox.beginChallenge()

        assertTrue(
            inbox.accept(
                "# SMS Code: 0519 Please make sure to enter this authorization code at " +
                    "www.mycredo.ge or in the Mycredo mobile application. 9C0QEHAcSxJ",
            ),
        )

        assertEquals(listOf("0519"), inbox.codes.replayCache)
    }
}
