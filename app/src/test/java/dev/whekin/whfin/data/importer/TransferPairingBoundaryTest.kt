package dev.whekin.whfin.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferPairingBoundaryTest {
    private lateinit var db: WhfinDatabase
    private val zone = ZoneId.of("Asia/Tbilisi")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rebuildingNearAStatementBoundary_keepsBothExistingConversionLegsTogether() = runBlocking {
        val bankId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK),
        )
        val gel = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, groupId = bankId, currency = "GEL"),
        )
        val usd = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, groupId = bankId, currency = "USD"),
        )
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(type = TransferGroupType.CONVERSION, createdAt = 1),
        )
        val periodStart = LocalDate.of(2026, 8, 1)
        val windowStart = periodStart.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        listOf(
            TransactionEntity(
                accountId = gel,
                amountMinor = -270_000,
                currency = "GEL",
                occurredAt = windowStart,
                note = "Currency exchange",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                transferGroupId = groupId,
                isTransfer = true,
                externalKey = "gel-leg",
                createdAt = 1,
            ),
            TransactionEntity(
                accountId = usd,
                amountMinor = 100_000,
                currency = "USD",
                occurredAt = windowStart - 1,
                note = "Currency exchange",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                transferGroupId = groupId,
                isTransfer = true,
                externalKey = "usd-leg",
                createdAt = 1,
            ),
        ).forEach { db.transactionDao().insert(it) }
        assertTrue(DataIntegrityChecker(db).run().isHealthy)

        TransferPairing(db, zone).pairWithinPeriod(
            account = requireNotNull(db.accountDao().byId(gel)),
            from = periodStart,
            to = periodStart,
        )

        val report = DataIntegrityChecker(db).run()
        assertTrue(report.issues.joinToString { "${it.code}:${it.entityId}" }, report.isHealthy)
    }
}
