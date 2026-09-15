package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import dev.whekin.whfin.data.tbc.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcDepositSyncTest {
    private lateinit var db: WhfinDatabase
    private val today = LocalDate.of(2026, 9, 15)
    private val iban = "GE00TB0000000000000009"
    private val deposit = TbcDepositAccount("7", iban, "GEL", "My Safe", 30000L, acceptsTopUp = true)

    private fun row(date: LocalDate, amount: Long, balance: Long, operation: StatementOperation) = StatementRow(
        postedDate = date, operation = operation, operationRaw = "Deposit top-up", amountMinor = amount,
        balanceAfterMinor = balance, description = "Deposit top-up", beneficiaryName = null, beneficiaryAccount = null)

    /** 200.00 already there, then a top-up and interest, ending at the 300.00 the product shows. */
    private fun statement() = TbcDepositStatement(
        rows = listOf(
            row(today.minusDays(5), 9900L, 29900L, StatementOperation.SAVINGS_TOPUP),
            row(today.minusDays(1), 100L, 30000L, StatementOperation.INTEREST)),
        openingMinor = 20000L, closingMinor = 30000L)

    private class Gateway(
        val ledgers: List<TbcLedgerAccount> = emptyList(),
        val deposits: List<TbcDepositAccount> = emptyList(),
        val statements: Map<String, TbcDepositStatement> = emptyMap(),
        val depositsFailure: TbcException? = null,
    ) : TbcGateway {
        override suspend fun login(username: String, credential: String): TbcLoginResult = error("unused")
        override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession = error("unused")
        override suspend fun resume(session: TbcSession): TbcSession = error("unused")
        override suspend fun accounts() = emptyList<TbcAccount>()
        override fun snapshot() = TbcSession(emptyMap(), "synthetic")
        override fun clear() = Unit
        val asked = mutableListOf<String>()
        override suspend fun ledgerAccounts() = ledgers
        override suspend fun history(account: TbcLedgerAccount, from: LocalDate, through: LocalDate): List<TbcHistoryRow> {
            asked += account.key
            return emptyList()
        }
        override suspend fun deposits(): List<TbcDepositAccount> = depositsFailure?.let { throw it } ?: deposits
        override suspend fun depositStatement(deposit: TbcDepositAccount) = statements.getValue(deposit.key)
    }

    private suspend fun sync(gateway: Gateway) = TbcHistorySync(db).sync(gateway, today)
    private suspend fun ledgerSum(accountId: Long) =
        db.transactionDao().allForIntegrity().filter { it.accountId == accountId && !it.isVoided }.sumOf { it.amountMinor }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    @Test fun aDepositBecomesItsOwnLedgerWhoseBalanceIsTheOneTheBankPrinted() = runBlocking {
        val gateway = Gateway(deposits = listOf(deposit), statements = mapOf(deposit.key to statement()))
        val result = sync(gateway)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(2, result.inserted)
        val account = requireNotNull(db.accountDao().byIbanAndCurrency(iban, "GEL"))
        assertEquals("My Safe", account.name)
        assertEquals(BankProduct.DEMAND_DEPOSIT, account.bankProduct)
        // Opening anchor plus both movements, so the ledger equals the closing balance of the chain.
        assertEquals(30000L, ledgerSum(account.id))
        val report = result.reports.single()
        assertEquals(2, report.received)
        assertFalse(report.bankBalanceDiffers)

        val again = sync(gateway)
        assertEquals(1, again.unchanged)
        assertEquals(0, again.inserted)
        assertEquals(2, again.reports.single().alreadyKnown)
        assertEquals(30000L, ledgerSum(account.id))
    }

    @Test fun aTermDepositIsTheProductTheBankDescribesAndTheGapToItsFigureIsVisible() = runBlocking {
        val term = deposit.copy(acceptsTopUp = false, balanceMinor = 40000L)
        val result = sync(Gateway(deposits = listOf(term), statements = mapOf(term.key to statement())))
        assertTrue(result.errors.isEmpty())
        assertEquals(BankProduct.TERM_DEPOSIT, requireNotNull(db.accountDao().byIbanAndCurrency(iban, "GEL")).bankProduct)
        assertTrue(result.reports.single().bankBalanceDiffers)
    }

    @Test fun anAccountNumberThatIsNotAnIbanStaysAVisibleQuestionInsteadOfALedger() = runBlocking {
        val odd = deposit.copy(accountNo = "7000123456")
        val result = sync(Gateway(deposits = listOf(odd), statements = mapOf(odd.key to statement())))
        assertEquals(listOf("DEPOSIT_ACCOUNT"), result.errors.map { it.substringAfterLast(": ") })
        assertTrue(db.accountDao().allForIntegrity().isEmpty())
        assertEquals("DEPOSIT_ACCOUNT", result.reports.single().error)
    }

    @Test fun aFailedDepositListingCostsNothingToTheCardAccounts() = runBlocking {
        val remote = TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
        val result = sync(Gateway(ledgers = listOf(remote), depositsFailure = TbcException("NETWORK")))
        assertEquals(listOf("NETWORK"), result.errors.map { it.substringAfterLast(": ") })
        assertEquals(1, result.needsStatement.size)
        assertEquals(1, result.initialHistories.size)
    }

    @Test fun anIbanLessLedgerFromSmsIsNeverAdoptedByADeposit() = runBlocking {
        val group = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "TBC", type = FinancialGroupType.BANK, provider = "TBC"))
        val spending = db.accountDao().insert(
            AccountEntity(name = "TBC GEL", type = AccountType.BANK, groupId = group, currency = "GEL"))
        val result = sync(Gateway(deposits = listOf(deposit), statements = mapOf(deposit.key to statement())))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertNull(requireNotNull(db.accountDao().byId(spending)).iban)
        assertEquals(0L, ledgerSum(spending))
        assertEquals(2, db.accountDao().allForIntegrity().size)
        assertEquals(30000L, ledgerSum(requireNotNull(db.accountDao().byIbanAndCurrency(iban, "GEL")).id))
    }

    @Test fun aProductTheBankCallsADepositIsNotAlsoReadAsACardLedger() = runBlocking {
        // The dashboard lists My Safe beside the card accounts, but only the deposit listing prints
        // a running balance, so reading it there is what removes the booked-balance question.
        val alsoInDashboard = TbcLedgerAccount("31", iban, "GEL", "My Safe")
        val gateway = Gateway(ledgers = listOf(alsoInDashboard), deposits = listOf(deposit),
            statements = mapOf(deposit.key to statement()))
        val result = sync(gateway)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertTrue(gateway.asked.isEmpty())
        assertTrue(result.needsStatement.isEmpty())
        assertEquals(2, result.inserted)
        assertEquals(listOf("My Safe · GEL · •0009"), result.reports.map { it.label })
        assertEquals(30000L, ledgerSum(requireNotNull(db.accountDao().byIbanAndCurrency(iban, "GEL")).id))
    }

    @Test fun aLedgerAlreadyReadThroughTheCardHistoryKeepsItsOwnRowsUntouched() = runBlocking {
        val group = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "TBC", type = FinancialGroupType.BANK, provider = "TBC"))
        val account = db.accountDao().insert(AccountEntity(name = "My Safe", type = AccountType.BANK,
            groupId = group, currency = "GEL", iban = iban))
        db.transactionDao().insert(TransactionEntity(accountId = account, amountMinor = 9900L, currency = "GEL",
            occurredAt = today.minusDays(5).atStartOfDay(dev.whekin.whfin.data.LedgerCalendar.zone).toInstant().toEpochMilli(),
            status = TxStatus.CONFIRMED, source = TxSource.STATEMENT,
            externalKey = "stmt|$iban|GEL|id|${TbcRowIdentity.mobileId("movement-1")}"))
        val result = sync(Gateway(deposits = listOf(deposit), statements = mapOf(deposit.key to statement())))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertTrue(result.reports.single().readAsLedger)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
        assertEquals(9900L, ledgerSum(account))
    }

    @Test fun aLedgerTheCardHistoryAlreadyWroteStaysWithIt() = runBlocking {
        val group = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "TBC", type = FinancialGroupType.BANK, provider = "TBC"))
        val account = db.accountDao().insert(AccountEntity(name = "My Safe", type = AccountType.BANK,
            groupId = group, currency = "GEL", iban = iban))
        db.transactionDao().insert(TransactionEntity(accountId = account, amountMinor = 9900L, currency = "GEL",
            occurredAt = today.minusDays(5).atStartOfDay(dev.whekin.whfin.data.LedgerCalendar.zone).toInstant().toEpochMilli(),
            status = TxStatus.CONFIRMED, source = TxSource.STATEMENT,
            externalKey = "stmt|$iban|GEL|id|${TbcRowIdentity.mobileId("movement-1")}"))
        db.statementImportDao().insert(StatementImportEntity(accountId = account,
            periodFrom = today.minusYears(1).toEpochDay(), periodTo = today.minusDays(2).toEpochDay(),
            openingBalanceMinor = 0, closingBalanceMinor = 0, totalRows = 1, inserted = 1, duplicates = 0,
            reconciled = 0, importedAt = 1, origin = StatementImportOrigin.TBC_HISTORY))
        // The deposit listing names this product, but handing it over would leave its ledger with
        // no source at all: the deposit statement cannot name rows the card history already wrote.
        val ledger = TbcLedgerAccount("31", iban, "GEL", "My Safe")
        val gateway = Gateway(ledgers = listOf(ledger), deposits = listOf(deposit),
            statements = mapOf(deposit.key to statement()))
        val result = sync(gateway)

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(ledger.key), gateway.asked)
        assertEquals(9900L, ledgerSum(account))
        assertTrue(result.reports.single { it.readAsLedger }.label.contains("My Safe"))
    }

    @Test fun aDepositWithoutMovementsIsReportedAndWritesNothing() = runBlocking {
        val empty = TbcDepositStatement(emptyList(), 0L, 0L)
        val result = sync(Gateway(deposits = listOf(deposit), statements = mapOf(deposit.key to empty)))
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.reports.single().received)
        assertTrue(db.accountDao().allForIntegrity().isEmpty())
    }
}
