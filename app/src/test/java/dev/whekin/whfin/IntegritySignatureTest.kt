package dev.whekin.whfin

import dev.whekin.whfin.data.integrity.IntegrityIssue
import dev.whekin.whfin.data.integrity.IntegritySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Not now" has to mean this set of findings, and only this set.
 *
 * Home can be told to leave the books alone, which is the difference between a note and a standing
 * demand nothing but a repair can end. The signature is what makes that safe: silence lasts while
 * the findings are the same and breaks the moment they are not.
 */
class IntegritySignatureTest {

    @Test
    fun `a healthy ledger has nothing to acknowledge`() {
        assertNull(integritySignature(emptyList()))
    }

    @Test
    fun `the same findings keep the same signature whichever order they arrive in`() {
        val first = integritySignature(listOf(issue("duplicate_statement_row", 4293), issue("orphan_allocation", 12)))
        val second = integritySignature(listOf(issue("orphan_allocation", 12), issue("duplicate_statement_row", 4293)))

        assertEquals(first, second)
    }

    @Test
    fun `row ids alone do not break the silence`() {
        // The ledger grows and the same contradiction gets renumbered; that is not new information.
        assertEquals(
            integritySignature(listOf(issue("duplicate_statement_row", 4293))),
            integritySignature(listOf(issue("duplicate_statement_row", 5001))),
        )
    }

    @Test
    fun `a new rule breaks it`() {
        assertNotEquals(
            integritySignature(listOf(issue("duplicate_statement_row", 1))),
            integritySignature(listOf(issue("duplicate_statement_row", 1), issue("incomplete_transfer_group", 2))),
        )
    }

    @Test
    fun `the same rule firing on more rows breaks it`() {
        assertNotEquals(
            integritySignature(listOf(issue("duplicate_statement_row", 1))),
            integritySignature(listOf(issue("duplicate_statement_row", 1), issue("duplicate_statement_row", 2))),
        )
    }

    private fun issue(code: String, id: Long) = IntegrityIssue(
        code = code,
        severity = IntegritySeverity.ERROR,
        entity = "transactions",
        entityId = id,
        message = "…",
    )
}
