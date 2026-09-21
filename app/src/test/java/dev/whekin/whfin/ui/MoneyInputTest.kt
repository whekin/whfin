package dev.whekin.whfin.ui

import org.junit.Assert.*
import org.junit.Test

class MoneyInputTest {
    @Test fun `minor units are exact and reversible`() {
        assertEquals(108320L, parseToMinor("1 083,20"))
        assertEquals(-125L, parseToMinor("-1.25"))
        assertEquals(Long.MAX_VALUE, parseToMinor("92233720368547758.07"))
        assertNull(parseToMinor("1.001"))
        assertNull(parseToMinor("92233720368547758.08"))
        assertNull(parseToMinor("-92233720368547758.08"))
        assertNull(parseToMinor("1e999999999"))
        assertNull(parseToMinor("0"))
    }
}
