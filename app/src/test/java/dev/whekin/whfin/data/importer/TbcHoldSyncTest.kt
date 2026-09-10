package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.tbc.*
import dev.whekin.whfin.data.sms.*
import dev.whekin.whfin.data.push.*
import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.mutation.TransactionMutationModule
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class TbcHoldSyncTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026, 9, 10)
    private val remote = TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
    private val time = day.atTime(14, 7, 12).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
    private val hold get() = TbcHold("hold|tbc|synthetic", remote.iban, "GEL", -1200, time, "EXAMPLE CAFE>Tbilisi GE", "0001")
    private val push get() = BankPush(TbcPush.PACKAGE, "synthetic-key", time, text = "12.00 GEL (*0001) EXAMPLE CAFE 10/09/26 14:07")
    private class Gateway(val remote: TbcLedgerAccount) : TbcGateway {
        var booked = emptyList<TbcHistoryRow>(); var held = emptyList<TbcHold>()
        override suspend fun login(username: String, credential: String): TbcLoginResult = error("unused")
        override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession = error("unused")
        override suspend fun resume(session: TbcSession): TbcSession = error("unused")
        override suspend fun accounts() = emptyList<TbcAccount>()
        override fun snapshot() = TbcSession(emptyMap(), "synthetic")
        override fun clear() = Unit
        override suspend fun ledgerAccounts() = listOf(remote)
        override suspend fun history(account: TbcLedgerAccount, from: LocalDate, through: LocalDate) = booked
        override fun pendingHolds() = held
    }
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    private suspend fun initialize(g: Gateway): AccountEntity {
        val sync = TbcHistorySync(db)
        sync.initialize(sync.sync(g, day).initialHistories.single(), 10000)
        return db.accountDao().byIbanAndCurrency(remote.iban, "GEL")!!
    }
    private suspend fun deliver(p: BankPush = push): SmsImportResult {
        val parsed = TbcPush.classify(p)
        return SmsTransactionImporter(db, BankSmsBank.TBC).importPush(parsed, TbcPush.ledgerKey(p, parsed), p.postedAt)
    }
    @Test fun firstSyncIncludesHoldsWithoutSubtractingThemFromOpening() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }
        val account = initialize(g)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
        assertEquals(1, db.transactionDao().allForIntegrity().count { it.source == TxSource.BANK_HOLD })
        assertEquals(1, TbcHistorySync(db).sync(g, day).unchanged)
    }
    @Test fun pushThenHoldThenBookingKeepsOneRowAndCategory() = runBlocking {
        val g = Gateway(remote); val account = initialize(g)
        val sms = SmsTransactionImporter(db, BankSmsBank.TBC)
        val first = deliver()
        sms.resolveDiagnostic(first.diagnosticId!!, account.id)
        val id = db.smsDiagnosticDao().byId(first.diagnosticId)!!.transactionId!!
        val category = db.categoryDao().insert(CategoryEntity(name = "Synthetic food", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0))
        val row = db.transactionDao().byId(id)!!
        db.transactionDao().update(row.copy(categoryId = category))
        g.held = listOf(hold)
        assertEquals(1, TbcHistorySync(db).sync(g, day).matched)
        assertEquals(TxSource.BANK_HOLD, db.transactionDao().byId(id)!!.source)
        g.booked = listOf(TbcHistoryRow("d_booked", "42", StatementRow(day.plusDays(1), StatementOperation.CARD_PAYMENT,
            "CARD", -1200, null, "POS - EXAMPLE CAFE", beneficiaryName = null, beneficiaryAccount = null, merchantRaw = "EXAMPLE CAFE", purchaseDate = day,
            bankTransactionId = TbcRowIdentity.mobileId("d_booked"))))
        val result = TbcHistorySync(db).sync(g, day.plusDays(1))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(id)!!.source)
        assertEquals(category, db.transactionDao().byId(id)!!.categoryId)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, deliver().outcome)
        assertEquals(id, db.bankHoldDao().byKey(hold.key)!!.transactionId)
    }
    @Test fun holdBeforePushDoesNotNeedAnExtraCardRouteOrExpense() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }; val account = initialize(g)
        assertEquals(SmsDiagnosticOutcome.ATTACHED, deliver().outcome)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, deliver().outcome)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
    }
    @Test fun identicalPurchasesRemainDistinctAndAmbiguousPushDoesNotAddMoney() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold, hold.copy(key = "hold|tbc|second", occurredAt = time + 1000)) }
        val account = initialize(g)
        assertEquals(SmsDiagnosticOutcome.UNRECOGNIZED, deliver().outcome)
        assertEquals(7600L, db.transactionDao().sumByAccount(account.id))
    }
    @Test fun settlementKeepsAnOwnerWithdrawnHoldWithdrawn() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }; val account = initialize(g)
        val id = db.bankHoldDao().byKey(hold.key)!!.transactionId
        TransactionMutationModule(db).voidTransaction(id)
        g.booked = listOf(TbcHistoryRow("withdrawn", "77", StatementRow(day, StatementOperation.CARD_PAYMENT,
            "CARD", -1200, null, "POS - EXAMPLE CAFE, 12.00 GEL, Sep 10 2026 2:07PM, MCC: 5812", null, null,
            merchantRaw = "EXAMPLE CAFE", purchaseDate = day, bankTransactionId = TbcRowIdentity.mobileId("withdrawn"))))
        assertTrue(TbcHistorySync(db).sync(g, day).errors.isEmpty())
        assertEquals(10000L, db.transactionDao().sumByAccount(account.id))
        assertTrue(db.transactionDao().byId(id)!!.isVoided)
        assertEquals(1, TbcHistorySync(db).sync(g, day).unchanged)
    }
    @Test fun separatePostedPurchaseAtAnotherMinuteDoesNotConsumeNewHold() = runBlocking {
        val g = Gateway(remote); val account = initialize(g)
        g.booked = listOf(TbcHistoryRow("earlier", "76", StatementRow(day, StatementOperation.CARD_PAYMENT,
            "CARD", -1200, null, "POS - EXAMPLE CAFE, 12.00 GEL, Sep 10 2026 1:00PM, MCC: 5812", null, null,
            merchantRaw = "EXAMPLE CAFE", purchaseDate = day, bankTransactionId = TbcRowIdentity.mobileId("earlier"))))
        g.held = listOf(hold)
        val result = TbcHistorySync(db).sync(g, day)
        assertTrue(result.errors.toString(),result.errors.isEmpty())
        assertEquals(2,result.inserted)
        assertEquals(7600L,db.transactionDao().sumByAccount(account.id))
    }

    @Test fun queuedPushIsAttachedWhenTheApiSuppliesItsAccount() = runBlocking {
        val g = Gateway(remote); val account = initialize(g)
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, deliver().outcome)
        g.held = listOf(hold)
        val result = TbcHistorySync(db).sync(g, day)
        assertEquals(1, result.inserted)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, deliver().outcome)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
    }
    @Test fun missingApiCardSuffixCanBeResolvedWithoutDuplicatingTheHold() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold.copy(cardLast4 = null)) }; val account = initialize(g)
        val first = deliver()
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, first.outcome)
        val result = SmsTransactionImporter(db, BankSmsBank.TBC).resolveDiagnostic(first.diagnosticId!!, account.id)
        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
    }
    @Test fun changingAnUnallocatedHoldUpdatesTheSamePurchaseAndReportsTheChange() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }; val account = initialize(g)
        val id = db.bankHoldDao().byKey(hold.key)!!.transactionId
        g.held = listOf(hold.copy(amountMinor = -1300))
        val result = TbcHistorySync(db).sync(g, day)
        assertEquals(0, result.inserted); assertEquals(1, result.matched)
        assertEquals(id, db.bankHoldDao().byKey(hold.key)!!.transactionId)
        assertEquals(8700L, db.transactionDao().sumByAccount(account.id))
    }

    @Test fun changedHoldAmountCannotRewriteAllocatedMoney() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }; val account = initialize(g)
        val id = db.bankHoldDao().byKey(hold.key)!!.transactionId
        db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(transactionId = id,
            amountMinor = -1200, purpose = AllocationPurpose.PERSONAL)))
        g.held = listOf(hold.copy(amountMinor = -1300))
        assertTrue(TbcHistorySync(db).sync(g, day).errors.isNotEmpty())
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
    }

    @Test fun onePushCannotChooseArbitrarilyBetweenTwoIdenticalHolds() = runBlocking {
        val g = Gateway(remote); val account = initialize(g)
        val first = deliver()
        SmsTransactionImporter(db, BankSmsBank.TBC).resolveDiagnostic(first.diagnosticId!!, account.id)
        g.held = listOf(hold, hold.copy(key = "hold|tbc|second", occurredAt = time + 1000))
        val result = TbcHistorySync(db).sync(g, day)
        assertTrue(result.errors.any { it.contains("HISTORY_CONFLICT") })
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
        assertTrue(db.bankHoldDao().forAccount(account.id).isEmpty())
    }

    @Test fun disappearingHoldIsNotAnInferredCancellationAndOwnerWithdrawalIsPreserved() = runBlocking {
        val g = Gateway(remote).apply { held = listOf(hold) }; val account = initialize(g)
        g.held = emptyList(); TbcHistorySync(db).sync(g, day)
        assertEquals(8800L, db.transactionDao().sumByAccount(account.id))
        val id = db.bankHoldDao().byKey(hold.key)!!.transactionId
        TransactionMutationModule(db).voidTransaction(id)
        g.held = listOf(hold); TbcHistorySync(db).sync(g, day)
        assertEquals(10000L, db.transactionDao().sumByAccount(account.id))
        assertEquals(SmsDiagnosticOutcome.ATTACHED, deliver().outcome)
        assertEquals(10000L, db.transactionDao().sumByAccount(account.id))
        assertTrue(dev.whekin.whfin.data.integrity.DataIntegrityChecker(db).run().isHealthy)
    }
}
