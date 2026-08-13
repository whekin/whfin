package dev.whekin.whfin.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsForegroundCatchUpTest {
    @Test
    fun `first foreground checks only the last day`() {
        val now = 10L * 24 * 60 * 60 * 1_000

        assertEquals(now - 24L * 60 * 60 * 1_000, smsCatchUpSince(now, 0))
    }

    @Test
    fun `completed scans overlap five minutes and throttle rapid resumes`() {
        val minute = 60_000L
        val last = 1_000 * minute

        assertNull(smsCatchUpSince(last + minute, last))
        assertEquals(last - 5 * minute, smsCatchUpSince(last + 6 * minute, last))
    }
}
