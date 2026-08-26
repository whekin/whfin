package dev.whekin.whfin.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRestoreStateTest {

    @Test
    fun `the flag is raised only while the replacement runs`() = runTest {
        assertFalse(LedgerRestoreState.active.value)

        val seen = LedgerRestoreState.during { LedgerRestoreState.active.value }

        assertTrue(seen)
        assertFalse(LedgerRestoreState.active.value)
    }

    @Test
    fun `a failed restore does not leave the ledger looking busy forever`() = runTest {
        val failure = runCatching {
            LedgerRestoreState.during { throw IllegalStateException("bad file") }
        }

        assertEquals("bad file", failure.exceptionOrNull()?.message)
        assertFalse(LedgerRestoreState.active.value)
    }
}
