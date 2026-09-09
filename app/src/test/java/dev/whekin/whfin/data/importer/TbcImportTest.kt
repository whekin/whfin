package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.SyntheticTbcWorkbook as Tbc
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook as Credo
import dev.whekin.whfin.data.LedgerCalendar
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcImportTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026, 9, 8)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries().build()
        db.categoryDao().insert(CategoryEntity(name = "Bank fees", icon = "AccountBalance", kind = CategoryKind.EXPENSE, color = 0))
        Unit
    }
    @After fun close() = db.close()
    private fun credo(fee: String = "1.50") = Credo.build(
        iban = Tbc.CREDO_IBAN, periodFrom = day, periodTo = day.plusDays(1), openingBalance = "300",
        closingBalance = (java.math.BigDecimal("50") - java.math.BigDecimal(fee)).toPlainString(),
        rows = listOf(
            Credo.Row(day, "სწრაფი გადარიცხვა", debit = "250", balance = "50", description = "Fast transfer, Example Owner, ა/ნ: ${Tbc.IBAN}", beneficiaryName = "Example Owner", beneficiaryAccount = Tbc.IBAN),
            Credo.Row(day, "სხვა და სხვა საკომისიო", debit = fee, balance = (java.math.BigDecimal("50") - java.math.BigDecimal(fee)).toPlainString(), description = "Fast transfer, Example Owner, ა/ნ: ${Tbc.IBAN}", beneficiaryName = "Example Owner", beneficiaryAccount = Tbc.IBAN),
        ),
    )
    private suspend fun load(bytes: ByteArray) = StatementImporter(db).import(bytes.inputStream(), "synthetic.xlsx")

    @Test fun `fast transfer pairs in either import order and fees remain expenses after rebuild`() = runBlocking {
        for (tbcFirst in listOf(true, false)) {
            if (tbcFirst) { load(Tbc.build()); load(credo()) } else { load(credo()); load(Tbc.build()) }
            TransferPairing(db, LedgerCalendar.zone).repairAll()
            val rows = db.transactionDao().allForIntegrity()
            val legs = rows.filter { it.transferGroupId != null }
            assertEquals(2, legs.size)
            assertEquals(1, legs.map { it.transferGroupId }.distinct().size)
            assertEquals(0L, legs.sumOf { it.amountMinor })
            assertTrue(legs.all { it.isTransfer })
            val fee = rows.single { it.amountMinor == -150L }
            assertFalse(fee.isTransfer)
            assertNull(fee.transferGroupId)
            assertNotNull(fee.categoryId)
            val repeat = StatementImporter(db).preview(Tbc.build().inputStream())
            assertTrue(repeat.changesNothing)
            // Reset only this disposable database to exercise the opposite arrival order.
            db.clearAllTables()
            db.categoryDao().insert(CategoryEntity(name = "Bank fees", icon = "AccountBalance", kind = CategoryKind.EXPENSE, color = 0))
        }
    }
    @Test fun `revised bank ID updates money without duplicating or erasing manual categorization`() = runBlocking {
        load(Tbc.build())
        val before = db.transactionDao().allForIntegrity().single { it.amountMinor == -4000L }
        val category = db.categoryDao().all().first().id
        db.transactionDao().update(before.copy(categoryId = category))
        val changed = Tbc.defaultRows.map { it.toMutableList() }
        changed[2][2] = "41"; changed[2][4] = "202"; changed[3][4] = "200"
        val bytes = Tbc.build(changed, closing = "200", paidOut = "50")
        assertEquals(2, StatementImporter(db).preview(bytes.inputStream()).reconciled)
        load(bytes)
        assertEquals(4, db.transactionDao().allForIntegrity().size)
        val after = requireNotNull(db.transactionDao().byId(before.id))
        assertEquals(-4100L, after.amountMinor)
        assertEquals(category, after.categoryId)
        assertTrue(StatementImporter(db).preview(bytes.inputStream()).changesNothing)
    }
    @Test fun `correction breaks an outdated derived link but preserves explicit owner link`() = runBlocking {
        load(credo()); load(Tbc.build())
        val changed = Tbc.defaultRows.map { it.toMutableList() }
        changed[0][3] = "251"
        changed.forEach { it[4] = (java.math.BigDecimal(it[4]) + java.math.BigDecimal.ONE).toPlainString() }
        val bytes = Tbc.build(changed, closing = "202", paidIn = "251")
        load(bytes)
        assertTrue(db.transactionDao().allForIntegrity().filter { it.source == TxSource.STATEMENT }.none { it.isTransfer })
        val legs = db.transactionDao().allForIntegrity().filter { it.amountMinor in listOf(-25000L, 25100L) }
        val group = db.transactionDao().insertTransferGroup(TransferGroupEntity(type = TransferGroupType.OWN_LINK, createdAt = 1))
        db.transactionDao().attachToTransferGroup(legs.map { it.id }, group)
        load(Tbc.build())
        assertTrue(legs.all { db.transactionDao().byId(it.id)?.transferGroupId == group })
    }
    @Test fun `ordinary transfer accepts three day posting lag with reciprocal IBANs`() = runBlocking {
        load(credo("1.00")); load(Tbc.build())
        val credit = db.transactionDao().allForIntegrity().single { it.amountMinor == 25000L }
        db.transactionDao().update(credit.copy(counterpartyIban = Tbc.CREDO_IBAN, note = "Ordinary transfer", postedAt = credit.postedAt!! + 3L * 86400000))
        CrossBankTransfers(db).pair()
        assertNotNull(db.transactionDao().byId(credit.id)?.transferGroupId)
        assertFalse(db.transactionDao().allForIntegrity().single { it.amountMinor == -100L }.isTransfer)
        db.transactionDao().update(requireNotNull(db.transactionDao().byId(credit.id)).copy(postedAt = credit.postedAt!! + 4L * 86400000))
        CrossBankTransfers(db).pair()
        assertNull(db.transactionDao().byId(credit.id)?.transferGroupId)
    }
    @Test fun `ambiguous or missing reciprocal evidence never matches by amount alone`() = runBlocking {
        load(credo()); load(Tbc.build())
        val credit = db.transactionDao().allForIntegrity().single { it.amountMinor == 25000L }
        db.transactionDao().update(credit.copy(note = "No origin account"))
        CrossBankTransfers(db).pair()
        assertNull(db.transactionDao().byId(credit.id)?.transferGroupId)
        db.transactionDao().update(credit.copy(isTransfer = false, transferGroupId = null))
        db.transactionDao().insert(credit.copy(id = 0, externalKey = "another-credit", isTransfer = false, transferGroupId = null))
        CrossBankTransfers(db).pair()
        assertTrue(db.transactionDao().allForIntegrity().none { it.transferGroupId != null })
    }

    @Test fun `optional private cross bank imports reconcile balances and a reciprocal pair`() = runBlocking {
        val directory = System.getenv("WHFIN_REAL_STATEMENTS_DIR")
        Assume.assumeTrue(directory != null)
        val files = java.io.File(requireNotNull(directory)).listFiles().orEmpty().filter { it.extension == "xlsx" }
        Assume.assumeTrue(files.size == 2)
        val statements = files.map { file ->
            dev.whekin.whfin.data.statement.StatementParsers.parse(
                dev.whekin.whfin.data.statement.StatementFile(file.name, file.readBytes()),
            )
        }
        Assume.assumeTrue(statements.map { it.bank.provider }.toSet() == setOf("Credo", "TBC"))
        files.forEach { load(it.readBytes()) }
        val rows = db.transactionDao().allForIntegrity()
        statements.forEach { statement ->
            val account = requireNotNull(db.accountDao().byIbanAndCurrency(statement.accountIban, statement.currency))
            assertTrue("Imported balance must equal statement balance", rows.filter { it.accountId == account.id }.sumOf { it.amountMinor } == statement.closingBalanceMinor)
        }
        val crossGroups = db.transactionDao().crossBankGroupIds()
        assertTrue("Expected a reciprocal cross-bank pair", crossGroups.isNotEmpty())
        crossGroups.forEach { group ->
            val legs = db.transactionDao().byTransferGroup(group)
            assertTrue(legs.size == 2 && legs.sumOf { it.amountMinor } == 0L)
        }
        files.forEach { assertTrue(StatementImporter(db).preview(it.inputStream()).changesNothing) }
    }

    @Test fun `bank correction cannot invalidate an existing expense split`() = runBlocking {
        load(Tbc.build())
        val purchase = db.transactionDao().allForIntegrity().single { it.amountMinor == -4000L }
        db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(
            transactionId = purchase.id, amountMinor = -4000, purpose = AllocationPurpose.PERSONAL,
        )))
        val changed = Tbc.defaultRows.map { it.toMutableList() }
        changed[2][2] = "41"; changed[2][4] = "202"; changed[3][4] = "200"
        try {
            load(Tbc.build(changed, "200", "50"))
            fail("A changed amount with existing allocations must stop before writing")
        } catch (_: InvalidStatementException) { }
        assertEquals(-4000L, db.transactionDao().byId(purchase.id)?.amountMinor)
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
}
