package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.tbc.TbcHold
import dev.whekin.whfin.data.backup.*
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TbcHoldBackupInstrumentedTest {
    @Test fun pendingAndSettledAliasesSurvivePortableRestore() = runBlocking {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WhfinDatabase::class.java).build()
        try {
            val account = AccountEntity(id = 1, name = "Synthetic TBC", type = AccountType.BANK, currency = "GEL", iban = "GE00TB0000000000000001")
            db.accountDao().insert(account)
            val hold = TbcHold("hold|tbc|synthetic", account.iban!!, "GEL", -1200, 1789045632000L, "EXAMPLE CAFE", "0001")
            TbcHoldImporter(db).apply(account,listOf(hold))
            val row = db.transactionDao().allForIntegrity().single()
            val manager = WhfinBackupManager(db)
            suspend fun roundTrip() {
                val out = ByteArrayOutputStream()
                manager.export(out,WhfinBackupMetadata(Instant.now(),"synthetic","GEL"))
                manager.restore(out.toByteArray().inputStream())
            }
            roundTrip()
            assertEquals(0,TbcHoldImporter(db).apply(account,listOf(hold)).inserted)
            db.transactionDao().update(row.copy(source = TxSource.STATEMENT,status = TxStatus.CONFIRMED,externalKey = "synthetic-booked"))
            roundTrip()
            assertEquals(0,TbcHoldImporter(db).apply(account,listOf(hold)).inserted)
            assertEquals(TxSource.STATEMENT,db.transactionDao().byId(row.id)!!.source)
            assertEquals(-1200L,db.transactionDao().sumByAccount(account.id))
            assertEquals(row.id,db.bankHoldDao().byKey(hold.key)!!.transactionId)
        } finally { db.close() }
    }
}
