package dev.whekin.whfin.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredoSyncReminderTest {
    @Test
    fun recentSyncStaysQuietUntilThirtyDays() {
        val now = 50L * DAY

        assertNull(credoSyncReminder(now - 29L * DAY, 100, hasCredoAccounts = true, nowMillis = now))
        assertEquals(
            CredoSyncReminder(daysSinceSync = 30, awaitingStatementCount = 100),
            credoSyncReminder(now - 30L * DAY, 100, hasCredoAccounts = true, nowMillis = now),
        )
    }

    @Test
    fun neverSyncedOnlyAppearsWhenSmsOperationsNeedStatementEvidence() {
        assertNull(credoSyncReminder(null, 0, hasCredoAccounts = true, nowMillis = 50L * DAY))
        assertEquals(
            CredoSyncReminder(daysSinceSync = null, awaitingStatementCount = 4),
            credoSyncReminder(null, 4, hasCredoAccounts = true, nowMillis = 50L * DAY),
        )
    }

    @Test
    fun archivedOrRemovedCredoDoesNotLeaveAStaleReminder() {
        assertNull(
            credoSyncReminder(
                lastCompletedAt = 1L,
                awaitingStatementCount = 10,
                hasCredoAccounts = false,
                nowMillis = 50L * DAY,
            ),
        )
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
