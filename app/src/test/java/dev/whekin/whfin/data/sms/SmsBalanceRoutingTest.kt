package dev.whekin.whfin.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsBalanceRoutingTest {

    @Test
    fun `the ledger whose declared balance reaches the printed figure answers`() {
        val id = accountAtDeclaredBalance(
            evidence = listOf(
                DeclaredBalanceEvidence(accountId = 1, anchorBalanceMinor = 50_000, movedSinceMinor = 0),
                DeclaredBalanceEvidence(accountId = 2, anchorBalanceMinor = 120_000, movedSinceMinor = 0),
            ),
            ledgerDeltaMinor = -10_000,
            declaredBalanceMinor = 110_000,
        )

        assertEquals(2L, id)
    }

    @Test
    fun `what the ledger recorded since that declaration is walked forward too`() {
        val id = accountAtDeclaredBalance(
            evidence = listOf(
                DeclaredBalanceEvidence(accountId = 1, anchorBalanceMinor = 120_000, movedSinceMinor = 0),
                DeclaredBalanceEvidence(accountId = 2, anchorBalanceMinor = 120_000, movedSinceMinor = -20_000),
            ),
            ledgerDeltaMinor = -10_000,
            declaredBalanceMinor = 90_000,
        )

        assertEquals(2L, id)
    }

    @Test
    fun `money arriving is added, not subtracted`() {
        val id = accountAtDeclaredBalance(
            evidence = listOf(
                DeclaredBalanceEvidence(accountId = 7, anchorBalanceMinor = 30_000, movedSinceMinor = 0),
                DeclaredBalanceEvidence(accountId = 8, anchorBalanceMinor = 90_000, movedSinceMinor = 0),
            ),
            ledgerDeltaMinor = 45_000,
            declaredBalanceMinor = 75_000,
        )

        assertEquals(7L, id)
    }

    @Test
    fun `two ledgers at the same figure stay a question`() {
        val id = accountAtDeclaredBalance(
            evidence = listOf(
                DeclaredBalanceEvidence(accountId = 1, anchorBalanceMinor = 50_000, movedSinceMinor = 0),
                DeclaredBalanceEvidence(accountId = 2, anchorBalanceMinor = 40_000, movedSinceMinor = 10_000),
            ),
            ledgerDeltaMinor = -10_000,
            declaredBalanceMinor = 40_000,
        )

        assertNull(id)
    }

    @Test
    fun `a figure no ledger reaches is not forced onto the nearest one`() {
        val id = accountAtDeclaredBalance(
            evidence = listOf(
                DeclaredBalanceEvidence(accountId = 1, anchorBalanceMinor = 50_000, movedSinceMinor = 0),
                DeclaredBalanceEvidence(accountId = 2, anchorBalanceMinor = 120_000, movedSinceMinor = 0),
            ),
            ledgerDeltaMinor = -10_000,
            declaredBalanceMinor = 110_001,
        )

        assertNull(id)
    }

    @Test
    fun `a ledger the bank never declared a balance for does not answer`() {
        val id = accountAtDeclaredBalance(
            evidence = emptyList(),
            ledgerDeltaMinor = -10_000,
            declaredBalanceMinor = 110_000,
        )

        assertNull(id)
    }
}
