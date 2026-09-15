package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoStatementMultiplicityTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026, 9, 9)
    private val zone = LedgerCalendar.zone
    private fun doc(count: Int = 1, amount: Long = -100, to: LocalDate = day) = BankStatement(
        BankProfile("Credo", "Credo"), "GE00CD0000000000000001", "GEL", day, to, 10000,
        10000 + count * amount, (1..count).map { index -> StatementRow(day, StatementOperation.CARD_PAYMENT,
            "CARD", amount, 10000 + index * amount, "Payment EXAMPLE TRANSIT", null, null, "EXAMPLE TRANSIT", day) })
    private suspend fun apply(statement: BankStatement): ImportPlan = db.withTransaction {
        StatementValidator.validate(statement)
        val ledger = BankLedgerResolver(db).resolve(statement)
        ImportPlanner(db, zone).plan(statement, ledger.account, ledger.created, ledger.adopted).also {
            ImportApplier(db, zone).apply(it, ledger.account, "synthetic.xlsx", StatementImportOrigin.FILE)
        }
    }
    private suspend fun rows() = db.transactionDao().allStatementRows(db.accountDao().allActive().single().id)
    private suspend fun duplicate(tx: TransactionEntity, balance: Long): Long = db.transactionDao().insert(
        tx.copy(id = 0, balanceAfterMinor = balance, externalKey = tx.externalKey!!.split('|').toMutableList().also {
            it[5] = balance.toString()
        }.joinToString("|")))
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun twoRealTransitPaymentsRemainTwoAfterRepairAndOlderReplay() = runBlocking {
        val original = doc(2)
        apply(original)
        val ids = rows().map { it.id }
        for ((i, tx) in rows().withIndex()) duplicate(tx, 7000L - i * 100)
        val repaired = apply(original)
        assertEquals(2, repaired.reconciled)
        assertEquals(0, repaired.duplicates)
        assertEquals(ids, rows().filterNot { it.isVoided }.map { it.id })
        val old = original.copy(openingBalanceMinor = 7100, closingBalanceMinor = 6900,
            rows = original.rows.mapIndexed { i, row -> row.copy(balanceAfterMinor = 7000L - i * 100) })
        assertEquals(0, apply(old).inserted)
        assertEquals(ids, rows().filterNot { it.isVoided }.map { it.id })
        assertEquals(0, apply(original).inserted)
        assertTrue(apply(original).isNoOp)
    }
    @Test fun twoOwnTransfersAreNotCollapsed() = runBlocking {
        val document = doc(2, -30000).let { it.copy(rows = it.rows.map { row -> row.copy(
            operation = StatementOperation.OWN_TRANSFER, description = "Personal Transfer", merchantRaw = null,
            beneficiaryAccount = "GE00CD0000000000000002") }) }
        apply(document)
        val ids = rows().map { it.id }
        assertTrue(apply(document).isNoOp)
        assertEquals(ids, rows().filterNot { it.isVoided }.map { it.id })
    }
    @Test fun olderFileWithSameCutoffCannotEraseNewlyConfirmedRepeatEvenAfterRestore() = runBlocking {
        apply(doc())
        apply(doc(2))
        val ids = rows().map { it.id }
        val output = java.io.ByteArrayOutputStream()
        dev.whekin.whfin.data.backup.WhfinBackupManager(db).export(output,
            dev.whekin.whfin.data.backup.WhfinBackupMetadata(java.time.Instant.now(), "test", "GEL"))
        dev.whekin.whfin.data.backup.WhfinBackupManager(db).restore(output.toByteArray().inputStream())
        assertTrue(apply(doc()).statementMerges.isEmpty())
        assertEquals(ids, rows().filterNot { it.isVoided }.map { it.id })
        assertTrue(apply(doc(2)).isNoOp)
    }
    @Test fun olderCutoffCannotRemoveLaterEvidence() = runBlocking {
        apply(doc(to = day.plusDays(1)))
        duplicate(rows().single(), 8000)
        assertTrue(apply(doc()).statementMerges.isEmpty())
        assertEquals(2, rows().count { !it.isVoided })
    }
    @Test fun conflictingCategoriesAndAllocationsRemainUntouched() = runBlocking {
        apply(doc())
        val original = rows().single()
        val id = duplicate(original, 8000)
        val categories = (1..2).map { db.categoryDao().insert(CategoryEntity(name = "Choice $it", kind = CategoryKind.EXPENSE, icon = "Home", color = 0)) }
        db.transactionDao().update(original.copy(categoryId = categories[0]))
        db.transactionDao().update(db.transactionDao().byId(id)!!.copy(categoryId = categories[1]))
        assertTrue(apply(doc()).statementMerges.isEmpty())
        db.transactionDao().update(db.transactionDao().byId(id)!!.copy(categoryId = categories[0]))
        db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(transactionId = id, amountMinor = -100, purpose = AllocationPurpose.PERSONAL)))
        assertTrue(apply(doc()).statementMerges.isEmpty())
        assertEquals(2, rows().count { !it.isVoided })
    }
    @Test fun bankIdentityAndWithdrawalAreNotDiscarded() = runBlocking {
        apply(doc())
        val id = duplicate(rows().single(), 8000)
        val duplicate = db.transactionDao().byId(id)!!
        db.transactionDao().update(duplicate.copy(externalKey = dev.whekin.whfin.data.credo.CredoRowIdentity.join(duplicate.externalKey!!,
            dev.whekin.whfin.data.credo.CredoRowIdentity.mobileId("example-id"))))
        assertTrue(apply(doc()).statementMerges.isEmpty())
        db.transactionDao().update(duplicate.copy(isVoided = true))
        assertTrue(apply(doc()).statementMerges.isEmpty())
        assertTrue(db.transactionDao().byId(id)!!.isVoided)
    }
    @Test fun invalidFileCannotRetireRows() = runBlocking {
        apply(doc())
        duplicate(rows().single(), 8000)
        val before = db.transactionDao().allForIntegrity()
        assertTrue(runCatching { apply(doc().copy(closingBalanceMinor = 0)) }.exceptionOrNull() is InvalidStatementException)
        assertEquals(before, db.transactionDao().allForIntegrity())
    }
    @Test fun mergeAndEvidenceRelinkingRollBackTogether() = runBlocking {
        apply(doc())
        val id = duplicate(rows().single(), 8000)
        db.smsDiagnosticDao().insert(SmsDiagnosticEntity(externalKey = "sms|example", kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.IMPORTED, receivedAt = 1, transactionId = id, updatedAt = 1))
        val before = db.transactionDao().allForIntegrity()
        runCatching { db.withTransaction { apply(doc()); error("Synthetic interrupted import") } }
        assertEquals(before, db.transactionDao().allForIntegrity())
        assertEquals(id, db.smsDiagnosticDao().forTransaction(id).single().transactionId)
        apply(doc())
        assertTrue(db.smsDiagnosticDao().forTransaction(id).isEmpty())
        assertEquals(rows().first { !it.isVoided }.id, db.smsDiagnosticDao().forTransaction(rows().first { !it.isVoided }.id).single().transactionId)
    }
}
