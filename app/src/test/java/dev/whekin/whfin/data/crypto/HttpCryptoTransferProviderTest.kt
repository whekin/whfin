package dev.whekin.whfin.data.crypto

import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Written against the real shape of a TronGrid TRC-20 transfer list. The request matters as much as
 * the parsing: dropping `only_to` would count money leaving the wallet as money earned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpCryptoTransferProviderTest {

    private val calls = mutableListOf<String>()
    private var response: String = "{}"

    private val transport = object : CryptoHttpTransport {
        override fun post(url: String, body: String): String = error("transfers are read over GET")
        override fun get(url: String): String {
            calls += url
            return response
        }
    }

    private val address = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"

    private fun provider(endpoints: CryptoEndpoints = CryptoEndpoints()) =
        HttpCryptoTransferProvider(endpoints = { endpoints }, transport = transport)

    private fun payload(vararg rows: String) =
        """{"success":true,"data":[${rows.joinToString(",")}]}"""

    private fun transfer(
        hash: String = "abc123",
        value: String = "2700000000",
        type: String = "Transfer",
        symbol: String = "USDT",
        decimals: Int = 6,
        at: Long = 1_785_921_687_000,
    ) = """
        {"transaction_id":"$hash","type":"$type","value":"$value",
         "from":"TKefRH9GtbTGaaaaaaaaaaaaaaaaaaaaaa","to":"$address",
         "block_timestamp":$at,
         "token_info":{"symbol":"$symbol","decimals":$decimals,
           "address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"}}
    """.trimIndent()

    @Test
    fun `only payments into the address are requested`() = runBlocking {
        response = payload(transfer())

        provider().incoming(CryptoNetwork.TRON, address, sinceMillis = 1_780_000_000_000)

        val url = calls.single()
        assertTrue(url, url.contains("only_to=true"))
        assertTrue(url, url.contains("min_timestamp=1780000000000"))
        assertTrue(url, url.contains("/v1/accounts/$address/transactions/trc20"))
    }

    @Test
    fun `a received payment keeps its exact base units and its own date`() = runBlocking {
        response = payload(transfer())

        val received = provider().incoming(CryptoNetwork.TRON, address, 0).single()

        assertEquals("abc123", received.txHash)
        assertEquals(BigInteger("2700000000"), received.baseUnits)
        assertEquals(6, received.decimals)
        assertEquals("USDT", received.symbol)
        assertEquals(1_785_921_687_000, received.occurredAt)
    }

    @Test
    fun `an approval is not a payment`() = runBlocking {
        response = payload(transfer(type = "Approval"), transfer(hash = "def456"))

        val received = provider().incoming(CryptoNetwork.TRON, address, 0)

        assertEquals(listOf("def456"), received.map { it.txHash })
    }

    @Test
    fun `a zero-value transfer is not income`() = runBlocking {
        response = payload(transfer(value = "0"))

        assertEquals(emptyList<CryptoIncomingTransfer>(), provider().incoming(CryptoNetwork.TRON, address, 0))
    }

    /** An empty answer must never be manufactured from a refusal: it would read as "never paid". */
    @Test
    fun `a refused list raises instead of looking like an empty month`() {
        response = """{"success":false,"error":"rate limited"}"""

        val failure = assertThrows(CryptoBalanceException::class.java) {
            runBlocking { provider().incoming(CryptoNetwork.TRON, address, 0) }
        }

        assertEquals(CryptoBalanceException.Kind.REJECTED, failure.kind)
    }

    @Test
    fun `a chain without a readable transfer list says so rather than returning nothing`() {
        val failure = assertThrows(CryptoBalanceException::class.java) {
            runBlocking { provider().incoming(CryptoNetwork.ETHEREUM, address, 0) }
        }

        assertEquals(CryptoBalanceException.Kind.UNSUPPORTED, failure.kind)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `an endpoint the user cleared is never called`() {
        val failure = assertThrows(CryptoBalanceException::class.java) {
            runBlocking {
                provider(CryptoEndpoints(tronApiUrl = "")).incoming(CryptoNetwork.TRON, address, 0)
            }
        }

        assertEquals(CryptoBalanceException.Kind.NOT_CONFIGURED, failure.kind)
        assertEquals(emptyList<String>(), calls)
    }
}
