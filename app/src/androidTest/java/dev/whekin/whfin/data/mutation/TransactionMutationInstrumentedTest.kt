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
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
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
}
