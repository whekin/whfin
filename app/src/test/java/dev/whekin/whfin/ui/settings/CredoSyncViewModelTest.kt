package dev.whekin.whfin.ui.settings

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CredoSyncViewModelTest {
    @Test
    fun statementRange_matchesMyCredoExactTwelveMonthWindow() {
        val now = ZonedDateTime.of(2026, 7, 14, 22, 21, 31, 555_000_000, ZoneId.of("Asia/Tbilisi"))

        val (from, to) = credoStatementRange(now)

        assertEquals("2025-07-14T18:21:31.555Z", from)
        assertEquals("2026-07-14T18:21:31.555Z", to)
    }
}
