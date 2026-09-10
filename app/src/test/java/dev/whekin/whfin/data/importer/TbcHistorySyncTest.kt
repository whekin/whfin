package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.tbc.*
import dev.whekin.whfin.data.LedgerCalendar
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcHistorySyncTest {
    private lateinit var db: WhfinDatabase
    private val today = LocalDate.of(2026, 9, 9)
    private val remote = TbcLedgerAccount("10", SyntheticTbcWorkbook.IBAN, "GEL", "Everyday")
    private val file get() = SyntheticTbcWorkbook.build()
    private val statement get() = StatementParsers.parse(StatementFile("synthetic.xlsx", file))
    private fun mobile() = statement.rows.mapIndexed { i, row -> TbcHistoryRow("api-$i", "${900+i}",
        row.copy(bankTransactionId = TbcRowIdentity.mobileId("api-$i"), balanceAfterMinor = null)) }
    private class Gateway(val accounts: List<TbcLedgerAccount>, val rows: Map<String, List<TbcHistoryRow>>) : TbcGateway {
        val requestedFrom = mutableListOf<LocalDate>()
        override suspend fun login(username: String, credential: String): TbcLoginResult = error("unused")
        override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession = error("unused")
        override suspend fun resume(session: TbcSession): TbcSession = error("unused")
        override suspend fun accounts() = emptyList<TbcAccount>()
        override fun snapshot() = TbcSession(emptyMap(), "synthetic")
        override fun clear() = Unit
        override suspend fun ledgerAccounts() = accounts
        override suspend fun history(account: TbcLedgerAccount, from: LocalDate, through: LocalDate): List<TbcHistoryRow> {
            requestedFrom += from
            return rows.getValue(account.key).filter { it.row.postedDate in from..through }
        }
    }
    private suspend fun sync(rows: List<TbcHistoryRow> = mobile()) = TbcHistorySync(db).sync(Gateway(listOf(remote), mapOf(remote.key to rows)), today)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    private suspend fun seedOpening(account: TbcLedgerAccount = remote): Long {
        val group = db.financialGroupDao().byProvider(FinancialGroupType.BANK, "TBC")?.id ?: db.financialGroupDao().insert(
            FinancialGroupEntity(name = "TBC", provider = "TBC", type = FinancialGroupType.BANK))
        val id = db.accountDao().insert(AccountEntity(name = account.name, type = AccountType.BANK, groupId = group, currency = account.currency, iban = account.iban))
        db.statementImportDao().insert(StatementImportEntity(accountId = id, periodFrom = today.minusYears(1).toEpochDay(),
            periodTo = today.minusDays(2).toEpochDay(), openingBalanceMinor = 0, closingBalanceMinor = 0, totalRows = 0,
            inserted = 0, duplicates = 0, reconciled = 0, importedAt = 1))
        return id
    }
    @Test fun fileThenApiLinksDifferentIdsWithoutErasingFileBalancesOrCategories() = runBlocking {
        StatementImporter(db).import(file.inputStream())
        val before = db.transactionDao().allForIntegrity()
        val result = sync()
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(0, result.inserted); assertEquals(4, result.matched)
        val after = db.transactionDao().allForIntegrity()
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.balanceAfterMinor }, after.map { it.balanceAfterMinor })
        assertEquals(20100L, after.sumOf { it.amountMinor })
        assertEquals(1, sync().unchanged)
        assertTrue(StatementImporter(db).preview(file.inputStream()).changesNothing)
    }
    @Test fun mobileGroupingOnPurchaseDayDoesNotDuplicateLaterFilePosting() = runBlocking {
        StatementImporter(db).import(file.inputStream())
        val api = mobile().map { it.copy(row = it.row.copy(postedDate = it.row.purchaseDate ?: it.row.postedDate)) }
        val result = sync(api)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(4, result.matched)
        assertEquals(4, db.transactionDao().allForIntegrity().size)
        assertEquals(1, sync(api).unchanged)
    }
    @Test fun apiThenFileUpgradesTheSameRowsAndKeepsMobileAliases() = runBlocking {
        seedOpening()
        assertEquals(4, sync().inserted)
        val ids = db.transactionDao().allForIntegrity().map { it.id }
        assertEquals(4, StatementImporter(db).import(file.inputStream()).reconciled)
        assertEquals(ids, db.transactionDao().allForIntegrity().map { it.id })
        assertTrue(db.transactionDao().allForIntegrity().all { it.balanceAfterMinor != null })
        assertEquals(1, sync().unchanged)
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
    @Test fun newApiRowsAppendOnceAfterFileSeed() = runBlocking {
        StatementImporter(db).import(file.inputStream()); sync()
        val extra = mobile().last().let { it.copy(movementId = "api-new", row = it.row.copy(amountMinor = -350,
            merchantRaw = "EXAMPLE CAFE", description = "POS - EXAMPLE CAFE, 3.50 GEL, Sep  9 2026 10:00AM, MCC: 5812",
            bankTransactionId = TbcRowIdentity.mobileId("api-new"))) }
        assertEquals(1, sync(mobile() + extra).inserted)
        assertEquals(1, sync(mobile() + extra).unchanged)
        assertEquals(5, db.transactionDao().allForIntegrity().size)
        assertEquals(19750L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
    @Test fun ambiguousCrossSourceTwinAbortsBeforeAnyAliasOrMoneyWrite() = runBlocking {
        StatementImporter(db).import(file.inputStream())
        val twin = mobile().last().let { it.copy(movementId = "api-twin", row = it.row.copy(bankTransactionId = TbcRowIdentity.mobileId("api-twin"))) }
        val result = sync(mobile() + twin)
        assertEquals(1, result.errors.size)
        assertEquals(4, db.transactionDao().allForIntegrity().size)
        assertTrue(db.transactionDao().allForIntegrity().none { "|mobile|" in it.externalKey.orEmpty() })
    }
    @Test fun exactReceiptDistinguishesSameMerchantSameAmountOnSameDay() = runBlocking {
        val rows = SyntheticTbcWorkbook.defaultRows.map { it.toMutableList() }.toMutableList()
        val twin = rows.last().toMutableList().apply { set(1, get(1).replace("10:22PM", "11:22PM")); set(4, "199"); set(9, "105") }
        rows += twin
        val bytes = SyntheticTbcWorkbook.build(rows, closing = "199", paidOut = "51")
        val parsed = StatementParsers.parse(StatementFile("synthetic.xlsx", bytes))
        StatementImporter(db).import(bytes.inputStream())
        val api = parsed.rows.mapIndexed { i, row -> TbcHistoryRow("receipt-$i", "$i", row.copy(
            bankTransactionId = TbcRowIdentity.mobileId("receipt-$i"), balanceAfterMinor = null)) }
        val result = sync(api)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(5, result.matched)
    }
    @Test fun nonzeroFileOpeningSurvivesApiSync() = runBlocking {
        val rows = SyntheticTbcWorkbook.defaultRows.map { it.toMutableList().apply { set(4, (get(4).toBigDecimal() + 100.toBigDecimal()).toPlainString()) } }
        val bytes = SyntheticTbcWorkbook.build(rows, closing = "301", opening = "100")
        StatementImporter(db).import(bytes.inputStream())
        assertTrue(sync().errors.isEmpty())
        assertEquals(30100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        assertEquals(1, db.transactionDao().allForIntegrity().count { it.source == TxSource.ADJUSTMENT })
    }
    @Test fun crossBankTransferSurvivesMobileSyncAndFeeRemainsExpense() = runBlocking {
        seedOpening()
        val group = db.financialGroupDao().insert(FinancialGroupEntity(name = "Credo", provider = "Credo", type = FinancialGroupType.BANK))
        val credo = db.accountDao().insert(AccountEntity(name = "Credo", type = AccountType.BANK, groupId = group,
            iban = SyntheticTbcWorkbook.CREDO_IBAN, currency = "GEL"))
        val time = today.minusDays(1).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
        db.transactionDao().insert(TransactionEntity(accountId = credo, amountMinor = -25000, currency = "GEL", occurredAt = time,
            postedAt = time, source = TxSource.STATEMENT, status = TxStatus.CONFIRMED, counterpartyIban = remote.iban, externalKey = "credo-transfer"))
        db.transactionDao().insert(TransactionEntity(accountId = credo, amountMinor = -150, currency = "GEL", occurredAt = time,
            postedAt = time, source = TxSource.STATEMENT, status = TxStatus.CONFIRMED, counterpartyIban = remote.iban, externalKey = "credo-fee"))
        assertTrue(sync().errors.isEmpty())
        val linked = db.transactionDao().allForIntegrity().filter { it.transferGroupId != null }
        assertEquals(2, linked.size)
        assertEquals(0L, linked.sumOf { it.amountMinor })
        assertFalse(db.transactionDao().allForIntegrity().single { it.amountMinor == -150L }.isTransfer)
    }
    @Test fun conversionUsesExplicitBankMeaningAndPairsItsCurrencyLedgers() = runBlocking {
        val usd = remote.copy(id = "11", currency = "USD")
        seedOpening(); seedOpening(usd)
        fun leg(id: String, amount: Long) = TbcHistoryRow(id, "conversion-example", StatementRow(today,
            StatementOperation.CURRENCY_EXCHANGE, "PAYMENTS", amount, null, "კონვერტაცია\ninternal transfer", null, null,
            bankTransactionId = TbcRowIdentity.mobileId(id)))
        val result = TbcHistorySync(db).sync(Gateway(listOf(remote, usd), mapOf(remote.key to listOf(leg("c_fx", 26000)),
            usd.key to listOf(leg("d_fx", -10000)))), today)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val rows = db.transactionDao().allForIntegrity()
        assertTrue(rows.all { it.isTransfer })
        assertEquals(1, rows.map { it.transferGroupId }.distinct().size)
        assertNotNull(rows.first().transferGroupId)
    }
    @Test fun missingInitialBalanceDoesNotInventAnOpeningAdjustment() = runBlocking {
        val result = sync()
        assertEquals(1, result.needsStatement.size)
        assertTrue(db.accountDao().allActive().isEmpty())
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
    }
    @Test fun aRenumberedFileCannotDuplicateAnAlreadyLinkedMobileMovement() = runBlocking {
        StatementImporter(db).import(file.inputStream()); sync()
        val rows = SyntheticTbcWorkbook.defaultRows.map { it.toMutableList() }
        rows[3][9] = "999"
        try { StatementImporter(db).import(SyntheticTbcWorkbook.build(rows).inputStream()); fail() }
        catch (_: InvalidStatementException) { }
        assertEquals(4, db.transactionDao().allForIntegrity().size)
    }
    @Test fun correctionsToFileBackedMoneyRequireFreshFileEvidence() = runBlocking {
        StatementImporter(db).import(file.inputStream()); sync()
        val changed = mobile().mapIndexed { i, row -> if (i == 0) row.copy(row = row.row.copy(amountMinor = 26000)) else row }
        assertEquals(1, sync(changed).errors.size)
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
    @Test fun ownerCanInitializeWithoutAFileAndLaterFileReplacesEstimate() = runBlocking {
        val initial = sync().initialHistories.single()
        TbcHistorySync(db).initialize(initial, 30100)
        assertEquals(30100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        val account = db.accountDao().allActive().single()
        val seed = db.statementImportDao().forAccount(account.id).single { it.origin == StatementImportOrigin.USER_OPENING }
        assertEquals(10000L, seed.openingBalanceMinor)
        assertEquals(0, db.statementImportDao().deleteIfNoEffect(seed.id))
        assertEquals(1, sync().unchanged)
        StatementImporter(db).import(file.inputStream())
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        assertEquals(4, db.transactionDao().allForIntegrity().size)
        assertEquals(1, sync().unchanged)
    }
    @Test fun fourEmptyCurrencyLedgersKeepConfirmedZeroAndDoNotAskAgain() = runBlocking {
        val accounts = listOf("GEL", "USD", "EUR", "GBP").map { remote.copy(currency = it) }
        val gateway = Gateway(accounts, accounts.associate { it.key to emptyList<TbcHistoryRow>() })
        val sync = TbcHistorySync(db)
        val initial = sync.sync(gateway, today)
        assertEquals(4, initial.initialHistories.size)
        initial.initialHistories.forEach { sync.initialize(it, 0L) }
        assertEquals(4, db.accountDao().allActive().size)
        accounts.forEach { remote ->
            val account = requireNotNull(db.accountDao().byIbanAndCurrency(remote.iban, remote.currency))
            assertEquals(0L, db.statementImportDao().earliestWithOpeningBalance(account.id)?.openingBalanceMinor)
        }
        assertTrue(sync.sync(gateway, today).needsStatement.isEmpty())
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
    }

    @Test fun mistakenOpeningCanBeCorrectedWithoutTodaysAdjustmentOrTransactionChanges() = runBlocking {
        TbcHistorySync(db).initialize(sync().initialHistories.single(), 30100)
        val account = db.accountDao().allActive().single()
        val correction = UserOpeningCorrection(db)
        val before = db.transactionDao().allStatementRows(account.id)
        correction.correct(requireNotNull(correction.read(account.id)), 0)
        assertEquals(0L, db.transactionDao().sumByAccount(account.id))
        assertEquals(before, db.transactionDao().allStatementRows(account.id))
        val adjustments = db.transactionDao().activeForAccount(account.id).filter { it.source == TxSource.ADJUSTMENT }
        assertTrue(adjustments.all { it.isTransfer && it.categoryId == null })
        sync()
        assertEquals(0L, db.transactionDao().sumByAccount(account.id))
        correction.correct(requireNotNull(correction.read(account.id)), -1234)
        assertEquals(-1234L, db.transactionDao().sumByAccount(account.id))
        StatementImporter(db).import(file.inputStream())
        assertNull(correction.read(account.id))
        assertEquals(20100L, db.transactionDao().sumByAccount(account.id))
    }

    @Test fun emptyZeroOpeningCanBeCorrectedAndReturnedToZeroForOnlyOneCurrency() = runBlocking {
        val remotes = listOf(remote, remote.copy(currency = "USD"))
        val gateway = Gateway(remotes, remotes.associate { it.key to emptyList<TbcHistoryRow>() })
        TbcHistorySync(db).sync(gateway, today).initialHistories.forEach { TbcHistorySync(db).initialize(it, 0) }
        val account = requireNotNull(db.accountDao().byIbanAndCurrency(remote.iban, "GEL"))
        val correction = UserOpeningCorrection(db)
        correction.correct(requireNotNull(correction.read(account.id)), 1200)
        assertEquals(1200L, db.transactionDao().sumByAccount(account.id))
        val usd = requireNotNull(db.accountDao().byIbanAndCurrency(remote.iban, "USD"))
        assertEquals(0L, db.transactionDao().sumByAccount(usd.id))
        correction.correct(requireNotNull(correction.read(account.id)), 0)
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
        assertTrue(TbcHistorySync(db).sync(gateway, today).needsStatement.isEmpty())
    }

    @Test fun openingCorrectionRejectsStaleLedgerAndNewBankEvidence() = runBlocking {
        TbcHistorySync(db).initialize(sync().initialHistories.single(), 30100)
        val account = db.accountDao().allActive().single()
        val correction = UserOpeningCorrection(db)
        val snapshot = requireNotNull(correction.read(account.id))
        correction.correct(snapshot, 12300)
        assertTrue(runCatching { correction.correct(snapshot, 0) }.exceptionOrNull() is UserOpeningCorrection.Changed)
        val fresh = requireNotNull(correction.read(account.id))
        StatementImporter(db).import(file.inputStream())
        assertTrue(runCatching { correction.correct(fresh, 0) }.exceptionOrNull() is UserOpeningCorrection.Changed)
        assertEquals(20100L, db.transactionDao().sumByAccount(account.id))
    }

    @Test fun manualConfirmationCannotRunTwiceOrUseAnExpiredRead() = runBlocking {
        val initial = sync().initialHistories.single()
        val expired = runCatching { TbcHistorySync(db).initialize(initial.copy(readAt = 1), 0) }.exceptionOrNull()
        assertTrue(expired is TbcException)
        assertTrue(db.accountDao().allActive().isEmpty())
        TbcHistorySync(db).initialize(initial, 0)
        assertTrue(runCatching { TbcHistorySync(db).initialize(initial, 0) }.exceptionOrNull() is TbcException)
        assertEquals(0L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }

    @Test fun bankOpeningAfterProvisionalStartWalksBackAcrossKeptHistory() = runBlocking {
        val initial = sync().initialHistories.single()
        TbcHistorySync(db).initialize(initial, 30100)
        val account = db.accountDao().allActive().single()
        val later = BankStatement(BankProfile("TBC", "TBC"), remote.iban, "GEL", today.plusDays(1), today.plusDays(1), 20100, 20100, emptyList())
        val plan = ImportPlanner(db, LedgerCalendar.zone).plan(later, account, false, false)
        ImportApplier(db, LedgerCalendar.zone).apply(plan, account, "synthetic.xlsx", StatementImportOrigin.FILE)
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        assertEquals(4, db.transactionDao().allForIntegrity().size)
        assertEquals(1, sync().unchanged)
    }

    @Test fun newerBankOpeningForSamePeriodSurvivesNextApiRun() = runBlocking {
        val initial = sync().initialHistories.single()
        TbcHistorySync(db).initialize(initial, 30100)
        val account = db.accountDao().allActive().single()
        for (amount in listOf(0L, 10000L)) {
            val bank = BankStatement(BankProfile("TBC", "TBC"), remote.iban, "GEL", initial.from, initial.from,
                amount, amount, emptyList())
            val plan = ImportPlanner(db, LedgerCalendar.zone).plan(bank, account, false, false)
            ImportApplier(db, LedgerCalendar.zone).apply(plan, account, "synthetic.xlsx", StatementImportOrigin.FILE)
        }
        assertEquals(30100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        sync()
        assertEquals(30100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }

    @Test fun existingOneYearImportIsExtendedWithoutChangingCurrentBalance() = runBlocking {
        StatementImporter(db).import(file.inputStream())
        val older = mobile().first().let { it.copy(movementId = "old", row = it.row.copy(
            postedDate = today.minusYears(3), purchaseDate = null, amountMinor = 10000,
            description = "Earlier deposit", bankTransactionId = TbcRowIdentity.mobileId("old"))) }
        val gateway = Gateway(listOf(remote), mapOf(remote.key to (mobile() + older)))
        assertTrue(TbcHistorySync(db).sync(gateway, today).errors.isEmpty())
        assertEquals(LocalDate.MIN, gateway.requestedFrom.single())
        assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        assertTrue(db.transactionDao().allForIntegrity().any { it.externalKey?.endsWith(TbcRowIdentity.mobileId("old")) == true })
        val account = db.accountDao().allActive().single()
        assertTrue(db.statementImportDao().forAccount(account.id).any { it.origin == StatementImportOrigin.TBC_HISTORY })
        assertEquals(1, TbcHistorySync(db).sync(gateway, today).unchanged)
        assertEquals(today.minusMonths(1), gateway.requestedFrom.last())
    }
    @Test fun newAccountBalanceConfirmationUsesEveryAvailableYear() = runBlocking {
        val older = mobile().first().let { it.copy(movementId = "old", row = it.row.copy(
            postedDate = today.minusYears(3), purchaseDate = null, amountMinor = 10000,
            description = "Earlier deposit", bankTransactionId = TbcRowIdentity.mobileId("old"))) }
        val initial = sync(mobile() + older).initialHistories.single()
        assertEquals(today.minusYears(3), initial.from)
        TbcHistorySync(db).initialize(initial, 30100)
        assertEquals(30100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
        assertNull(db.transactionDao().openingAnchor(db.accountDao().allActive().single().id))
    }

}
