package dev.whekin.whfin.data.sms

import org.junit.Assert.*
import org.junit.Test

class TbcLoginOtpTest {
    private val body = "Your TBC login code: 246810"
    @Test fun onlyLoginMessagesAreAcceptedWithoutConsent() {
        assertEquals("246810", TbcLoginOtp.extract(body))
        assertEquals("246810", TbcLoginOtp.extract("კოდი ავტორიზაციისთვის: 246810"))
        assertEquals("246810", TbcLoginOtp.extract("Код для входа: 246810"))
        for (other in listOf("Payment code: 246810", "TBC login payment code: 246810", "PIN code: 246810", "Your code: 246810", "TBC login code: 246810 or 123456")) {
            assertNull(other, TbcLoginOtp.extract(other))
        }
    }
    @Test fun approvedSingleMessageCanFillAGenericCodeButNotAPayment() {
        val inbox = TbcOtpInbox()
        assertFalse(inbox.acceptConsented("Your one-time code: 246810"))
        inbox.beginChallenge()
        assertFalse(inbox.acceptConsented("Payment confirmation code: 246810"))
        assertFalse(inbox.acceptConsented("ПИН-код: 0000"))
        assertFalse(inbox.acceptConsented("პინ კოდი: 0000"))
        assertFalse(inbox.acceptConsented("# SMS Code: 0000"))
        assertTrue(inbox.acceptConsented("Your one-time code: 246810"))
        assertEquals(listOf("246810"), inbox.codes.replayCache)
    }
    @Test fun activeBankWindowRejectsOldOtherBankAndDuplicateMessages() {
        val inbox = TbcOtpInbox(); val now = System.currentTimeMillis()
        assertFalse(inbox.accept(body, "TBCSMS", now))
        inbox.beginChallenge(now)
        assertFalse(inbox.accept(body, "Credo", now))
        assertFalse(inbox.accept(body, "TBCSMS", now - 2000))
        assertTrue(inbox.accept(body, "TBCSMS", now / 1000 * 1000))
        inbox.clearBufferedCode()
        assertFalse(inbox.accept(body, "TBCSMS", now))
        inbox.endChallenge()
        assertTrue(inbox.codes.replayCache.isEmpty())
        assertFalse(inbox.accept(body, "TBCSMS", now + 1))
    }
    @Test fun observedTbcWrappersUseTheLabelledCodeAndIgnoreAppHashDigits() {
        val english = "<#> TBC SMS code: 246810\nPlease, make sure you’re entering it on https://tbconline.ge or in TBC mobilebank\nAbcd123456E"
        val transliterated = "<#> TBC SMS Code: 246810\nDartsmundi, rom kodi shegyavs: https://tbconline.ge an TBC mobailbankshi\nAbcd123456E"
        for (message in listOf(english, transliterated)) {
            assertEquals("246810", TbcLoginOtp.extract(message))
            assertEquals("246810", TbcLoginOtp.fromConsentedMessage(message))
        }
    }

}
