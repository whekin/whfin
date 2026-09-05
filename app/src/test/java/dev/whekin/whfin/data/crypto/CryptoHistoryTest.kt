package dev.whekin.whfin.data.crypto

import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CryptoHistoryTest {
    private val wallet = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
    private val asset = CryptoNetwork.TRON.asset("USDT")!!
    private val request = CryptoBalanceRequest(CryptoNetwork.TRON, wallet, asset)
    private val calls = mutableListOf<String>()
    private fun provider(vararg pages: String) = HttpCryptoTransferProvider(
        { CryptoEndpoints() },
        object : CryptoHttpTransport {
            override fun post(url: String, body: String): String = error("Unexpected POST")
            override fun get(url: String): String {
                calls += url
                return pages[calls.lastIndex]
            }
        },
    )

    private fun row(hash: String, from: String, to: String, value: String = "1800000000") = """
        {"transaction_id":"$hash","type":"Transfer","from":"$from","to":"$to",
        "value":"$value","block_timestamp":1780000000000,
        "token_info":{"address":"${asset.contractAddress}","symbol":"USDT","decimals":6}}
    """.trimIndent()

    @Test fun historyIncludesWithdrawalsAndReadsPastTheFirstPage() = runBlocking {
        val reader = provider(
            """{"success":true,"data":[${row("withdrawal", wallet, "exchange")}],"meta":{"fingerprint":"next/page"}}""",
            """{"success":true,"data":[${row("salary", "employer", wallet)}]}""",
        )
        val history = reader.history(request)
        assertEquals(listOf("withdrawal", "salary"), history.map { it.txHash })
        assertEquals("exchange", history.first().toAddress)
        assertEquals(BigInteger("1800000000"), history.last().baseUnits)
        assertEquals(2, calls.size)
        assertTrue(calls.all { "only_confirmed=true" in it && "only_to=true" !in it })
        assertTrue(calls.last().contains("fingerprint=next%2Fpage"))
    }
    @Test fun aFailureOnTheNextPageNeverReturnsPartialHistory() {
        val reader = provider(
            """{"success":true,"data":[${row("salary", "employer", wallet)}],"meta":{"fingerprint":"next"}}""",
            """{"success":false}""",
        )
        assertThrows(CryptoBalanceException::class.java) { runBlocking { reader.history(request) } }
    }

    @Test fun tokenIdentityUsesTheContractAndValidatesDecimals() = runBlocking<Unit> {
        val impostor = row("fake", "stranger", wallet).replace(asset.contractAddress!!, "impostor")
        assertEquals(emptyList<CryptoTransfer>(), provider("""{"success":true,"data":[$impostor]}""").history(request))
        calls.clear()
        val wrongScale = row("wrong", "employer", wallet).replace("\"decimals\":6", "\"decimals\":18")
        val reader = provider("""{"success":true,"data":[$wrongScale]}""")
        assertThrows(CryptoBalanceException::class.java) { runBlocking { reader.history(request) } }
    }

    @Test fun repeatedCursorsFailInsteadOfLoopingOrDuplicatingTheLedger() {
        val page = """{"success":true,"data":[${row("salary", "employer", wallet)}],"meta":{"fingerprint":"same"}}"""
        val reader = provider(page, page)
        assertThrows(CryptoBalanceException::class.java) { runBlocking { reader.history(request) } }
        assertEquals(2, calls.size)
    }

}
