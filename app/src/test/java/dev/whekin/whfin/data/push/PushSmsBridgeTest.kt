package dev.whekin.whfin.data.push

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sms.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushSmsBridgeTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var account = 0L
    private val body = "2.00 GEL (*0001) EXAMPLE BUS 10/09/26 14:07"
    private val push get() = BankPush(TbcPush.PACKAGE, "synthetic-key", System.currentTimeMillis(), title = "TBC", text = body + "\nhttps://example.invalid/detail")
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java).allowMainThreadQueries().build()
        importer = SmsTransactionImporter(db, BankSmsBank.TBC)
        val group = db.financialGroupDao().insert(FinancialGroupEntity(name = "TBC", provider = "TBC", type = FinancialGroupType.BANK))
        account = db.accountDao().insert(AccountEntity(name = "Everyday", type = AccountType.BANK, currency = "GEL", groupId = group, iban = "GE00TB0000000000000001"))
    }
    @After fun close() = db.close()
    private suspend fun pushImport(value: BankPush = push): SmsImportResult {
        val parsed = TbcPush.classify(value)
        return importer.importPush(parsed, TbcPush.ledgerKey(value, parsed), value.postedAt)
    }
    private suspend fun mapping() {
        val unresolved = importer.import(body)
        importer.resolveDiagnostic(requireNotNull(unresolved.diagnosticId), account)
    }
    @Test fun smsThenPushAndRepeatedPushMakeOneExpense() = runBlocking {
        mapping()
        assertEquals(SmsDiagnosticOutcome.ATTACHED, pushImport().outcome)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, pushImport().outcome)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
        assertEquals(-200L, db.transactionDao().sumByAccount(account))
    }
    @Test fun pushThenSmsAlsoMakeOneExpenseWhenCardIsMappedLater() = runBlocking {
        val first = pushImport()
        importer.import(body)
        importer.resolveDiagnostic(requireNotNull(first.diagnosticId), account)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
        assertEquals(-200L, db.transactionDao().sumByAccount(account))
    }
    @Test fun separateNotificationsWithinOneChannelAreNotCollapsedByAmount() = runBlocking {
        mapping()
        pushImport()
        pushImport(push.copy(notificationKey = "second-notification"))
        assertEquals(2, db.transactionDao().allForIntegrity().size)
    }    @Test fun ambiguousOppositeChannelMatchesWaitForReview() = runBlocking {
        mapping()
        importer.import(body + " https://example.invalid/second")
        assertEquals(2, db.transactionDao().allForIntegrity().size)
        assertEquals(SmsDiagnosticOutcome.UNRECOGNIZED, pushImport().outcome)
        assertEquals(2, db.transactionDao().allForIntegrity().size)
    }

}
