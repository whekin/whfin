package dev.whekin.whfin.data.crypto

import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chain adapters are exercised through a scripted transport: the request shape matters as much
 * as the parsed number, because a wrong parameter silently returns somebody else's balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpCryptoBalanceProviderTest {

    private val calls = mutableListOf<Pair<String, String>>()
    private var responder: (String, String) -> String = { _, _ -> "{}" }

    private val transport = object : CryptoHttpTransport {
        override fun post(url: String, body: String): String {
            calls += url to body
            return responder(url, body)
        }

        override fun get(url: String): String {
            calls += url to ""
            return responder(url, "")
        }
    }

    private fun provider(endpoints: CryptoEndpoints = CryptoEndpoints()) =
        HttpCryptoBalanceProvider(endpoints = { endpoints }, transport = transport)

    private val evmAddress = "0x00112233445566778899aabbccddeeff00112233"
    private val tronAddress = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"

    private fun request(network: CryptoNetwork, symbol: String) = CryptoBalanceRequest(
        network = network,
        address = if (network == CryptoNetwork.ETHEREUM) evmAddress else tronAddress,
        asset = network.asset(symbol)!!,
    )

    @Test
    fun `native ether is read with eth_getBalance and kept in wei`() = runBlocking {
        responder = { _, _ -> """{"jsonrpc":"2.0","id":1,"result":"0x1bc16d674ec80000"}""" }

        val reading = provider().balance(request(CryptoNetwork.ETHEREUM, "ETH"))

        assertEquals(BigInteger("2000000000000000000"), reading.baseUnits)
        assertEquals(18, reading.decimals)
        val body = JSONObject(calls.single().second)
        assertEquals("eth_getBalance", body.getString("method"))
        assertEquals(evmAddress, body.getJSONArray("params").getString(0))
    }

    @Test
    fun `an ERC-20 balance calls balanceOf on the pinned contract`() = runBlocking {
        responder = { _, _ -> """{"result":"0x0000000000000000000000000000000000000000000000000000000005f5e100"}""" }

        val reading = provider().balance(request(CryptoNetwork.ETHEREUM, "USDT"))

        assertEquals(BigInteger("100000000"), reading.baseUnits)
        assertEquals(6, reading.decimals)
        val call = JSONObject(calls.single().second).getJSONArray("params").getJSONObject(0)
        assertEquals(CryptoNetwork.ETHEREUM.asset("USDT")!!.contractAddress, call.getString("to"))
        assertTrue(call.getString("data").startsWith("0x70a08231"))
        assertTrue(call.getString("data").endsWith(evmAddress.removePrefix("0x")))
    }

    @Test
    fun `a JSON-RPC error is reported as rejected, not as a zero balance`() {
        responder = { _, _ -> """{"error":{"code":-32000,"message":"limit exceeded"}}""" }

        val error = assertThrows(CryptoBalanceException::class.java) {
            runBlocking { provider().balance(request(CryptoNetwork.ETHEREUM, "ETH")) }
        }
        assertEquals(CryptoBalanceException.Kind.REJECTED, error.kind)
    }

    @Test
    fun `native TRX comes from the account endpoint in sun`() = runBlocking {
        responder = { _, _ -> """{"balance":1500000}""" }

        val reading = provider().balance(request(CryptoNetwork.TRON, "TRX"))

        assertEquals(BigInteger("1500000"), reading.baseUnits)
        assertTrue(calls.single().first.endsWith("/wallet/getaccount"))
    }

    @Test
    fun `an address that never received anything reads as zero, not as a failure`() = runBlocking {
        responder = { _, _ -> "{}" }

        assertEquals(BigInteger.ZERO, provider().balance(request(CryptoNetwork.TRON, "TRX")).baseUnits)
    }

    @Test
    fun `a TRC-20 balance passes the raw address as the padded parameter`() = runBlocking {
        responder = { _, _ ->
            """{"constant_result":["0000000000000000000000000000000000000000000000000000000000000539"]}"""
        }

        val reading = provider().balance(request(CryptoNetwork.TRON, "USDT"))

        assertEquals(BigInteger("1337"), reading.baseUnits)
        val body = JSONObject(calls.single().second)
        assertEquals("balanceOf(address)", body.getString("function_selector"))
        assertEquals(64, body.getString("parameter").length)
        assertTrue(
            body.getString("parameter")
                .endsWith(CryptoAddressValidator.tronAddressHex(tronAddress)!!),
        )
        assertTrue(calls.single().first.endsWith("/wallet/triggerconstantcontract"))
    }

    @Test
    fun `a failing contract call is rejected instead of read as empty`() {
        responder = { _, _ -> """{"result":{"message":"REVERT"}}""" }

        val error = assertThrows(CryptoBalanceException::class.java) {
            runBlocking { provider().balance(request(CryptoNetwork.TRON, "USDT")) }
        }
        assertEquals(CryptoBalanceException.Kind.REJECTED, error.kind)
    }

    @Test
    fun `a non-https endpoint is refused before anything is sent`() {
        val error = assertThrows(CryptoBalanceException::class.java) {
            runBlocking {
                provider(CryptoEndpoints(ethereumRpcUrl = "http://insecure.example"))
                    .balance(request(CryptoNetwork.ETHEREUM, "ETH"))
            }
        }
        assertEquals(CryptoBalanceException.Kind.NOT_CONFIGURED, error.kind)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `a blank endpoint stops the read instead of falling back silently`() {
        val error = assertThrows(CryptoBalanceException::class.java) {
            runBlocking {
                provider(CryptoEndpoints(tronApiUrl = "  "))
                    .balance(request(CryptoNetwork.TRON, "TRX"))
            }
        }
        assertEquals(CryptoBalanceException.Kind.NOT_CONFIGURED, error.kind)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `defaults are https so a first run never sends an address in the clear`() {
        assertTrue(CryptoEndpoints.isUsable(CryptoEndpoints.DEFAULT_ETHEREUM_RPC))
        assertTrue(CryptoEndpoints.isUsable(CryptoEndpoints.DEFAULT_TRON_API))
    }
}
