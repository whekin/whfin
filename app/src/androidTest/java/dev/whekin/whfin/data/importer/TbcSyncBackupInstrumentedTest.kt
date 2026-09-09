package dev.whekin.whfin.data.importer

import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.tbc.*
import dev.whekin.whfin.data.backup.*
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TbcSyncBackupInstrumentedTest {
    @Test fun apiFileAliasesAndSyncOriginSurvivePortableBackup() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        val bytes = SyntheticTbcWorkbook.build()
        val statement = StatementParsers.parse(StatementFile("synthetic.xlsx", bytes))
        val remote = TbcLedgerAccount("10", statement.accountIban, statement.currency, "Everyday")
        val api = statement.rows.mapIndexed { i, row -> TbcHistoryRow("mobile-$i", "$i", row.copy(
            bankTransactionId = TbcRowIdentity.mobileId("mobile-$i"), balanceAfterMinor = null)) }
        val gateway = object : TbcGateway {
            override suspend fun login(username: String, credential: String): TbcLoginResult = error("unused")
            override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession = error("unused")
            override suspend fun resume(session: TbcSession): TbcSession = error("unused")
            override suspend fun accounts() = emptyList<TbcAccount>()
            override fun snapshot() = TbcSession(emptyMap(), "synthetic")
            override fun clear() = Unit
            override suspend fun ledgerAccounts() = listOf(remote)
            override suspend fun history(account: TbcLedgerAccount, from: LocalDate, through: LocalDate) = api
        }
        try {
            StatementImporter(db).import(bytes.inputStream())
            val sync = TbcHistorySync(db)
            assertEquals(4, sync.sync(gateway, LocalDate.of(2026, 9, 9)).matched)
            val keys = db.transactionDao().allForIntegrity().map { it.externalKey }.toSet()
            val output = ByteArrayOutputStream()
            val manager = WhfinBackupManager(db)
            manager.export(output, WhfinBackupMetadata(Instant.now(), "synthetic", "GEL"))
            manager.restore(output.toByteArray().inputStream())
            assertEquals(keys, db.transactionDao().allForIntegrity().map { it.externalKey }.toSet())
            assertEquals(1, sync.sync(gateway, LocalDate.of(2026, 9, 9)).unchanged)
            assertTrue(StatementImporter(db).preview(bytes.inputStream()).changesNothing)
            val account = db.accountDao().byIbanAndCurrency(remote.iban, "GEL")!!
            assertTrue(db.statementImportDao().forAccount(account.id).any { it.origin == StatementImportOrigin.TBC_SYNC })
        } finally { db.close() }
    }
}
