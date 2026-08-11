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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun repairAll_findsGroupedTransfersWhoseLabelsDoNotSayExchange() = runBlocking {
        val bankId = insertBank()
        val from = insertAccount(bankId, "From", "GEL", "GE00FROM")
        val to = insertAccount(bankId, "To", "GEL", "GE00TO")
        val firstGroup = insertGroup(TransferGroupType.CONVERSION)
        val secondGroup = insertGroup(TransferGroupType.CONVERSION)
        val outgoing = insertTransfer(from, -10_000, "Personal Transfer", "GE00TO", firstGroup, 1_000)
        val incoming = insertTransfer(to, 10_000, "Personal Transfer", "GE00FROM", secondGroup, 1_001)

        TransferPairing(db, zone).repairAll()

        val repairedOutgoing = requireNotNull(db.transactionDao().byId(outgoing))
        val repairedIncoming = requireNotNull(db.transactionDao().byId(incoming))
        assertEquals(repairedOutgoing.transferGroupId, repairedIncoming.transferGroupId)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun repairAll_rebuildsCrossedSameAmountTransfersUsingOwnedIbans() = runBlocking {
        val bankId = insertBank()
        val everyday = insertAccount(bankId, "Everyday", "GEL", "GE00EVERYDAY")
        val deposit = insertAccount(bankId, "Deposit", "GEL", "GE00DEPOSIT")
        val savings = insertAccount(bankId, "Savings", "GEL", "GE00SAVINGS")
        val wrongGroup = insertGroup(TransferGroupType.TRANSFER)

        val everydayIncoming = insertTransfer(
            everyday, 10_000, "Personal Transfer", "GE00DEPOSIT", wrongGroup, 2_000,
        )
        val depositToSavings = insertTransfer(
            deposit, -10_000, "Personal Transfer", "GE00SAVINGS", wrongGroup, 1_000,
        )
        val depositToEveryday = insertTransfer(
            deposit, -10_000, "Personal Transfer", "GE00EVERYDAY", null, 2_001,
        )
        val savingsIncoming = insertTransfer(
            savings, 10_000, "Personal Transfer", "GE00DEPOSIT", null, 1_001,
        )

        TransferPairing(db, zone).repairAll()

        val everydayGroup = requireNotNull(db.transactionDao().byId(everydayIncoming)?.transferGroupId)
        val savingsGroup = requireNotNull(db.transactionDao().byId(savingsIncoming)?.transferGroupId)
        assertEquals(everydayGroup, db.transactionDao().byId(depositToEveryday)?.transferGroupId)
        assertEquals(savingsGroup, db.transactionDao().byId(depositToSavings)?.transferGroupId)
        assertTrue(everydayGroup != savingsGroup)
    }

    @Test
    fun repairAll_detachesOpeningAnchorsInsteadOfPairingThem() = runBlocking {
        val bankId = insertBank()
        val account = insertAccount(bankId, "Everyday", "GEL", "GE00EVERYDAY")
        val groupId = insertGroup(TransferGroupType.TRANSFER)
        val anchorId = db.transactionDao().insert(
            TransactionEntity(
                accountId = account,
                amountMinor = 50_000,
                currency = "GEL",
                occurredAt = 1_000,
                note = "Opening balance",
                status = TxStatus.CONFIRMED,
                source = TxSource.ADJUSTMENT,
                transferGroupId = groupId,
                isTransfer = true,
                externalKey = "opening|GE00EVERYDAY|GEL|1000",
                createdAt = 1,
            ),
        )

        TransferPairing(db, zone).repairAll()

        assertNull(db.transactionDao().byId(anchorId)?.transferGroupId)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun repairAll_doesNotPairDifferentExplicitIbanWithSameDayExchange() = runBlocking {
        val bankId = insertBank()
        val gel = insertAccount(bankId, "Everyday", "GEL", "GE00EVERYDAY")
        val eur = insertAccount(bankId, "Everyday", "EUR", "GE00EVERYDAY")
        val staleGroup = insertGroup(TransferGroupType.CONVERSION)
        val piggyBankTopUp = insertTransfer(
            gel, -75, "Piggy-bank top-up", "GE00OLDDEPOSIT", staleGroup, 1_000,
        )
        val exchangeIncome = db.transactionDao().insert(
            TransactionEntity(
                accountId = eur,
                amountMinor = 18_815,
                currency = "EUR",
                occurredAt = 1_000,
                counterpartyIban = "GE00EVERYDAY",
                note = "Exchange amount",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                isTransfer = true,
                externalKey = "exchange-income",
                createdAt = 1,
            ),
        )

        TransferPairing(db, zone).repairAll()

        assertNull(db.transactionDao().byId(piggyBankTopUp)?.transferGroupId)
        assertNull(db.transactionDao().byId(exchangeIncome)?.transferGroupId)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun repairAllPreservesACompleteSmsConversionHypothesis() = runBlocking {
        val bankId = insertBank()
        val gel = insertAccount(bankId, "Everyday", "GEL", "GE00EVERYDAY")
        val usd = insertAccount(bankId, "Everyday", "USD", "GE00EVERYDAY")
        val smsGroup = insertGroup(TransferGroupType.CONVERSION)
        val outgoing = db.transactionDao().insert(
            TransactionEntity(
                accountId = gel,
                amountMinor = -5_000,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                transferGroupId = smsGroup,
                isTransfer = true,
                externalKey = "sms|exchange",
                createdAt = 1,
            ),
        )
        val incoming = db.transactionDao().insert(
            TransactionEntity(
                accountId = usd,
                amountMinor = 1_800,
                currency = "USD",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                transferGroupId = smsGroup,
                isTransfer = true,
                externalKey = "sms|exchange|to",
                createdAt = 1,
            ),
        )

        TransferPairing(db, zone).repairAll()

        assertEquals(smsGroup, db.transactionDao().byId(outgoing)?.transferGroupId)
        assertEquals(smsGroup, db.transactionDao().byId(incoming)?.transferGroupId)
        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    private suspend fun insertBank(): Long = db.financialGroupDao().insert(
        FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK),
    )

    private suspend fun insertAccount(bankId: Long, name: String, currency: String, iban: String): Long =
        db.accountDao().insert(
            AccountEntity(
                name = name,
                type = AccountType.BANK,
                groupId = bankId,
                currency = currency,
                iban = iban,
            ),
        )

    private suspend fun insertGroup(type: TransferGroupType): Long =
        db.transactionDao().insertTransferGroup(TransferGroupEntity(type = type, createdAt = 1))

    private suspend fun insertTransfer(
        accountId: Long,
        amountMinor: Long,
        note: String,
        counterpartyIban: String,
        transferGroupId: Long?,
        occurredAt: Long,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = amountMinor,
            currency = "GEL",
            occurredAt = occurredAt,
            counterpartyIban = counterpartyIban,
            note = note,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
            transferGroupId = transferGroupId,
            isTransfer = true,
            externalKey = "$accountId|$occurredAt|$amountMinor",
            createdAt = 1,
        ),
    )
}
