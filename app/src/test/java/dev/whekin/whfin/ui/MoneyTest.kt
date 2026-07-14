package dev.whekin.whfin.ui

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MoneyTest {
    private lateinit var previousLocale: Locale

    @Before
    fun setUpLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `usd symbol precedes amount and sign`() {
        assertEquals("\$23.60", formatMinor(2_360, "USD"))
        assertEquals("-\$23.60", formatMinor(-2_360, "USD", withSign = true))
        assertEquals("+\$23.60", formatMinor(2_360, "USD", withSign = true))
    }

    @Test
    fun `gel keeps postfix symbol`() {
        assertEquals("23.60 ₾", formatMinor(2_360, "GEL"))
        assertEquals("-23.60 ₾", formatMinor(-2_360, "GEL", withSign = true))
    }
}
