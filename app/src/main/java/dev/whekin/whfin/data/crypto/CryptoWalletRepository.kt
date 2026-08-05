package dev.whekin.whfin.data.crypto

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CryptoAssetEntity
import dev.whekin.whfin.data.db.CryptoBalanceEntity
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.WalletAddressEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigInteger
import kotlinx.coroutines.CancellationException

/**
 * A watch-only wallet is one address; its assets are read from the chain, not typed in.
 *
 * Everything here is a read plus a local write: the address is public, no key material is involved,
 * and no ledger is ever created for an asset the chain says is empty — an untouched USDC contract
 * would otherwise add a permanent zero row to every wallet.
 */
class CryptoWalletRepository(
    private val db: WhfinDatabase,
    private val provider: CryptoBalanceProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface AddResult {
        /**
         * The address is tracked. [funded] are the tickers a ledger now exists for, [failed] the
         * assets whose read did not answer, so the caller can say "added, but read nothing yet".
         */
        data class Tracked(
            val address: String,
            val network: CryptoNetwork,
            val funded: List<String>,
            val failed: Int,
            val alreadyTracked: Boolean,
        ) : AddResult

        data class InvalidAddress(val problem: CryptoAddressValidator.Problem) : AddResult

        data object UnsupportedNetwork : AddResult
    }

    /** New ledgers a discovery pass created, so a refresh can report what appeared by itself. */
    data class DiscoveryResult(val created: List<String> = emptyList(), val failed: Int = 0)

    /**
     * Adds (or completes) the wallet behind [rawAddress].
     *
     * Chain reads happen before the transaction: a database transaction must not wait on a network.
     * An address that is already tracked is not an error — it is a chance to pick up an asset that
     * arrived since, which is also why re-adding it can never hit the unique address×asset index.
     */
    suspend fun addWallet(
        name: String?,
        network: CryptoNetwork,
        rawAddress: String,
    ): AddResult {
        if (network !in provider.networks) return AddResult.UnsupportedNetwork
        val address = when (val checked = CryptoAddressValidator.check(network, rawAddress)) {
            is CryptoAddressValidator.Result.Valid -> checked.address
            is CryptoAddressValidator.Result.Invalid -> return AddResult.InvalidAddress(checked.problem)
        }
        val existing = db.cryptoDao().address(network.chainId, address)
        val readings = read(network, address)
        val walletName = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultWalletName(network, address)

        // Nothing readable and nothing tracked yet: keep the address the person typed and let the
        // native ledger stand for it, unread, instead of silently dropping the whole wallet.
        val fallback = existing == null && readings.none { it.baseUnits != null }

        val funded = mutableListOf<String>()
        db.withTransaction {
            val groupId = existing?.groupId ?: db.financialGroupDao().insert(
                FinancialGroupEntity(
                    name = walletName,
                    type = FinancialGroupType.WALLET,
                    provider = network.chainId,
                ),
            )
            val addressId = existing?.id ?: db.cryptoDao().insertAddress(
                WalletAddressEntity(groupId = groupId, chainId = network.chainId, address = address),
            )
            val tracked = trackedAssets(addressId)
            readings.forEach { reading ->
                val asset = reading.asset
                val held = reading.baseUnits != null && reading.baseUnits.signum() != 0
                val keep = held || (fallback && asset.contractAddress == null)
                val accountId = tracked[asset.contractAddress]
                    ?: if (keep) {
                        createLedger(groupId, walletName, addressId, network, asset)
                    } else {
                        return@forEach
                    }
                if (reading.baseUnits != null) {
                    observe(accountId, reading.baseUnits, asset.decimals, reading.source)
                }
                if (held) funded += asset.symbol
            }
        }
        return AddResult.Tracked(
            address = address,
            network = network,
            funded = funded.distinct(),
            failed = readings.count { it.baseUnits == null },
            alreadyTracked = existing != null,
        )
    }

    /**
     * Looks for assets that arrived after a wallet was added.
     *
     * Only assets without a ledger are read here; the ones that already have one are the plain
     * refresh path, so a discovery pass never asks the same question twice.
     */
    suspend fun discoverNewAssets(): DiscoveryResult {
        val created = mutableListOf<String>()
        var failed = 0
        db.cryptoDao().allAddresses().forEach { row ->
            val network = CryptoNetwork.byChainId(row.chainId)?.takeIf { it in provider.networks }
                ?: return@forEach
            val tracked = trackedAssets(row.id)
            val unknown = network.assets.filter { it.contractAddress !in tracked.keys }
            if (unknown.isEmpty()) return@forEach
            val group = db.financialGroupDao().byId(row.groupId)
            val walletName = group?.name ?: defaultWalletName(network, row.address)
            unknown.forEach { asset ->
                val reading = try {
                    provider.balance(CryptoBalanceRequest(network, row.address, asset))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failed++
                    return@forEach
                }
                if (reading.baseUnits.signum() == 0) return@forEach
                db.withTransaction {
                    val accountId = createLedger(row.groupId, walletName, row.id, network, asset)
                    observe(accountId, reading.baseUnits, asset.decimals, reading.source)
                }
                created += asset.symbol
            }
        }
        return DiscoveryResult(created.distinct(), failed)
    }

    private suspend fun read(
        network: CryptoNetwork,
        address: String,
    ): List<Reading> = network.assets.map { asset ->
        val reading = try {
            provider.balance(CryptoBalanceRequest(network, address, asset))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        Reading(asset, reading?.baseUnits, reading?.source)
    }

    /** Contract address (null for the native coin) to the ledger already tracking it. */
    private suspend fun trackedAssets(addressId: Long): Map<String?, Long> =
        db.accountDao().byWalletAddress(addressId).mapNotNull { account ->
            val assetId = account.cryptoAssetId ?: return@mapNotNull null
            val asset = db.cryptoDao().assetById(assetId) ?: return@mapNotNull null
            asset.contractAddress to account.id
        }.toMap()

    private suspend fun createLedger(
        groupId: Long,
        walletName: String,
        addressId: Long,
        network: CryptoNetwork,
        asset: CryptoAssetSpec,
    ): Long {
        db.cryptoDao().insertAsset(
            CryptoAssetEntity(
                chainId = network.chainId,
                contractAddress = asset.contractAddress,
                symbol = asset.symbol,
                name = asset.name,
                decimals = asset.decimals,
            ),
        )
        val assetId = requireNotNull(db.cryptoDao().asset(network.chainId, asset.contractAddress)).id
        return db.accountDao().insert(
            AccountEntity(
                name = walletName,
                type = AccountType.CRYPTO,
                currency = asset.symbol,
                groupId = groupId,
                walletAddressId = addressId,
                cryptoAssetId = assetId,
            ),
        )
    }

    private suspend fun observe(
        accountId: Long,
        baseUnits: BigInteger,
        decimals: Int,
        source: String?,
    ) {
        db.cryptoDao().upsertBalance(
            CryptoBalanceEntity(
                accountId = accountId,
                baseUnits = baseUnits.toString(),
                decimals = decimals,
                observedAt = now(),
                source = source,
            ),
        )
    }

    private data class Reading(
        val asset: CryptoAssetSpec,
        val baseUnits: BigInteger?,
        val source: String?,
    )

    private companion object {
        fun defaultWalletName(network: CryptoNetwork, address: String): String =
            "${network.displayName} ${address.takeLast(4)}"
    }
}
