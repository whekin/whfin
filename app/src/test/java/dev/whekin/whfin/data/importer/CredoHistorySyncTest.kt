package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.credo.*
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
class CredoHistorySyncTest {
    private lateinit var db: WhfinDatabase
    private val day = LocalDate.of(2026, 9, 9)
    private val remote = CredoRemoteAccount("GE00CD0000000000000001", "GEL", 10, "Current", "ACCOUNT")
    private val session = CredoSession("synthetic", null)
    private fun row(id: String? = null, amount: Long = -500) = StatementRow(day, StatementOperation.CARD_PAYMENT,
        "საბარათე ოპერაცია", amount, if (id == null) 9500 else null,
        "გადახდა - EXAMPLE CAFE 5.00 GEL 09.09.2026", null, null, "EXAMPLE CAFE", day,
        bankTransactionId = id?.let(CredoRowIdentity::mobileId))
    private fun statement(rows: List<StatementRow>) = BankStatement(BankProfile("Credo", "Credo"), remote.accountNumber,
        remote.currency, day, day, null, null, rows)
    private suspend fun file(rows: List<StatementRow> = listOf(row())): ImportPlan {
        val statement = statement(rows).copy(openingBalanceMinor = 10000, closingBalanceMinor = 9500)
        val resolved = BankLedgerResolver(db).resolve(statement)
        val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, resolved.account, resolved.created, resolved.adopted)
        ImportApplier(db, LedgerCalendar.zone).apply(plan, resolved.account, "synthetic.xlsx", StatementImportOrigin.FILE)
        return plan
    }
    private fun gateway(rows: List<StatementRow>) = object : CredoGateway {
        override suspend fun initiateLogin(credentials: CredoCredentials): CredoLoginChallenge = error("unused")
        override suspend fun sendOtp(operationId: String) = Unit
        override suspend fun confirmLogin(challenge: CredoLoginChallenge, username: String, otp: String?): CredoSession = error("unused")
        override suspend fun accounts(session: CredoSession) = listOf(remote)
        override suspend fun downloadStatement(session: CredoSession, account: CredoRemoteAccount, fromIso: String, toIso: String): ByteArray = error("API must not export")
        override suspend fun history(session: CredoSession, account: CredoRemoteAccount, from: LocalDate, to: LocalDate) = rows
    }
    private suspend fun sync(rows: List<StatementRow>) = CredoHistorySync(db).sync(gateway(rows), session, remote, day, day)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun firstLedgerRequestsAutomaticStatementSeed() = runBlocking {
        assertNull(sync(listOf(row("a"))))
        assertTrue(db.accountDao().allActive().isEmpty())
    }
    @Test fun fileThenApiKeepsMoneyBalanceAndIdentityAcrossRepeatedImports() = runBlocking {
        file()
        val before = db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single()
        assertEquals(1, sync(listOf(row("a")))!!.reconciled)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
        assertTrue(file().isNoOp)
        val after = db.transactionDao().byId(before.id)!!
        assertEquals(9500L, after.balanceAfterMinor)
        assertTrue(CredoRowIdentity.hasMobile(after.externalKey, CredoRowIdentity.mobileId("a")))
        assertEquals(9500L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
    @Test fun apiOnlyMovementIsUpgradedByLaterFileWithoutDuplicating() = runBlocking {
        file(emptyList())
        assertEquals(1, sync(listOf(row("a")))!!.inserted)
        val id = db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single().id
        assertEquals(1, file().reconciled)
        assertEquals(id, db.transactionDao().allStatementRows(db.accountDao().allActive().single().id).single().id)
        assertTrue(sync(listOf(row("a")))!!.isNoOp)
    }
    @Test fun ambiguousTwinAndChangedFileBackedAmountAreAtomic() = runBlocking {
        file()
        assertTrue(runCatching { sync(listOf(row("a"), row("b"))) }.exceptionOrNull() is InvalidStatementException)
        assertFalse(db.transactionDao().allForIntegrity().any { "|credoapi|" in it.externalKey.orEmpty() })
        sync(listOf(row("a")))
        assertTrue(runCatching { sync(listOf(row("a", -600))) }.exceptionOrNull() is InvalidStatementException)
        assertEquals(9500L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
    }
}
