package dev.whekin.whfin.data.mutation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.DebtDirection
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.debt.DebtRepository
import dev.whekin.whfin.data.debt.DebtSettlement
import dev.whekin.whfin.data.debt.NewDebt
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Correcting an imported row and taking that correction back must leave a ledger that still passes
 * its own integrity rules, and must not make the row uncorrectable forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CorrectionLifecycleTest {
    private lateinit var db: WhfinDatabase
    private lateinit var mutations: TransactionMutationModule
    private var accountId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"),
        )
        mutations = TransactionMutationModule(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun statementRow(amountMinor: Long = -2_500): Long =
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = amountMinor,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                externalKey = "stmt|test|$amountMinor",
                createdAt = 1,
            ),
        )

    @Test
    fun restore_leavesNoIntegrityIssue() = runBlocking {
        val id = statementRow()
        mutations.voidTransaction(id, reason = "Refunded")
        assertTrue(DataIntegrityChecker(db).run().isHealthy)

        mutations.restoreTransaction(id)

        assertFalse(requireNotNull(db.transactionDao().byId(id)).isVoided)
        val correction = db.transactionDao().correctionsFor(id).single()
        assertNotNull("The correction stays as evidence", correction.correctionRevokedAt)
        val report = DataIntegrityChecker(db).run()
        assertTrue(report.issues.joinToString { it.code }, report.isHealthy)
    }

    @Test
    fun restoredTransaction_canBeCorrectedAgain() = runBlocking {
        val id = statementRow()
        mutations.voidTransaction(id)
        mutations.restoreTransaction(id)

        mutations.voidTransaction(id, reason = "Really was refunded")

        assertTrue(requireNotNull(db.transactionDao().byId(id)).isVoided)
        assertEquals(1, db.transactionDao().activeCorrectionsFor(id).size)
        // Both attempts remain in the audit log.
        assertEquals(2, db.transactionDao().correctionsFor(id).size)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun activeTransaction_cannotBeRestored() = runBlocking {
        val id = statementRow()
        var rejected = false
        try {
            mutations.restoreTransaction(id)
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun borrowedMoney_reachesTheLedgerWithItsPerson() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Nino", color = 1))
        val debts = DebtRepository(db)

        val caseId = debts.open(
            NewDebt(
                personId = personId,
                direction = DebtDirection.I_OWE_THEM,
                amountMinor = 50_000,
                currency = "GEL",
                accountId = accountId,
                occurredAt = 1_000,
            ),
        )

        val event = db.debtDao().eventsForCase(caseId).single()
        val txId = requireNotNull(event.transactionId) { "Borrowing must move money" }
        assertEquals(50_000, requireNotNull(db.transactionDao().byId(txId)).amountMinor)
        val allocation = db.transactionAllocationDao().forTransaction(txId).single()
        assertEquals(50_000, allocation.amountMinor)
        assertEquals(personId, allocation.personId)
        assertEquals(AllocationPurpose.LOAN, allocation.purpose)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun repaymentReceived_reachesTheLedgerWithItsPerson() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Ana", color = 1))
        val debts = DebtRepository(db)
        val caseId = debts.open(
            NewDebt(
                personId = personId,
                direction = DebtDirection.THEY_OWE_ME,
                amountMinor = 10_000,
                currency = "GEL",
                accountId = accountId,
                occurredAt = 1_000,
            ),
        )

        debts.settle(
            DebtSettlement(
                debtCaseId = caseId,
                actualAmountMinor = 10_000,
                actualCurrency = "GEL",
                accountId = accountId,
                close = true,
                occurredAt = 2_000,
            ),
        )

        val settlement = db.debtDao().eventsForCase(caseId).last()
        val txId = requireNotNull(settlement.transactionId)
        assertEquals(10_000, requireNotNull(db.transactionDao().byId(txId)).amountMinor)
        assertEquals(
            AllocationPurpose.REPAYMENT,
            db.transactionAllocationDao().forTransaction(txId).single().purpose,
        )
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun aPlainExpense_isNotTreatedAsATransfer() = runBlocking {
        // The composer used to send the amount as a destination amount for every kind, which
        // described money arriving somewhere it never went and crashed the save.
        val id = mutations.createManual(
            ManualMutation(
                accountId = accountId,
                amountMinor = -1_234,
                destinationAccountId = null,
                destinationAmountMinor = null,
                occurredAt = 1_000,
            ),
        )

        val row = requireNotNull(db.transactionDao().byId(id))
        assertEquals(-1_234, row.amountMinor)
        assertNull(row.transferGroupId)
        assertFalse(row.isTransfer)
    }

    @Test
    fun aDestinationAmountWithoutADestination_isRefusedRatherThanGuessed() = runBlocking {
        var rejected = false
        try {
            mutations.createManual(
                ManualMutation(
                    accountId = accountId,
                    amountMinor = -1_234,
                    destinationAmountMinor = 1_234,
                    occurredAt = 1_000,
                ),
            )
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun incomeCannotBeSharedBetweenPeople() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Data", color = 1))
        val incomeId = mutations.createManual(
            ManualMutation(accountId = accountId, amountMinor = 30_000, occurredAt = 1_000),
        )

        var rejected = false
        try {
            mutations.replaceAllocations(
                incomeId,
                listOf(AllocationMutation(30_000, personId = personId, purpose = AllocationPurpose.SHARED)),
            )
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(db.transactionAllocationDao().forTransaction(incomeId).isEmpty())
    }

    @Test
    fun allocationSignsMustFollowTheirTransaction() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Giorgi", color = 1))
        val expenseId = mutations.createManual(
            ManualMutation(accountId = accountId, amountMinor = -1_000, occurredAt = 1_000),
        )

        var rejected = false
        try {
            mutations.replaceAllocations(
                expenseId,
                listOf(
                    AllocationMutation(-2_000, personId = personId, purpose = AllocationPurpose.SHARED),
                    AllocationMutation(1_000, purpose = AllocationPurpose.PERSONAL),
                ),
            )
        } catch (_: TransactionMutationException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(db.transactionAllocationDao().forTransaction(expenseId).isEmpty())
    }

    @Test
    fun deletingAnImportedRow_reportsWhyNothingHappened() = runBlocking {
        val statementId = statementRow()

        val report = mutations.delete(listOf(MutationSelection(statementId)))

        assertEquals(0, report.changed)
        assertEquals(1, report.skipped)
        assertEquals(MutationRejection.IMPORTED_IS_PROTECTED, report.skippedReason)
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(statementId)?.source)
    }

    @Test
    fun deletingADebtMovement_reportsTheDebtRatherThanTheSource() = runBlocking {
        val personId = db.personDao().insert(PersonEntity(name = "Lasha", color = 1))
        val debts = DebtRepository(db)
        val caseId = debts.open(
            NewDebt(
                personId = personId,
                direction = DebtDirection.THEY_OWE_ME,
                amountMinor = 4_000,
                currency = "GEL",
                accountId = accountId,
                occurredAt = 1_000,
            ),
        )
        val txId = requireNotNull(db.debtDao().eventsForCase(caseId).single().transactionId)

        val report = mutations.delete(listOf(MutationSelection(txId)))

        assertEquals(0, report.changed)
        assertEquals(MutationRejection.DEBT_LINKED, report.skippedReason)
        assertNotNull(db.transactionDao().byId(txId))
    }

    @Test
    fun deletingATransfer_takesBothLegsOrNeither() = runBlocking {
        val other = db.accountDao().insert(
            AccountEntity(name = "Reserve", type = AccountType.SAVINGS, currency = "GEL"),
        )
        val legId = mutations.createManual(
            ManualMutation(
                accountId = accountId,
                amountMinor = -5_000,
                destinationAccountId = other,
                occurredAt = 1_000,
            ),
        )
        val groupId = requireNotNull(requireNotNull(db.transactionDao().byId(legId)).transferGroupId)

        val report = mutations.delete(listOf(MutationSelection(legId)))

        assertEquals(2, report.changed)
        assertTrue(db.transactionDao().byTransferGroup(groupId).isEmpty())
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun aStaleGroupIdInTheSelection_cannotReachAnotherMovement() = runBlocking {
        val other = db.accountDao().insert(
            AccountEntity(name = "Reserve", type = AccountType.SAVINGS, currency = "GEL"),
        )
        val transferLeg = mutations.createManual(
            ManualMutation(
                accountId = accountId,
                amountMinor = -5_000,
                destinationAccountId = other,
                occurredAt = 1_000,
            ),
        )
        val groupId = requireNotNull(requireNotNull(db.transactionDao().byId(transferLeg)).transferGroupId)
        val plain = mutations.createManual(
            ManualMutation(accountId = accountId, amountMinor = -900, occurredAt = 2_000),
        )

        // The screen may hand over a group the row does not belong to; the row decides.
        val report = mutations.delete(listOf(MutationSelection(plain, transferGroupId = groupId)))

        assertEquals(1, report.changed)
        assertNull(db.transactionDao().byId(plain))
        assertEquals(2, db.transactionDao().byTransferGroup(groupId).size)
    }

    @Test
    fun aBatch_deletesWhatItMayAndKeepsTheRest() = runBlocking {
        val manual = mutations.createManual(
            ManualMutation(accountId = accountId, amountMinor = -700, occurredAt = 1_000),
        )
        val imported = statementRow(amountMinor = -800)

        val report = mutations.delete(
            listOf(MutationSelection(manual), MutationSelection(imported)),
        )

        assertEquals(1, report.changed)
        assertEquals(1, report.skipped)
        assertEquals(MutationRejection.IMPORTED_IS_PROTECTED, report.skippedReason)
        assertNull(db.transactionDao().byId(manual))
        assertNotNull(db.transactionDao().byId(imported))
    }

    @Test
    fun aSingleLeggedTransferGroupIsReported() = runBlocking {
        val other = db.accountDao().insert(
            AccountEntity(name = "Reserve", type = AccountType.SAVINGS, currency = "GEL"),
        )
        val transferId = mutations.createManual(
            ManualMutation(
                accountId = accountId,
                amountMinor = -5_000,
                destinationAccountId = other,
                occurredAt = 1_000,
            ),
        )
        assertTrue(DataIntegrityChecker(db).run().isHealthy)

        val groupId = requireNotNull(requireNotNull(db.transactionDao().byId(transferId)).transferGroupId)
        val incoming = db.transactionDao().byTransferGroup(groupId).single { it.amountMinor > 0 }
        db.transactionDao().delete(incoming.id)

        val report = DataIntegrityChecker(db).run()
        assertFalse(report.isHealthy)
        assertTrue(report.issues.any { it.code == "incomplete_transfer_group" })
        assertNull(db.transactionDao().byId(incoming.id))
    }
}
