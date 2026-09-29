package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsSettlementEvidenceTest {
    private lateinit var db: WhfinDatabase
    private lateinit var gel: AccountEntity
    private lateinit var usd: AccountEntity
    private val day = LocalDate.of(2026, 9, 8)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries().build()
        val group = db.financialGroupDao().insert(FinancialGroupEntity(name="Credo",provider="Credo",type=FinancialGroupType.BANK))
        suspend fun account(currency: String): AccountEntity {
            val id = db.accountDao().insert(AccountEntity(name="Example $currency",type=AccountType.BANK,
                groupId=group,iban="GE00CD0000000000000001",currency=currency))
            return requireNotNull(db.accountDao().byId(id))
        }
        gel=account("GEL"); usd=account("USD")
        db.paymentInstrumentDao().linkForAccount(gel,"0001",PaymentInstrumentType.PHYSICAL_CARD)
        db.paymentInstrumentDao().linkForAccount(usd,"0001",PaymentInstrumentType.PHYSICAL_CARD)
    }
    @After fun close() = db.close()
    private fun body(currency: String="USD", amount: String="7.00") =
        "Payment: $amount $currency Card N ****0001 EXAMPLE SUBSCRIPTION>Tbilisi GE Balance: 80.00 GEL 08/09/2026 22:22:00"
    private suspend fun statement(account: AccountEntity, lag: Long=0, amount: Long=-700, merchant: String="EXAMPLE SUBSCRIPTION"): Long =
        db.transactionDao().insert(TransactionEntity(accountId=account.id,amountMinor=amount,currency=account.currency,
            occurredAt=day.plusDays(lag).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli(),
            rawCounterparty=merchant,source=TxSource.STATEMENT,status=TxStatus.CONFIRMED))
    @Test fun foreignPurchaseUsesExactSettlementCurrencyWithinTheMappedCardFamily() = runBlocking {
        val id=statement(usd)
        val result=SmsTransactionImporter(db).import(body())
        assertEquals(SmsDiagnosticOutcome.ATTACHED,result.outcome)
        assertEquals(id,result.transactionId)
        assertEquals(1,db.transactionDao().allForIntegrity().size)
        assertEquals(usd.id,db.smsDiagnosticDao().byId(requireNotNull(result.diagnosticId))?.accountId)
    }
    @Test fun exactPurchaseCanSettleTwoDaysLater() = runBlocking {
        val id=statement(gel,lag=2)
        val result=SmsTransactionImporter(db).import(body("GEL"))
        assertEquals(SmsDiagnosticOutcome.ATTACHED,result.outcome)
        assertEquals(id,result.transactionId)
        assertEquals(1,db.transactionDao().allForIntegrity().size)
    }
    @Test fun widerWindowDoesNotChooseBetweenRepeatedPurchases() = runBlocking {
        statement(gel,lag=1); statement(gel,lag=2)
        val result=SmsTransactionImporter(db).preview(body("GEL"))
        assertNull(result.transactionId)
        assertEquals(2,db.transactionDao().allForIntegrity().size)
    }
    @Test fun foreignPurchaseDoesNotSearchAnotherContract() = runBlocking {
        val other=db.accountDao().insert(usd.copy(id=0,iban="GE00CD0000000000000002"))
        statement(requireNotNull(db.accountDao().byId(other)))
        assertNull(SmsTransactionImporter(db).preview(body()).transactionId)
    }
    @Test fun foreignPurchaseRequiresExactAmountInPurchaseCurrency() = runBlocking {
        statement(usd,amount=-900)
        assertNull(SmsTransactionImporter(db).preview(body()).transactionId)
    }
    @Test fun occupiedBankEvidenceIsNotAssignedToAnotherMessage() = runBlocking {
        statement(usd)
        assertEquals(SmsDiagnosticOutcome.ATTACHED,SmsTransactionImporter(db).import(body()).outcome)
        assertNull(SmsTransactionImporter(db).preview(body().replace("22:22:00","22:23:00")).transactionId)
    }
    @Test fun extraSettlementDayRequiresComparableMoney() = runBlocking {
        statement(gel,lag=2,amount=-1900)
        assertNull(SmsTransactionImporter(db).preview(body()).transactionId)
    }
    private suspend fun cover() {
        db.statementImportDao().insert(StatementImportEntity(accountId=gel.id,origin=StatementImportOrigin.CREDO_SYNC,
            periodFrom=day.toEpochDay(),periodTo=day.plusDays(3).toEpochDay(),openingBalanceMinor=0,
            closingBalanceMinor=0,totalRows=1,inserted=1,duplicates=0,reconciled=0,importedAt=1))
    }
    private suspend fun cohort(vararg amounts: String): List<Long> {
        val importer=SmsTransactionImporter(db)
        return amounts.mapIndexed { i, amount -> requireNotNull(importer.import(
            body("GEL",amount).replace("22:22:00","22:2${i}:00")).diagnosticId) }
    }
    @Test fun fullCardCohortAttachesToOneConsolidatedBankRowWithoutChangingMoney() = runBlocking {
        cover(); val id=statement(gel)
        val ids=cohort("6.00","1.00")
        val before=db.transactionDao().allForIntegrity()
        assertEquals(2,SmsTransactionImporter(db).attachUnroutedToStatements())
        ids.forEach { assertEquals(id,db.smsDiagnosticDao().byId(it)?.transactionId) }
        assertEquals(before,db.transactionDao().allForIntegrity())
        assertEquals(0,SmsTransactionImporter(db).attachUnroutedToStatements())
    }
    @Test fun consolidationDoesNotPickASubsetOfTheDaysMessages() = runBlocking {
        cover(); statement(gel); cohort("6.00","1.00","2.00")
        assertEquals(0,SmsTransactionImporter(db).attachUnroutedToStatements())
        assertEquals(3,db.smsDiagnosticDao().unrouted().size)
    }
    @Test fun anotherBankChargeAtTheMerchantPreventsConsolidation() = runBlocking {
        cover(); statement(gel); statement(gel,amount=-200); cohort("6.00","1.00")
        assertEquals(0,SmsTransactionImporter(db).attachUnroutedToStatements())
    }
    @Test fun ownTransferUsesThePairedReceivingIbanToDistinguishEqualWithdrawals() = runBlocking {
        val destination=db.accountDao().insert(gel.copy(id=0,iban="GE00CD0000000000000002"))
        val another=db.accountDao().insert(gel.copy(id=0,iban="GE00CD0000000000000003"))
        suspend fun pair(to: Long): Long {
            val group=db.transactionDao().insertTransferGroup(TransferGroupEntity(type=TransferGroupType.TRANSFER,createdAt=1))
            val at=day.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
            val sent=db.transactionDao().insert(TransactionEntity(accountId=gel.id,amountMinor=-700,currency="GEL",
                occurredAt=at,source=TxSource.STATEMENT,status=TxStatus.CONFIRMED,isTransfer=true,transferGroupId=group))
            db.transactionDao().insert(TransactionEntity(accountId=to,amountMinor=700,currency="GEL",
                occurredAt=at,source=TxSource.STATEMENT,status=TxStatus.CONFIRMED,isTransfer=true,transferGroupId=group))
            return sent
        }
        val id=pair(destination); pair(another)
        val sms="Transfer between accounts\nAmount: 7.00 GEL;\nFrom: GE00CD0000000000000001\nTo: GE00CD0000000000000002\nBalance: 80.00 GEL\nDate: 9/8/2026 10:22:00 PM"
        assertEquals(id,SmsTransactionImporter(db).import(sms).transactionId)
        assertEquals(4,db.transactionDao().allForIntegrity().size)
    }
}
