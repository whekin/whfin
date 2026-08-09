package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether money is counted once or twice when the same file is imported again.
 */
class StatementIdentityTest {

    private fun row(
        date: LocalDate = LocalDate.of(2026, 3, 14),
        amountMinor: Long = -1_250,
        balanceAfterMinor: Long? = 9_000,
    ) = StatementRow(
        postedDate = date,
        operation = StatementOperation.CARD_PAYMENT,
        operationRaw = "card",
        amountMinor = amountMinor,
        balanceAfterMinor = balanceAfterMinor,
        description = "Coffee",
        beneficiaryName = null,
        beneficiaryAccount = null,
        merchantRaw = "COFFEE",
    )

    @Test
    fun theSameFileTwice_producesTheSameKeys() {
        val first = StatementIdentity("GE00TEST0000000000001", "GEL")
        val second = StatementIdentity("GE00TEST0000000000001", "GEL")
        val rows = listOf(row(), row(amountMinor = -400), row())

        assertEquals(rows.map(first::rowKey), rows.map(second::rowKey))
    }

    @Test
    fun theSameAmountInAnotherCurrencyOfTheSameIban_isAnotherMovement() {
        val gel = StatementIdentity("GE00TEST0000000000001", "GEL").rowKey(row())
        val usd = StatementIdentity("GE00TEST0000000000001", "USD").rowKey(row())

        assertNotEquals(gel, usd)
    }

    @Test
    fun rowsABankPrintsIdentically_stayDistinct() {
        val identity = StatementIdentity("GE00TEST0000000000001", "GEL")

        val first = identity.rowKey(row())
        val second = identity.rowKey(row())

        assertNotEquals("Two coffees at the same shop are two payments", first, second)
    }

    @Test
    fun aRowWithoutARunningBalance_stillGetsAStableKey() {
        val identity = StatementIdentity("GE00TEST0000000000001", "GEL")
        val key = identity.rowKey(row(balanceAfterMinor = null))

        assertTrue(StatementIdentity.isStatementRowKey(key))
        assertEquals(
            key,
            StatementIdentity("GE00TEST0000000000001", "GEL").rowKey(row(balanceAfterMinor = null)),
        )
    }

    @Test
    fun theOpeningAnchorIsPerLedgerAndPeriod() {
        val identity = StatementIdentity("GE00TEST0000000000001", "GEL")

        val january = identity.openingKey(LocalDate.of(2026, 1, 1))
        val february = identity.openingKey(LocalDate.of(2026, 2, 1))

        assertNotEquals(january, february)
        assertFalse(StatementIdentity.isStatementRowKey(january))
        assertNotEquals(
            january,
            StatementIdentity("GE00TEST0000000000001", "USD").openingKey(LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun otherProvenanceIsNotMistakenForStatementTruth() {
        assertFalse(StatementIdentity.isStatementRowKey(null))
        assertFalse(StatementIdentity.isStatementRowKey("sms|card|0001|1700000000000"))
        assertFalse(StatementIdentity.isStatementRowKey("correction|12|1700000000000|0"))
    }
}
