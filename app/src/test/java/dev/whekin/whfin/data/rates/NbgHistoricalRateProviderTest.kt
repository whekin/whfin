package dev.whekin.whfin.data.rates

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NbgHistoricalRateProviderTest {

    private var requested: String? = null
    private var body: String = "[]"

    private val transport = object : RateHttpTransport {
        override fun get(url: String): String {
            requested = url
            return body
        }
    }

    private fun provider() = NbgHistoricalRateProvider(transport, "https://example.test/rates")

    @Test
    fun `the requested day is part of the query`() = runBlocking {
        body = """[{"date":"2026-03-14T00:00:00.000Z","currencies":[
            {"code":"USD","quantity":1,"rate":2.729}]}]"""

        provider().quotesOn(LocalDate.of(2026, 3, 14))

        assertTrue(requested!!.endsWith("?date=2026-03-14"))
    }

    @Test
    fun `a quantity of a hundred is normalized to one unit`() = runBlocking {
        body = """[{"date":"2026-03-14T00:00:00.000Z","currencies":[
            {"code":"RUB","quantity":100,"rate":3.15},
            {"code":"USD","quantity":1,"rate":2.729}]}]"""

        val quotes = provider().quotesOn(LocalDate.of(2026, 3, 14))

        assertEquals(BigDecimal("0.0315"), quotes.getValue("RUB").gelPerUnit.stripTrailingZeros())
        assertEquals(BigDecimal("2.729"), quotes.getValue("USD").gelPerUnit.stripTrailingZeros())
    }

    @Test
    fun `a weekend answers with the banking day the quote really belongs to`() = runBlocking {
        // Asked for Sunday, the bank replies with Saturday's publication.
        body = """[{"date":"2026-03-14T00:00:00.000Z","currencies":[
            {"code":"USD","quantity":1,"rate":2.729}]}]"""

        val quotes = provider().quotesOn(LocalDate.of(2026, 3, 15))

        assertEquals("2026-03-14", quotes.getValue("USD").validOn)
    }

    @Test
    fun `an empty or unreadable answer fails instead of returning nothing`() {
        body = "[]"
        assertThrows(RateException::class.java) {
            runBlocking { provider().quotesOn(LocalDate.of(2026, 3, 14)) }
        }
        body = "not json"
        assertThrows(RateException::class.java) {
            runBlocking { provider().quotesOn(LocalDate.of(2026, 3, 14)) }
        }
        body = """[{"date":"2026-03-14T00:00:00.000Z","currencies":[
            {"code":"USD","quantity":1,"rate":0}]}]"""
        assertThrows(RateException::class.java) {
            runBlocking { provider().quotesOn(LocalDate.of(2026, 3, 14)) }
        }
    }
}
