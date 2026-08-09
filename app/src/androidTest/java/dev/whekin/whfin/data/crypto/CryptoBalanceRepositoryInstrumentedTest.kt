package dev.whekin.whfin.data.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CryptoAssetEntity
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.WalletAddressEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Refresh behaviour against a fake chain: partial failures, idempotence, and the rule that a failed
 * read never overwrites a good observation with a blank one.
 */
@RunWith(AndroidJUnit4::class)
class CryptoBalanceRepositoryInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private var clock = 1_000L

    private val readings = mutableMapOf<String, BigInteger>()
    private val failures = mutableMapOf<String, CryptoBalanceException.Kind>()
    private var calls = 0

    private val provider = object : CryptoBalanceProvider {
        override val networks = setOf(CryptoNetwork.ETHEREUM, CryptoNetwork.TRON)
        override suspend fun balance(request: CryptoBalanceRequest): CryptoBalanceReading {
            calls++
            failures[request.address]?.let {
                throw CryptoBalanceException(it, "scripted failure")
            }
            return CryptoBalanceReading(
                baseUnits = readings.getValue(request.address),
                decimals = request.asset.decimals,
                source = "fake.test",
            )
        }
    }

    private lateinit var repository: CryptoBalanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        repository = CryptoBalanceRepository(db, provider, now = { clock })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createAccount(
        network: CryptoNetwork,
        address: String,
        symbol: String,
    ): Long {
        val asset = network.asset(symbol)!!
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Wallet $address",
                type = FinancialGroupType.WALLET,
                provider = network.chainId,
            ),
        )
        val addressId = db.cryptoDao().insertAddress(
            WalletAddressEntity(groupId = groupId, chainId = network.chainId, address = address),
        )
        db.cryptoDao().insertAsset(
            CryptoAssetEntity(
                chainId = network.chainId,
                contractAddress = asset.contractAddress,
                symbol = asset.symbol,
                name = asset.name,
                decimals = asset.decimals,
            ),
        )
        val assetId = db.cryptoDao().asset(network.chainId, asset.contractAddress)!!.id
        return db.accountDao().insert(
            AccountEntity(
                name = "Wallet $symbol",
                type = AccountType.CRYPTO,
                groupId = groupId,
                currency = asset.symbol,
                walletAddressId = addressId,
                cryptoAssetId = assetId,
            ),
        )
    }

    @Test
    fun refresh_storesExactBaseUnitsAndTheMomentTheyWereRead() = runBlocking {
        val accountId = createAccount(CryptoNetwork.TRON, TRON, "TRX")
        readings[TRON] = BigInteger("1500000")

        val result = repository.refreshAll()

        assertEquals(1, result.refreshed)
        assertEquals(0, result.failed)
        val stored = db.cryptoDao().balance(accountId)!!
        assertEquals("1500000", stored.baseUnits)
        assertEquals(6, stored.decimals)
        assertEquals(1_000L, stored.observedAt)
        assertEquals("fake.test", stored.source)
    }

    @Test
    fun repeatedRefresh_replacesTheObservationInsteadOfAppending() = runBlocking {
        val accountId = createAccount(CryptoNetwork.TRON, TRON, "TRX")
        readings[TRON] = BigInteger("1")
        repository.refreshAll()

        clock = 2_000L
        readings[TRON] = BigInteger("2")
        repository.refreshAll()

        val stored = db.cryptoDao().balance(accountId)!!
        assertEquals("2", stored.baseUnits)
        assertEquals(2_000L, stored.observedAt)
        assertEquals(1, db.cryptoDao().observeBalances().first().size)
    }

    @Test
    fun aFailingAddress_doesNotHideTheOthersAndKeepsItsLastGoodNumber() = runBlocking {
        val good = createAccount(CryptoNetwork.TRON, TRON, "TRX")
        val bad = createAccount(CryptoNetwork.ETHEREUM, EVM, "ETH")
        readings[TRON] = BigInteger("7")
        readings[EVM] = BigInteger("11")
        repository.refreshAll()

        clock = 3_000L
        readings[TRON] = BigInteger("8")
        failures[EVM] = CryptoBalanceException.Kind.UNREACHABLE
        val result = repository.refreshAll()

        assertEquals(1, result.refreshed)
        assertEquals(1, result.failed)
        assertEquals("8", db.cryptoDao().balance(good)!!.baseUnits)
        // The stale but true number survives; a failed read is not a zero balance.
        val staleRow = db.cryptoDao().balance(bad)!!
        assertEquals("11", staleRow.baseUnits)
        assertEquals(1_000L, staleRow.observedAt)
        assertEquals(
            CryptoBalanceException.Kind.UNREACHABLE,
            result.results.single { it.accountId == bad }.failure,
        )
    }

    @Test
    fun aChainThisBuildCannotRead_isReportedInsteadOfGuessed() = runBlocking {
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Old wallet", type = FinancialGroupType.WALLET, provider = "Trust Wallet"),
        )
        val addressId = db.cryptoDao().insertAddress(
            WalletAddressEntity(groupId = groupId, chainId = "bip122:bitcoin", address = "bc1qexample"),
        )
        db.cryptoDao().insertAsset(
            CryptoAssetEntity(chainId = "bip122:bitcoin", contractAddress = null, symbol = "BTC", name = "Bitcoin", decimals = 8),
        )
        val assetId = db.cryptoDao().asset("bip122:bitcoin", null)!!.id
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = "Legacy",
                type = AccountType.CRYPTO,
                groupId = groupId,
                currency = "BTC",
                walletAddressId = addressId,
                cryptoAssetId = assetId,
            ),
        )

        val result = repository.refreshAll()

        assertEquals(0, calls)
        assertEquals(0, result.refreshed)
        assertEquals(
            CryptoBalanceException.Kind.UNSUPPORTED,
            result.results.single { it.accountId == accountId }.failure,
        )
        assertNull(db.cryptoDao().balance(accountId))
    }

    @Test
    fun fiatLedgers_areNeverAskedFromTheChain() = runBlocking {
        db.accountDao().insert(
            AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"),
        )
        createAccount(CryptoNetwork.TRON, TRON, "TRX")
        readings[TRON] = BigInteger("5")

        val result = repository.refreshAll()

        assertEquals(1, result.results.size)
        assertEquals(1, calls)
        assertTrue(result.results.single().succeeded)
    }

    @Test
    fun deletingTheAccount_removesItsObservation() = runBlocking {
        val accountId = createAccount(CryptoNetwork.TRON, TRON, "TRX")
        readings[TRON] = BigInteger("9")
        repository.refreshAll()
        assertNotNull(db.cryptoDao().balance(accountId))

        db.openHelper.writableDatabase.execSQL("DELETE FROM accounts WHERE id = " + accountId)

        assertNull(db.cryptoDao().balance(accountId))
    }

    private companion object {
        const val TRON = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        const val EVM = "0x00112233445566778899aabbccddeeff00112233"
    }
}
