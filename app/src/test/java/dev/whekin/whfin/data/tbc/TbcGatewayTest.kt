package dev.whekin.whfin.data.tbc

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcGatewayTest {
    private class Script(private vararg val responses: TbcResponse) : TbcTransport {
        val calls = mutableListOf<Triple<String, Map<String, String>, String?>>()
        override fun exchange(url: String, method: String, headers: Map<String, String>, body: String?): TbcResponse {
            calls += Triple(url, headers, body)
            return responses[calls.lastIndex]
        }
    }
    private val challenge = """{"secondPhaseRequired":true,"transactionId":"example-challenge","signatures":[{"type":"SMS_OTP","otpId":"example-otp"}]}"""
    private val authenticated = TbcResponse(200, """{"success":true}""", listOf("session=example; Secure; HttpOnly"))
    private val user = TbcResponse(200, """{"sessionId":"example-session"}""")
    @Test fun passwordOtpAndSessionVerificationKeepCookiesAndNeverRegisterOrTrustDevice() = runBlocking {
        val script = Script(TbcResponse(200, challenge), authenticated, user)
        val gateway = MobileTbcGateway(script)
        val pending = gateway.login("example-user", "example-secret") as TbcLoginResult.Challenge
        val session = gateway.confirm(pending.value, "0000")
        assertEquals("example", session.cookies["session"])
        assertEquals(listOf("loginWithPassword", "certifyLogin", "userinfo"), script.calls.map { it.first.substringAfterLast('/') })
        assertEquals("session=example", script.calls.last().second["Cookie"])
        val sent = JSONObject(requireNotNull(script.calls[1].third))
        assertEquals("example-challenge", sent.getString("transactionId"))
        assertEquals("example-otp", sent.getJSONObject("signature").getString("otpId"))
        assertEquals("SMS_OTP", sent.getJSONObject("signature").getString("type"))
        assertFalse(session.toString().contains("example"))
    }
    @Test fun resumeUsesSessionWithoutPasswordAndGetsAccountListWithoutLedgerWrites() = runBlocking {
        val accounts = TbcResponse(200, """{"accountsAndDebitCards":[{"id":7,"iban":"GE00TB0000000000000001","currency":"GEL","name":"Everyday"}]}""", listOf("session=renewed; Secure"))
        val script = Script(user, accounts)
        val gateway = MobileTbcGateway(script)
        gateway.resume(TbcSession(mapOf("session" to "example"), "example-device"))
        assertEquals("GEL", gateway.accounts().single().currency)
        assertTrue(script.calls.all { it.third == null })
        assertEquals("renewed", gateway.snapshot().cookies["session"])
    }
    @Test fun failedOtpIsNotAcceptedAndDoesNotRetry() = runBlocking {
        val script = Script(TbcResponse(200, challenge), TbcResponse(200, """{"success":false}"""))
        val gateway = MobileTbcGateway(script)
        val pending = gateway.login("example-user", "example-secret") as TbcLoginResult.Challenge
        try { gateway.confirm(pending.value, "0000"); fail() } catch (e: TbcException) { assertEquals("OTP", e.code) }
        assertEquals(2, script.calls.size)
    }
    @Test fun unsupportedChallengeDoesNotSendAnInventedOtpMethod() = runBlocking {
        val script = Script(TbcResponse(200, challenge.replace("SMS_OTP", "UNKNOWN")))
        try { MobileTbcGateway(script).login("example-user", "example-secret"); fail() }
        catch (e: TbcException) { assertEquals("OTP_METHOD", e.code) }
        assertEquals(1, script.calls.size)
    }
    @Test fun passwordChangeOrProfileSelectionRequiresOfficialBankAction() = runBlocking {
        for (field in listOf("changePasswordRequired", "userSelectionRequired")) {
            val script = Script(TbcResponse(200, """{"success":true,"$field":true}"""))
            try { MobileTbcGateway(script).login("example-user", "example-secret"); fail() }
            catch (e: TbcException) { assertEquals("BANK_ACTION", e.code) }
        }
    }
    @Test fun rejectedSessionAndProtectionNeverLeakBankBody() = runBlocking {
        for ((status, code) in listOf(401 to "SESSION", 403 to "PROTECTION", 429 to "RATE_LIMIT", 302 to "HTTP_302")) {
            val script = Script(TbcResponse(status, "private body must not escape"))
            try { MobileTbcGateway(script).resume(TbcSession(mapOf("session" to "example"), "device")); fail() }
            catch (e: TbcException) { assertEquals(code, e.message) }
            assertEquals(1, script.calls.size)
        }
    }
    @Test fun transportRejectsUnknownHostsAndPlainHttpBeforeConnecting() {
        for (url in listOf("http://rmbgw.tbconline.ge/path", "https://example.com/path", "https://rmbgw.tbconline.ge:444/path")) {
            assertThrows(TbcException::class.java) { HttpsTbcTransport().exchange(url, "GET", emptyMap(), null) }
        }
    }
}
