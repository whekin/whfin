package dev.whekin.whfin.data.transfer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OwnTransfersTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = OwnTransferRepository(db)
    private val at = 1_780_000_000_000L
    private val day = 86_400_000L

    @After fun close() = db.close()

    /**
     * The intermediary is somebody else's business: the amounts, the currencies and the dates all
     * differ, and nothing in either row names the other. Only direction and ownership are required.
     */
    @Test fun `a wallet withdrawal and a bank credit days later are offered as one movement`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val credit = insert(bank, 731_000, "GEL", at + 2 * day, TxSource.STATEMENT)

        val offered = OwnTransfers.candidatesFor(out, listOf(out, credit), db.accountDao().allActive())

        assertEquals(listOf(credit.id), offered.map { it.transaction.id })
        assertEquals("Bank", offered.single().account?.name)
    }

    @Test fun `the same direction and the same account are never the other side`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val sameDirection = insert(bank, -100_000, "GEL", at, TxSource.STATEMENT)
        val sameAccount = insert(wallet, 100_000, "USDT", at, TxSource.CRYPTO)

        val offered = OwnTransfers.candidatesFor(
            out, listOf(out, sameDirection, sameAccount), db.accountDao().allActive(),
        )

        assertTrue(offered.isEmpty())
    }

    /** Money already claimed by a split or a debt has an owner; a movement must not steal it. */
    @Test fun `rows carrying an allocation are not offered`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val credit = insert(bank, 731_000, "GEL", at + day, TxSource.STATEMENT)
        db.transactionAllocationDao().insertAll(
            listOf(
                TransactionAllocationEntity(
                    transactionId = credit.id, amountMinor = 731_000, purpose = AllocationPurpose.LOAN,
                ),
            ),
        )

        val allocated = db.transactionAllocationDao().allForIntegrity().mapTo(mutableSetOf()) { it.transactionId }
        val offered = OwnTransfers.candidatesFor(out, listOf(out, credit), db.accountDao().allActive(), allocated)

        assertTrue(offered.isEmpty())
    }

    /** One withdrawal often comes back as several credits; the remainder must not read as income. */
    @Test fun `one withdrawal can be linked to several credits at once`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val cash = account("Cash", AccountType.CASH, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val first = insert(bank, 500_000, "GEL", at + day, TxSource.STATEMENT)
        val second = insert(cash, 231_000, "GEL", at + day, TxSource.MANUAL)

        val groupId = repository.link(listOf(out.id, first.id, second.id))

        assertEquals(3, db.transactionDao().byTransferGroup(groupId).size)
        assertEquals(TransferGroupType.OWN_LINK, db.transactionDao().transferGroupById(groupId)?.type)
        assertTrue(db.transactionDao().byTransferGroup(groupId).all { it.isTransfer })
    }

    /** Both sides keep their own money: linking explains rows, it never rewrites them. */
    @Test fun `linking changes neither amount nor currency nor provenance`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val credit = insert(bank, 731_000, "GEL", at + day, TxSource.STATEMENT)

        repository.link(listOf(out.id, credit.id))

        val savedOut = db.transactionDao().byId(out.id)!!
        val savedCredit = db.transactionDao().byId(credit.id)!!
        assertEquals(-270_000L, savedOut.amountMinor)
        assertEquals("USDT", savedOut.currency)
        assertEquals(TxSource.CRYPTO, savedOut.source)
        assertEquals(731_000L, savedCredit.amountMinor)
        assertEquals("GEL", savedCredit.currency)
        assertEquals(TxSource.STATEMENT, savedCredit.source)
    }

    @Test fun `unlinking returns both rows to ordinary money in and out`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val credit = insert(bank, 731_000, "GEL", at + day, TxSource.STATEMENT)
        val groupId = repository.link(listOf(out.id, credit.id))

        repository.unlink(groupId)

        assertFalse(db.transactionDao().byId(out.id)!!.isTransfer)
        assertNull(db.transactionDao().byId(credit.id)!!.transferGroupId)
        assertNull(db.transactionDao().transferGroupById(groupId))
    }

    /** Cash has no statement behind it, so the far side is written down and joined in one step. */
    @Test fun `a missing side can be recorded and is linked in the same breath`() = runBlocking {
        val bank = account("Bank", AccountType.BANK, "GEL")
        val cash = account("Cash", AccountType.CASH, "GEL")
        val out = insert(bank, -500_000, "GEL", at, TxSource.STATEMENT)

        val groupId = repository.linkToNewLeg(out.id, cash, 500_000, "GEL", at + day)

        val rows = db.transactionDao().byTransferGroup(groupId)
        assertEquals(2, rows.size)
        val recorded = rows.single { it.accountId == cash }
        assertEquals(500_000L, recorded.amountMinor)
        assertEquals(TxSource.MANUAL, recorded.source)
        assertEquals(TxStatus.MANUAL, recorded.status)
    }

    @Test fun `a recorded side facing the same way is refused`() = runBlocking {
        val bank = account("Bank", AccountType.BANK, "GEL")
        val cash = account("Cash", AccountType.CASH, "GEL")
        val out = insert(bank, -500_000, "GEL", at, TxSource.STATEMENT)

        val failure = runCatching { repository.linkToNewLeg(out.id, cash, -500_000, "GEL", at) }

        assertTrue(failure.isFailure)
        assertEquals(1, db.transactionDao().allForIntegrity().size)
    }

    /** A row already inside a movement has its answer; joining it again would be two answers. */
    @Test fun `a row already in a group cannot be linked again`() = runBlocking {
        val wallet = account("Wallet", AccountType.CRYPTO, "USDT")
        val bank = account("Bank", AccountType.BANK, "GEL")
        val cash = account("Cash", AccountType.CASH, "GEL")
        val out = insert(wallet, -270_000, "USDT", at, TxSource.CRYPTO)
        val credit = insert(bank, 731_000, "GEL", at + day, TxSource.STATEMENT)
        val other = insert(cash, 100_000, "GEL", at + day, TxSource.MANUAL)
        repository.link(listOf(out.id, credit.id))

        val failure = runCatching { repository.link(listOf(out.id, other.id)) }

        assertTrue(failure.isFailure)
    }

    /** Automatic pairing rebuilds derived groups; a hand-made one is never on that list. */
    @Test fun `a hand made link is not offered for automatic rebuilding`() = runBlocking {
        val groupId = db.financialGroupDao().insert(
            dev.whekin.whfin.data.db.FinancialGroupEntity(
                name = "Bank", type = dev.whekin.whfin.data.db.FinancialGroupType.BANK,
            ),
        )
        val one = account("One", AccountType.BANK, "GEL", groupId)
        val two = account("Two", AccountType.BANK, "GEL", groupId)
        val out = insert(one, -500_000, "GEL", at, TxSource.STATEMENT)
        val credit = insert(two, 500_000, "GEL", at, TxSource.STATEMENT)
        repository.link(listOf(out.id, credit.id))

        assertEquals(emptyList<Long>(), db.transactionDao().rebuildableTransferGroupIds(groupId))
    }

    private suspend fun account(
        name: String,
        type: AccountType,
        currency: String,
        groupId: Long? = null,
    ): Long = db.accountDao().insert(
        AccountEntity(name = name, type = type, currency = currency, groupId = groupId),
    )

    private suspend fun insert(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        source: TxSource,
    ): TransactionEntity {
        val id = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId, amountMinor = amountMinor, currency = currency,
                occurredAt = occurredAt, status = TxStatus.CONFIRMED, source = source,
            ),
        )
        return db.transactionDao().byId(id)!!
    }
}
