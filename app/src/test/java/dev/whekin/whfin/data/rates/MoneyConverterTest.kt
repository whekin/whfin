package dev.whekin.whfin.data.rates

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversion is the only place a number stops being literally true, so its edges are pinned here. */
class MoneyConverterTest {

    private fun rate(code: String, gel: String, observedAt: Long = 1_000L, validOn: String? = null) =
        code to ExchangeRate(code, BigDecimal(gel), observedAt, validOn)

    private val rates = mapOf(
        rate("USD", "2.6266", observedAt = 2_000L, validOn = "2026-07-31"),
        rate("EUR", "3.0159", observedAt = 2_000L, validOn = "2026-07-31"),
        rate("RUB", "0.032807", observedAt = 2_000L, validOn = "2026-07-31"),
        rate("TRX", "0.086", observedAt = 5_000L),
    )

    private fun amounts(vararg pairs: Pair<String, String>) =
        pairs.associate { (code, value) -> code to BigDecimal(value) }

    @Test
    fun `GEL needs no quote and stays itself`() {
        val total = MoneyConverter.convert(amounts("GEL" to "100.00"), "GEL", emptyMap())

        assertEquals(BigDecimal("100.00"), total.amount)
        assertTrue(total.isComplete)
        assertNull(total.asOf)
    }

    @Test
    fun `a mixed wallet becomes one number in the display currency`() {
        val total = MoneyConverter.convert(
            amounts("GEL" to "100.00", "USD" to "10.00", "TRX" to "1000"),
            "GEL",
            rates,
        )

        // 100 + 10*2.6266 + 1000*0.086
        assertEquals(BigDecimal("212.27"), total.amount)
        assertTrue(total.isComplete)
    }

    @Test
    fun `the same money reads differently in another display currency`() {
        val holdings = amounts("GEL" to "262.66", "USD" to "100.00")

        val gel = MoneyConverter.convert(holdings, "GEL", rates)
        val usd = MoneyConverter.convert(holdings, "USD", rates)
        val rub = MoneyConverter.convert(holdings, "RUB", rates)

        assertEquals(BigDecimal("525.32"), gel.amount)
        assertEquals(BigDecimal("200.00"), usd.amount)
        // 525.32 GEL / 0.032807
        assertEquals(BigDecimal("16012.44"), rub.amount)
    }

    @Test
    fun `a currency without a quote is named, not counted as zero`() {
        val total = MoneyConverter.convert(
            amounts("GEL" to "100.00", "AMD" to "50000"),
            "GEL",
            rates,
        )

        assertEquals(BigDecimal("100.00"), total.amount)
        assertEquals(setOf("AMD"), total.missing)
        assertFalse(total.isComplete)
    }

    @Test
    fun `an unquotable display currency yields no number at all`() {
        val total = MoneyConverter.convert(amounts("GEL" to "100.00"), "AMD", rates)

        assertNull(total.amount)
        assertFalse(total.isComplete)
    }

    @Test
    fun `the total is only as fresh as its oldest quote`() {
        val stale = mapOf(rate("USD", "2.6266", observedAt = 500L), rate("TRX", "0.086", observedAt = 9_000L))

        val total = MoneyConverter.convert(amounts("USD" to "1", "TRX" to "1"), "GEL", stale)

        assertEquals(500L, total.asOf)
    }

    @Test
    fun `the display currency quote counts towards freshness too`() {
        val total = MoneyConverter.convert(amounts("GEL" to "10"), "USD", rates)

        assertEquals(2_000L, total.asOf)
        assertEquals("2026-07-31", total.validOn)
    }

    @Test
    fun `an empty wallet is zero, not unknown`() {
        val total = MoneyConverter.convert(emptyMap(), "GEL", rates)

        assertEquals(BigDecimal("0.00"), total.amount)
        assertTrue(total.isComplete)
    }

    @Test
    fun `a debt keeps its sign through conversion`() {
        val total = MoneyConverter.convert(amounts("USD" to "-10.00"), "GEL", rates)

        assertEquals(BigDecimal("-26.27"), total.amount)
    }

    @Test
    fun `a zero balance in an unquoted currency does not spoil the total`() {
        val total = MoneyConverter.convert(
            amounts("GEL" to "5.00", "AMD" to "0"),
            "GEL",
            rates,
        )

        assertTrue(total.isComplete)
        assertEquals(BigDecimal("5.00"), total.amount)
    }
}
