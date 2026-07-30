package dev.whekin.whfin.data.rates

import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Response shapes are copied from the live endpoints, with the quantity trap kept intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RateProvidersTest {

    private val requested = mutableListOf<String>()
    private var body = "{}"

    private val transport = object : RateHttpTransport {
        override fun get(url: String): String {
            requested += url
            return body
        }
    }

    private val nbgBody = """
        [{"date":"2026-07-31T00:00:00.000Z","currencies":[
          {"code":"EUR","quantity":1,"rate":3.0159,"name":"Euro"},
          {"code":"RUB","quantity":100,"rate":3.2807,"name":"Russian Ruble"},
          {"code":"USD","quantity":1,"rate":2.6266,"name":"US Dollar"},
          {"code":"AMD","quantity":1000,"rate":6.8412,"name":"Armenian Dram"}
        ]}]
    """.trimIndent()

    @Test
    fun `NBG quotes are normalized by quantity`() = runBlocking {
        body = nbgBody

        val quotes = NbgFiatRateProvider(transport).quotes(RateContext(now = 42L))
            .associateBy { it.code }

        assertEquals(BigDecimal("2.6266"), quotes.getValue("USD").gelPerUnit.stripTrailingZeros())
        // A ruble is quoted per hundred: taking the raw rate would make it 100x too valuable.
        assertEquals(BigDecimal("0.032807"), quotes.getValue("RUB").gelPerUnit.stripTrailingZeros())
        assertEquals(BigDecimal("0.0068412"), quotes.getValue("AMD").gelPerUnit.stripTrailingZeros())
        assertEquals(42L, quotes.getValue("USD").observedAt)
        assertEquals("2026-07-31", quotes.getValue("USD").validOn)
    }

    @Test
    fun `every currency is requested, so the query says nothing about holdings`() = runBlocking {
        body = nbgBody

        NbgFiatRateProvider(transport).quotes(RateContext(now = 1L))

        assertTrue(requested.single().none { it == '?' })
    }

    @Test
    fun `an unreadable NBG answer fails instead of producing partial quotes`() {
        body = "<html>maintenance</html>"

        assertThrows(RateException::class.java) {
            runBlocking { NbgFiatRateProvider(transport).quotes(RateContext(now = 1L)) }
        }
    }

    @Test
    fun `crypto prices reach GEL through the USD quote`() = runBlocking {
        body = """{"ethereum":{"usd":1923.94},"tron":{"usd":0.328647},"tether":{"usd":0.999055}}"""
        val known = mapOf("USD" to ExchangeRate("USD", BigDecimal("2.6266"), observedAt = 1L))

        val quotes = CoinGeckoPriceProvider(transport).quotes(RateContext(now = 7L, known = known))
            .associateBy { it.code }

        assertEquals(
            BigDecimal("1923.94").multiply(BigDecimal("2.6266")),
            quotes.getValue("ETH").gelPerUnit.stripTrailingZeros(),
        )
        assertEquals(setOf("ETH", "TRX", "USDT"), quotes.keys)
        assertEquals(7L, quotes.getValue("TRX").observedAt)
    }

    @Test
    fun `the asset list is a constant, not the wallet contents`() = runBlocking {
        body = """{"ethereum":{"usd":1.0},"tron":{"usd":1.0},"tether":{"usd":1.0}}"""
        val known = mapOf("USD" to ExchangeRate("USD", BigDecimal("2.6"), observedAt = 1L))

        CoinGeckoPriceProvider(transport).quotes(RateContext(now = 1L, known = known))

        val query = requested.single()
        CoinGeckoPriceProvider.ASSETS.keys.forEach { assertTrue(query.contains(it)) }
    }

    @Test
    fun `without a USD quote crypto is not guessed into GEL`() {
        body = """{"tron":{"usd":0.3}}"""

        assertThrows(RateException::class.java) {
            runBlocking { CoinGeckoPriceProvider(transport).quotes(RateContext(now = 1L)) }
        }
    }
}
