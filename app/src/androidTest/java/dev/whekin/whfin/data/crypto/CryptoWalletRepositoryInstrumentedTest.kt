package dev.whekin.whfin.data.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountType
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
 * Address-first wallets against a scripted chain.
 *
 * The rules worth pinning: only funded assets become ledgers, a re-added address completes the same
 * wallet instead of hitting the unique address×asset index, and an asset that arrives later is found
 * by a discovery pass rather than by asking the person which tokens they own.
 */
@RunWith(AndroidJUnit4::class)
class CryptoWalletRepositoryInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private lateinit var repository: CryptoWalletRepository

    /** Base units per (address, ticker); absent means the read fails. */
    private val readings = mutableMapOf<Pair<String, String>, BigInteger>()
    private var calls = 0

    private val provider = object : CryptoBalanceProvider {
        override val networks = setOf(CryptoNetwork.ETHEREUM, CryptoNetwork.TRON)
        override suspend fun balance(request: CryptoBalanceRequest): CryptoBalanceReading {
            calls++
            val value = readings[request.address to request.asset.symbol]
                ?: throw CryptoBalanceException(CryptoBalanceException.Kind.UNREACHABLE, "no script")
            return CryptoBalanceReading(value, request.asset.decimals, source = "fake.test")
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        repository = CryptoWalletRepository(db, provider, now = { 1_000L })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun cryptoAccounts() =
        db.accountDao().allActive().filter { it.type == AccountType.CRYPTO }

    private fun fund(address: String, symbol: String, baseUnits: String) {
        readings[address to symbol] = BigInteger(baseUnits)
    }

    @Test
    fun addingAnAddress_createsLedgersOnlyForTheAssetsItHolds() = runBlocking {
        fund(TRON, "TRX", "512400000")
        fund(TRON, "USDT", "1200000000")
        fund(TRON, "USDC", "0")

        val result = repository.addWallet("Tron daily", CryptoNetwork.TRON, TRON)

        result as CryptoWalletRepository.AddResult.Tracked
        assertEquals(listOf("TRX", "USDT"), result.funded)
        assertEquals(0, result.failed)
        val accounts = cryptoAccounts()
        // An empty USDC contract must not leave a permanent zero row behind.
        assertEquals(setOf("TRX", "USDT"), accounts.map { it.currency }.toSet())
        assertEquals(listOf("Tron daily", "Tron daily"), accounts.map { it.name })
        val usdt = accounts.single { it.currency == "USDT" }
        assertEquals("1200000000", db.cryptoDao().balance(usdt.id)?.baseUnits)
        assertEquals(1_000L, db.cryptoDao().balance(usdt.id)?.observedAt)
    }

    @Test
    fun readdingTheSameAddress_completesTheWalletInsteadOfBreakingTheUniqueIndex() = runBlocking {
        fund(TRON, "TRX", "1000000")
        fund(TRON, "USDT", "0")
        fund(TRON, "USDC", "0")
        repository.addWallet(null, CryptoNetwork.TRON, TRON)

        // The same address, now holding USDT too: the second pass must not insert a duplicate TRX.
        fund(TRON, "USDT", "5000000")
        val result = repository.addWallet(null, CryptoNetwork.TRON, TRON)

        result as CryptoWalletRepository.AddResult.Tracked
        assertTrue(result.alreadyTracked)
        assertEquals(setOf("TRX", "USDT"), cryptoAccounts().map { it.currency }.toSet())
        assertEquals(2, cryptoAccounts().size)
        assertEquals(1, db.cryptoDao().allAddresses().size)
        assertEquals(1, db.financialGroupDao().observeActive().first().size)
    }

    @Test
    fun anAssetThatArrivesLater_isFoundByDiscoveryWithoutAsking() = runBlocking {
        fund(TRON, "TRX", "1000000")
        fund(TRON, "USDT", "0")
        fund(TRON, "USDC", "0")
        repository.addWallet(null, CryptoNetwork.TRON, TRON)
        val before = calls

        fund(TRON, "USDT", "42000000")
        val discovery = repository.discoverNewAssets()

        assertEquals(listOf("USDT"), discovery.created)
        assertEquals(0, discovery.failed)
        // Only the assets without a ledger are asked about, so TRX is not re-read here.
        assertEquals(2, calls - before)
        val usdt = cryptoAccounts().single { it.currency == "USDT" }
        assertEquals("42000000", db.cryptoDao().balance(usdt.id)?.baseUnits)
    }

    @Test
    fun anAddressOnTheWrongChain_writesNothing() = runBlocking {
        val result = repository.addWallet("Wallet", CryptoNetwork.ETHEREUM, TRON)

        assertTrue(result is CryptoWalletRepository.AddResult.InvalidAddress)
        assertEquals(0, calls)
        assertTrue(cryptoAccounts().isEmpty())
        assertTrue(db.cryptoDao().allAddresses().isEmpty())
    }

    @Test
    fun whenNothingCanBeRead_theAddressIsKeptAsAnUnreadNativeLedger() = runBlocking {
        val result = repository.addWallet("Cold", CryptoNetwork.ETHEREUM, EVM)

        result as CryptoWalletRepository.AddResult.Tracked
        assertTrue(result.funded.isEmpty())
        assertEquals(CryptoNetwork.ETHEREUM.assets.size, result.failed)
        // The typed address survives a bad endpoint; the balance stays unread rather than zero.
        val account = cryptoAccounts().single()
        assertEquals("ETH", account.currency)
        assertNull(db.cryptoDao().balance(account.id))
        assertNotNull(db.cryptoDao().address(CryptoNetwork.ETHEREUM.chainId, EVM))
    }

    @Test
    fun aFundedAssetOnTwoAddresses_staysTwoLedgersOfOneTicker() = runBlocking {
        fund(TRON, "TRX", "0")
        fund(TRON, "USDT", "1000000")
        fund(TRON, "USDC", "0")
        fund(EVM, "ETH", "0")
        fund(EVM, "USDT", "2000000")
        fund(EVM, "USDC", "0")

        repository.addWallet("Tron", CryptoNetwork.TRON, TRON)
        repository.addWallet("Cold", CryptoNetwork.ETHEREUM, EVM)

        val usdt = cryptoAccounts().filter { it.currency == "USDT" }
        assertEquals(2, usdt.size)
        // Same ticker, different contracts: separate balances by design.
        assertEquals(2, usdt.mapNotNull { it.cryptoAssetId }.distinct().size)
        assertEquals(2, usdt.mapNotNull { it.walletAddressId }.distinct().size)
    }

    private companion object {
        const val TRON = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        const val EVM = "0x00112233445566778899aabbccddeeff00112233"
    }
}
