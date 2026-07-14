package dev.whekin.whfin.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionBatchInstrumentedTest {
    private lateinit var db: WhfinDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun batchStatusAndDelete_includeBothTransferLegs() = runBlocking {
        val sourceId = db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"))
        val destinationId = db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "USD"))
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(type = TransferGroupType.CONVERSION, createdAt = 1_000),
        )
        val transferIds = db.transactionDao().insertAll(
            listOf(
                transaction(sourceId, -2_700, "GEL", TxStatus.MANUAL, groupId),
                transaction(destinationId, 1_000, "USD", TxStatus.MANUAL, groupId),
            ),
        )
        val pendingId = db.transactionDao().insert(transaction(sourceId, -500, "GEL", TxStatus.PENDING))

        db.transactionDao().updateStatus(listOf(pendingId), TxStatus.CONFIRMED)
        db.transactionDao().updateTransferGroupStatus(listOf(groupId), TxStatus.CONFIRMED)

        assertEquals(TxStatus.CONFIRMED, db.transactionDao().byId(pendingId)?.status)
        assertEquals(setOf(TxStatus.CONFIRMED), db.transactionDao().byTransferGroup(groupId).map { it.status }.toSet())

        db.transactionDao().deleteByIds(listOf(pendingId))
        db.transactionDao().deleteByTransferGroupIds(listOf(groupId))
        db.transactionDao().deleteTransferGroups(listOf(groupId))

        assertNull(db.transactionDao().byId(pendingId))
        transferIds.forEach { assertNull(db.transactionDao().byId(it)) }
    }

    private fun transaction(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        status: TxStatus,
        transferGroupId: Long? = null,
    ) = TransactionEntity(
        accountId = accountId,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = 1_000,
        status = status,
        source = if (status == TxStatus.PENDING) TxSource.SMS else TxSource.MANUAL,
        transferGroupId = transferGroupId,
        isTransfer = transferGroupId != null,
    )
}
