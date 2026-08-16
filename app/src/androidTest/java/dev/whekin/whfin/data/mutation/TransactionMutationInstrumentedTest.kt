package dev.whekin.whfin.data.mutation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.DebtDirection
import dev.whekin.whfin.data.db.DebtEventEntity
import dev.whekin.whfin.data.db.DebtEventKind
import dev.whekin.whfin.data.db.DebtCaseEntity
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.debt.DebtRepository
import dev.whekin.whfin.data.debt.DebtSettlement
import dev.whekin.whfin.data.debt.NewDebt
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionMutationInstrumentedTest {
    private lateinit var db: WhfinDatabase
    private lateinit var mutations: TransactionMutationModule
    private var accountId: Long = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
        accountId = runBlocking {
            db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"))
        }
        mutations = TransactionMutationModule(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * The money in the pocket when the account was added counts, but it was never earned in WHFIN.
     * Marked as a transfer, it reaches the balance without ever reading as this month's income.
     */
    @Test
    fun openingBalance_countsWithoutBeingIncome() = runBlocking {
        val id = mutations.createOpeningBalance(accountId, 30_050, occurredAt = 1_000)

        val row = db.transactionDao().byId(id)!!
        assertEquals(30_050, row.amountMinor)
        assertEquals("GEL", row.currency)
        assertEquals(TxSource.ADJUSTMENT, row.source)
        assertEquals(TxStatus.CONFIRMED, row.status)
        assertTrue("an opening balance is not income", row.isTransfer)
        assertEquals(30_050, db.transactionDao().sumByAccount(accountId))
    }

    @Test
    fun delete_neverRemovesStatementRows() = runBlocking {
        val manualId = mutations.createManual(
            ManualMutation(accountId, -1_000, categoryId = null, occurredAt = 1_000),
        )
        val statementId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_000,
                currency = "GEL",
                occurredAt = 2_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
        )

        val report = mutations.delete(
            listOf(MutationSelection(manualId), MutationSelection(statementId)),
        )

        assertEquals(1, report.changed)
        assertEquals(1, report.skipped)
        assertEquals(null, db.transactionDao().byId(manualId))
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(statementId)?.source)
    }

    @Test
    fun allocations_mustSumExactlyToParent() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Nino", color = 1))
        val transactionId = mutations.createManual(
            ManualMutation(accountId, -1_000, occurredAt = 1_000),
        )

        mutations.replaceAllocations(
            transactionId,
            listOf(
                AllocationMutation(-600, personId = personId, purpose = AllocationPurpose.SHARED),
                AllocationMutation(-400, purpose = AllocationPurpose.PERSONAL),
            ),
        )
        assertEquals(-1_000, db.transactionAllocationDao().forTransaction(transactionId).sumOf { it.amountMinor })

        var rejected = false
        try {
            mutations.replaceAllocations(
                transactionId,
                listOf(AllocationMutation(-600, personId = personId, purpose = AllocationPurpose.SHARED)),
            )
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(-1_000, db.transactionAllocationDao().forTransaction(transactionId).sumOf { it.amountMinor })
    }

    @Test
    fun editingAmount_cannotBreakExistingAllocations() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Ana", color = 1))
        val transactionId = mutations.createManual(
            ManualMutation(accountId, -1_000, occurredAt = 1_000),
            allocations = listOf(
                AllocationMutation(-1_000, personId = personId, purpose = AllocationPurpose.LOAN),
            ),
        )

        var rejected = false
        try {
            mutations.updateManual(
                transactionId,
                ManualMutation(accountId, -900, occurredAt = 1_000),
            )
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(-1_000L, db.transactionDao().byId(transactionId)?.amountMinor)
    }

    @Test
    fun pendingSmsCanBeConfirmed_butStatementCannotBeRewritten() = runBlocking {
        val pendingId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -700,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.PENDING,
                source = TxSource.SMS,
            ),
        )
        val statementId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -800,
                currency = "GEL",
                occurredAt = 2_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
        )

        val report = mutations.setReviewStatus(
            listOf(MutationSelection(pendingId), MutationSelection(statementId)),
            TxStatus.CONFIRMED,
        )

        assertEquals(1, report.changed)
        assertEquals(TxStatus.CONFIRMED, db.transactionDao().byId(pendingId)?.status)
        assertEquals(TxStatus.CONFIRMED, db.transactionDao().byId(statementId)?.status)
        assertEquals(1, report.skipped)
    }

    @Test
    fun debtLinkedManualTransactionIsNotDeleted() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Giorgi", color = 1))
        val transactionId = mutations.createManual(
            ManualMutation(accountId, -1_000, occurredAt = 1_000),
            allocations = listOf(
                AllocationMutation(-1_000, personId = personId, purpose = AllocationPurpose.LOAN),
            ),
        )
        val debtId = db.debtDao().insertCase(
            DebtCaseEntity(
                personId = personId,
                direction = DebtDirection.I_OWE_THEM,
                originalAmountMinor = 1_000,
                currency = "GEL",
                openedAt = 1_000,
            ),
        )
        db.debtDao().insertEvent(
            DebtEventEntity(
                debtCaseId = debtId,
                kind = DebtEventKind.OPENED,
                transactionId = transactionId,
                accountId = accountId,
                actualAmountMinor = 1_000,
                actualCurrency = "GEL",
                occurredAt = 1_000,
            ),
        )

        val report = mutations.delete(listOf(MutationSelection(transactionId)))

        assertEquals(0, report.changed)
        assertEquals(1, report.skipped)
        assertTrue(db.transactionDao().byId(transactionId) != null)
    }

    @Test
    fun statementCorrection_isBalancedAndRestorable() = runBlocking {
        val transactionId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -1_250,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
        )

        mutations.voidTransaction(transactionId, "Wrong bank row")

        val correction = db.transactionDao().correctionsFor(transactionId).single()
        assertTrue(db.transactionDao().byId(transactionId)?.isVoided == true)
        assertTrue(correction.isVoided)
        assertEquals(0L, db.transactionDao().sumByAccount(accountId))

        mutations.restoreTransaction(transactionId)

        assertFalse(db.transactionDao().byId(transactionId)?.isVoided == true)
        assertTrue(db.transactionDao().byId(correction.id)?.isVoided == true)
        assertEquals(-1_250L, db.transactionDao().sumByAccount(accountId))
    }

    @Test
    fun accountArchive_preservesLedgerAndCanRestore() = runBlocking {
        val transactionId = mutations.createManual(
            ManualMutation(accountId, -700, occurredAt = 1_000),
        )

        db.accountDao().archive(accountId)

        assertEquals(0, db.accountDao().allActive().count { it.id == accountId })
        assertEquals(transactionId, db.transactionDao().byId(transactionId)?.id)
        assertEquals(1, db.accountDao().observeArchived().first().count { it.id == accountId })

        db.accountDao().restore(accountId)
        assertEquals(accountId, db.accountDao().allActive().single().id)
    }

    @Test
    fun debtCorrection_reopensClosedCaseWithoutDeletingEvents() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Nino", color = 1))
        val debts = DebtRepository(db)
        val caseId = debts.open(
            NewDebt(
                personId = personId,
                direction = DebtDirection.THEY_OWE_ME,
                amountMinor = 1_000,
                currency = "GEL",
                occurredAt = 1_000,
            ),
        )
        debts.settle(
            DebtSettlement(
                debtCaseId = caseId,
                debtValueMinor = 1_000,
                close = true,
                occurredAt = 2_000,
            ),
        )
        val closing = db.debtDao().eventsForCase(caseId).single { it.kind == DebtEventKind.CLOSED }

        debts.correctEvent(closing.id, "Wrong settlement")

        val events = db.debtDao().eventsForCase(caseId)
        assertTrue(events.any { it.id == closing.id && it.isVoided })
        assertTrue(events.any { it.correctionOfEventId == closing.id && it.isVoided })
        assertEquals(DebtStatus.OPEN, db.debtDao().caseById(caseId)?.status)
        assertEquals(0L, db.debtDao().eventsForCase(caseId).filterNot { it.isVoided }.sumOf { it.debtValueMinor })
    }

    @Test
    fun integrityChecker_reportsAllocationMismatch() = runBlocking {
        val transactionId = mutations.createManual(
            ManualMutation(accountId, -1_000, occurredAt = 1_000),
        )
        db.transactionAllocationDao().insertAll(
            listOf(TransactionAllocationEntity(transactionId = transactionId, amountMinor = -500, purpose = AllocationPurpose.PERSONAL)),
        )

        val report = DataIntegrityChecker(db).run()

        assertTrue(report.issues.any { it.code == "allocation_total_mismatch" && it.entityId == transactionId })
    }
}
