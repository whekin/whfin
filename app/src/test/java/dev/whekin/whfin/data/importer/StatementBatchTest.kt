package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementBatchTest {

    @Test
    fun `a batch with nothing new is imported without a question`() {
        val plan = planStatementBatch(listOf("a", "b")) { preview(inserted = 4) }

        assertTrue(plan.confirmLedgers.isEmpty())
        assertEquals(listOf("a", "b"), plan.toImport)
        assertEquals(0, plan.unchanged)
    }

    @Test
    fun `files that would change nothing are dropped instead of imported`() {
        val previews = mapOf(
            "fresh" to preview(inserted = 2),
            "again" to preview(inserted = 0),
            "once more" to preview(inserted = 0),
        )

        val plan = planStatementBatch(previews.keys.toList()) { previews.getValue(it) }

        assertEquals(listOf("fresh"), plan.toImport)
        assertEquals(2, plan.unchanged)
    }

    @Test
    fun `a statement that only confirms a draft still counts as a change`() {
        val plan = planStatementBatch(listOf("sms week")) { preview(inserted = 0, reconciled = 1) }

        assertEquals(listOf("sms week"), plan.toImport)
        assertEquals(0, plan.unchanged)
    }

    @Test
    fun `two files for the same missing ledger name it once`() {
        val previews = mapOf(
            "q1" to preview(inserted = 40, effect = LedgerEffect.CREATED, ledger = "Credo GEL •0001"),
            "q2" to preview(inserted = 38, effect = LedgerEffect.CREATED, ledger = "Credo GEL •0001"),
            "usd" to preview(inserted = 12, effect = LedgerEffect.CREATED, ledger = "Credo USD •0001"),
        )

        val plan = planStatementBatch(previews.keys.toList()) { previews.getValue(it) }

        assertEquals(listOf("Credo GEL •0001", "Credo USD •0001"), plan.confirmLedgers)
        assertEquals(3, plan.toImport.size)
    }

    @Test
    fun `adopting an SMS ledger is not a new account and is never asked about`() {
        val plan = planStatementBatch(listOf("first statement")) {
            preview(inserted = 9, effect = LedgerEffect.ADOPTED, ledger = "Everyday")
        }

        assertTrue(plan.confirmLedgers.isEmpty())
        assertEquals(listOf("first statement"), plan.toImport)
    }

    @Test
    fun `an unreadable file is kept so the import itself reports why`() {
        val plan = planStatementBatch(listOf("broken", "fine")) {
            if (it == "broken") null else preview(inserted = 0)
        }

        assertEquals(listOf("broken"), plan.toImport)
        assertEquals(1, plan.unchanged)
    }

    private fun preview(
        inserted: Int,
        reconciled: Int = 0,
        reviewCount: Int = 0,
        effect: LedgerEffect = LedgerEffect.UNCHANGED,
        ledger: String = "Credo GEL •0001",
    ) = StatementImporter.Preview(
        statement = BankStatement(
            bank = BankProfile(provider = "Credo", displayName = "Credo"),
            accountIban = "GE00WH0000000000000000",
            currency = "GEL",
            periodFrom = null,
            periodTo = null,
            openingBalanceMinor = null,
            closingBalanceMinor = null,
            rows = emptyList(),
        ),
        ledgerEffect = effect,
        ledgerName = ledger,
        totalRows = inserted,
        inserted = inserted,
        duplicates = 0,
        reconciled = reconciled,
        reviewCount = reviewCount,
    )
}
