package dev.whekin.whfin.data.credo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MyCredoGatewayTest {
    @Test
    fun otpLogin_discoversLedgers_andDownloadsStatementReadOnly() = runBlocking {
        val xlsx = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val transport = RecordingTransport(
            """{"data":{"operationId":"op-1","requires2FA":true,"operationData":{"mobile":"***42"}}}""",
            """{"data":{"operationSendChallenge":true}}""",
            """{"data":{"operationData":{"token":"access-token","refreshToken":"refresh-token"}}}""",
            """{"data":{"accounts":[
                {"accountId":1,"accountNumber":"GE0012345678","currency":"gel","category":"Current"},
                {"accountId":2,"accountNumber":"GE0012345678","currency":"USD","category":"Current"},
                {"accountId":3,"accountNumber":"GE0012345678","currency":"GEL","category":"Duplicate"}
            ]}}""",
            """{"data":{"exportToExcel":"data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64,UEsDBA=="}}""",
        )
        val gateway = MyCredoGateway(
            context = ApplicationProvider.getApplicationContext<Context>(),
            transport = transport,
            deviceHistoryId = "device-id",
        )

        val challenge = gateway.initiateLogin(CredoCredentials("User", "secret"))
        assertTrue(challenge.requiresOtp)
        assertEquals("***42", challenge.mobileHint)
        gateway.sendOtp(challenge.operationId)
        val session = gateway.confirmLogin(challenge, "User", "1234")
        val accounts = gateway.accounts(session)
        val downloaded = gateway.downloadStatement(
            session,
            accounts.first(),
            "2025-07-14T00:00:00Z",
            "2026-07-14T23:59:59Z",
        )

        assertEquals(2, accounts.size)
        assertEquals("GEL", accounts.first().currency)
        assertArrayEquals(xlsx, downloaded)
        assertFalse(transport.calls.first().body.contains("access-token"))
        val loginRequest = JSONObject(transport.calls.first().body)
        assertEquals("ENGLISH", loginRequest.getString("deviceLanguage"))
        assertEquals("mobile", loginRequest.getString("deviceType"))
        assertEquals("Android", loginRequest.getString("deviceModel"))
        assertTrue(loginRequest.getString("deviceScreenSize").matches(Regex("\\d+X\\d+")))
        assertEquals("Bearer access-token", transport.calls[3].headers["Authorization"])
        assertEquals("Bearer access-token", transport.calls[4].headers["Authorization"])
        val exportVariables = JSONObject(transport.calls.last().body)
            .getJSONObject("variables")
            .getJSONObject("exportToExcel")
        assertEquals("GE0012345678", exportVariables.getString("accountNumber"))
        assertEquals("GEL", exportVariables.getString("currency"))
        assertEquals(2, exportVariables.getInt("language"))
    }

    @Test
    fun graphqlError_isReducedToStableCode() = runBlocking {
        val transport = RecordingTransport(
            """{"errors":[{"message":"details not exposed","extensions":{"code":"UNAUTHORIZED"}}]}""",
        )
        val gateway = MyCredoGateway(
            context = ApplicationProvider.getApplicationContext<Context>(),
            transport = transport,
            deviceHistoryId = "device-id",
        )

        val error = runCatching { gateway.accounts(CredoSession("expired", null)) }.exceptionOrNull()

        assertTrue(error is CredoApiException)
        assertEquals("UNAUTHORIZED", (error as CredoApiException).code)
    }

    private data class Call(
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class RecordingTransport(vararg responses: String) : CredoTransport {
        private val responses = ArrayDeque(responses.toList())
        val calls = mutableListOf<Call>()

        override fun post(url: String, headers: Map<String, String>, body: String): String {
            calls += Call(url, headers, body)
            return responses.removeFirst()
        }
    }
}
