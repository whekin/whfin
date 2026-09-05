package dev.whekin.whfin.data.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.income.IncomeSourceRepository
import dev.whekin.whfin.data.transfer.OwnTransferRepository
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CryptoHistoryRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java)
        .allowMainThreadQueries().build()
    private val asset = CryptoNetwork.TRON.asset("USDT")!!
    private val wallet = "synthetic-wallet"
    private val at = 1_780_000_000_000L
    @After fun close() = db.close()

    private suspend fun setup(): Long {
        val groupId = db.financialGroupDao().insert(FinancialGroupEntity(name = "Test wallet", type = FinancialGroupType.WALLET))
        return db.cryptoDao().insertAddress(WalletAddressEntity(groupId = groupId, chainId = CryptoNetwork.TRON.chainId, address = wallet))
    }
    private fun event(hash: String, incoming: Boolean, units: Long) = CryptoTransfer(
        hash, asset.contractAddress!!, if (incoming) "employer" else wallet,
        if (incoming) wallet else "exchange", BigInteger.valueOf(units), at,
    )
    private fun repository(events: List<CryptoTransfer>) = CryptoHistoryRepository(db, object : CryptoHistoryProvider {
        override suspend fun history(request: CryptoBalanceRequest) =
            if (request.asset.symbol == "USDT") events else emptyList()
    })

    /** Optional replay of a private capture. Input files remain outside the public tree. */
    @Test fun localCapturedHistoryIsImportedIdempotently() = runBlocking {
        val backupPath = System.getenv("WHFIN_REAL_CRYPTO_BACKUP")
        val historyPath = System.getenv("WHFIN_REAL_CRYPTO_HISTORY")
        org.junit.Assume.assumeTrue(backupPath != null && historyPath != null)
        val backup = org.json.JSONObject(java.io.File(backupPath!!).readText())
        val address = backup.getJSONObject("tables").getJSONArray("wallet_addresses").getJSONObject(0).getString("address")
        val body = java.io.File(historyPath!!).readText()
        val groupId = db.financialGroupDao().insert(FinancialGroupEntity(name = "Local replay", type = FinancialGroupType.WALLET))
        db.cryptoDao().insertAddress(WalletAddressEntity(groupId = groupId, chainId = CryptoNetwork.TRON.chainId, address = address))
        val reader = HttpCryptoTransferProvider({ CryptoEndpoints() }, object : CryptoHttpTransport {
            override fun post(url: String, body: String): String = error("Unexpected POST")
            override fun get(url: String): String = body
        })
        val repository = CryptoHistoryRepository(db, object : CryptoHistoryProvider {
            override suspend fun history(request: CryptoBalanceRequest) =
                if (request.asset.symbol == "USDT") reader.history(request) else emptyList()
        })
        val first = repository.refreshAll()
        assertTrue(first.imported > 0)
        assertEquals(0, first.failed)
        val rows = db.transactionDao().allForIntegrity()
        assertTrue(rows.any { it.amountMinor > 0 })
        assertTrue(rows.any { it.amountMinor < 0 })
        assertEquals(org.json.JSONObject(body).getJSONArray("data").length(), rows.size)
        assertEquals(0, repository.refreshAll().imported)
        assertEquals(rows, db.transactionDao().allForIntegrity())
    }

    @Test fun salaryAndWithdrawalPersistOnceEvenIfTheWalletIsEmpty() = runBlocking {
        setup()
        val repository = repository(listOf(event("salary", true, 1800000000), event("withdrawal", false, 1800000000)))
        assertEquals(2, repository.refreshAll().imported)
        val account = db.accountDao().allActive().single()
        assertEquals("USDT", account.currency)
        assertEquals(listOf(-180000L, 180000L), db.transactionDao().allForIntegrity().map { it.amountMinor }.sorted())
        assertEquals(0, repository.refreshAll().imported)
        assertEquals(2, db.transactionDao().allForIntegrity().size)
        // A movement never manufactures or changes a chain balance snapshot.
        assertNull(db.cryptoDao().balance(account.id))
    }

    @Test fun repeatedEventsInOneChainTransactionAreSummedBeforeRounding() = runBlocking {
        setup()
        repository(listOf(event("salary", true, 1000001), event("salary", true, 1999999))).refreshAll()
        assertEquals(300L, db.transactionDao().allForIntegrity().single().amountMinor)
    }

    @Test fun networkFailureLeavesImportedHistoryUntouched() = runBlocking {
        setup()
        repository(listOf(event("salary", true, 1800000000))).refreshAll()
        val failure = CryptoHistoryRepository(db, object : CryptoHistoryProvider {
            override suspend fun history(request: CryptoBalanceRequest): List<CryptoTransfer> = error("offline")
        }).refreshAll()
        assertEquals(2, failure.failed)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
    }

    @Test fun selectedBankCreditBecomesAnUndoableTransferWithoutChangingBalances() = runBlocking {
        setup()
        repository(listOf(event("withdrawal", false, 1800000000))).refreshAll()
        val out = db.transactionDao().allForIntegrity().single()
        val bank = db.accountDao().insert(AccountEntity(name = "Bank", type = AccountType.BANK, currency = "GEL"))
        val credit = db.transactionDao().insert(TransactionEntity(accountId = bank, amountMinor = 450000, currency = "GEL",
            occurredAt = at - 86_400_000, status = TxStatus.CONFIRMED, source = TxSource.STATEMENT))
        val bridges = OwnTransferRepository(db)
        bridges.link(listOf(out.id, credit))
        val linked = db.transactionDao().byId(credit)!!
        assertTrue(linked.isTransfer)
        assertEquals(450000L, linked.amountMinor)
        assertEquals(linked.transferGroupId, db.transactionDao().byId(out.id)!!.transferGroupId)
        bridges.unlink(linked.transferGroupId!!)
        assertFalse(db.transactionDao().byId(credit)!!.isTransfer)
        assertNull(db.transactionDao().byId(out.id)!!.transferGroupId)
    }

    @Test fun aLaterStatementKeepsTheExplicitCryptoBridge() = runBlocking {
        setup()
        repository(listOf(event("withdrawal", false, 1800000000))).refreshAll()
        val out = db.transactionDao().allForIntegrity().single()
        val group = db.financialGroupDao().insert(FinancialGroupEntity(name = "Test bank", type = FinancialGroupType.BANK))
        val accountId = db.accountDao().insert(AccountEntity(name = "Bank", type = AccountType.BANK, currency = "GEL", groupId = group))
        val credit = db.transactionDao().insert(TransactionEntity(accountId = accountId, amountMinor = 450000, currency = "GEL",
            occurredAt = at, status = TxStatus.CONFIRMED, source = TxSource.SMS))
        OwnTransferRepository(db).link(listOf(out.id, credit))
        val bridge = db.transactionDao().byId(credit)!!.transferGroupId
        val day = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
        val row = dev.whekin.whfin.data.statement.StatementRow(day,
            dev.whekin.whfin.data.statement.StatementOperation.OTHER, "Test credit", 450000, 450000,
            "Test exchange", null, null)
        val statement = dev.whekin.whfin.data.statement.BankStatement(
            dev.whekin.whfin.data.statement.BankProfile("Test", "Test"), "GE00CD0000000000000000", "GEL",
            day, day, 0, 450000, listOf(row))
        val plan = dev.whekin.whfin.data.importer.ImportPlan(statement, accountId, false, false,
            listOf(dev.whekin.whfin.data.importer.PlannedRow.Reconcile(row, "test-statement", credit)), emptyList())
        dev.whekin.whfin.data.importer.ImportApplier(db, java.time.ZoneId.of("UTC"))
            .apply(plan, db.accountDao().byId(accountId)!!, null, StatementImportOrigin.FILE)
        assertTrue(db.transactionDao().byId(credit)!!.isTransfer)
        assertEquals(bridge, db.transactionDao().byId(credit)!!.transferGroupId)
        assertEquals(emptyList<Long>(), db.transactionDao().rebuildableTransferGroupIds(group))
    }

    @Test fun switchingFromCashToWalletPreservesTheEarlierEra() = runBlocking {
        val cash = db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "USD"))
        val crypto = db.accountDao().insert(AccountEntity(name = "Wallet", type = AccountType.CRYPTO, currency = "USDT"))
        val sources = IncomeSourceRepository(db)
        val old = IncomeSourceEntity(label = "Pay", amountMinor = 180000, currency = "USD", accountId = cash,
            expectedDayFrom = 5, expectedDayTo = 10, startedOn = 20000, createdAt = 0)
        val id = sources.save(old)
        val nextId = sources.save(old.copy(id = id, accountId = crypto, currency = "USDT", startedOn = 20060))
        assertNotEquals(id, nextId)
        assertEquals(20059L, db.incomeSourceDao().byId(id)!!.endedOn)
        assertEquals(cash, db.incomeSourceDao().byId(id)!!.accountId)
        assertEquals(crypto, db.incomeSourceDao().byId(nextId)!!.accountId)
    }
}
