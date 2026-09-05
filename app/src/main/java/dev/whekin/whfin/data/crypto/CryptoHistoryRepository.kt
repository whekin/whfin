package dev.whekin.whfin.data.crypto

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** Imports confirmed token movements; balances continue to come exclusively from chain snapshots. */
class CryptoHistoryRepository(
    private val db: WhfinDatabase,
    private val provider: CryptoHistoryProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Result(val imported: Int = 0, val failed: Int = 0, val unsupported: Int = 0)

    suspend fun refreshAll(): Result {
        var imported = 0
        var failed = 0
        var unsupported = 0
        val addresses = db.cryptoDao().allAddresses()
        for (address in addresses) {
            if (db.financialGroupDao().byId(address.groupId)?.isArchived != false) continue
            val network = CryptoNetwork.byChainId(address.chainId)
            if (network != CryptoNetwork.TRON) {
                unsupported++
                continue
            }
            // Even a fully withdrawn token has history: a zero balance is not a reason to skip it.
            for (asset in network.assets.filter { it.contractAddress != null }) {
                val existing = db.accountDao().byWalletAddress(address.id).firstOrNull { account ->
                    account.cryptoAssetId?.let { db.cryptoDao().assetById(it)?.contractAddress } == asset.contractAddress
                }
                if (existing?.isArchived == true) continue
                try {
                    val request = CryptoBalanceRequest(network, address.address, asset)
                    val history = provider.history(request)
                    imported += apply(address, asset, history)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed++
                }
            }
        }
        return Result(imported, failed, unsupported)
    }

    private suspend fun apply(
        address: WalletAddressEntity,
        asset: CryptoAssetSpec,
        history: List<CryptoTransfer>,
    ): Int = db.withTransaction {
        if (history.isEmpty()) return@withTransaction 0
        val group = db.financialGroupDao().byId(address.groupId) ?: return@withTransaction 0
        if (group.isArchived) return@withTransaction 0
        db.cryptoDao().insertAsset(CryptoAssetEntity(
            chainId = address.chainId, contractAddress = asset.contractAddress,
            symbol = asset.symbol, name = asset.name, decimals = asset.decimals,
        ))
        val assetId = requireNotNull(db.cryptoDao().asset(address.chainId, asset.contractAddress)).id
        val existing = db.accountDao().byWalletAddress(address.id).firstOrNull { it.cryptoAssetId == assetId }
        if (existing?.isArchived == true) return@withTransaction 0
        val accountId = existing?.id ?: db.accountDao().insert(AccountEntity(
            name = group.name, type = AccountType.CRYPTO, groupId = group.id,
            currency = asset.symbol, walletAddressId = address.id, cryptoAssetId = assetId,
        ))
        val ownAddresses = db.cryptoDao().allAddresses().filter { it.chainId == address.chainId }
            .mapTo(mutableSetOf()) { it.address }
        var inserted = 0
        // A chain transaction may emit several Transfer events. Store its net movement on this
        // asset ledger once, rather than dropping events that share a hash or inventing event ids.
        history.groupBy { it.txHash }.forEach { (hash, events) ->
            require(events.all { it.contractAddress == asset.contractAddress &&
                (it.fromAddress == address.address || it.toAddress == address.address) })
            val key = "crypto:${address.chainId}:${address.address}:${asset.contractAddress}:$hash"
            val counterparties = events.map { if (it.fromAddress == address.address) it.toAddress else it.fromAddress }.distinct()
            val ownMovement = counterparties.all { it in ownAddresses }
            val recorded = db.transactionDao().byExternalKey(key)
            if (recorded != null) {
                if (ownMovement && !recorded.isTransfer && !recorded.isVoided && recorded.transferGroupId == null &&
                    db.transactionAllocationDao().forTransaction(recorded.id).isEmpty()
                ) db.transactionDao().update(recorded.copy(isTransfer = true))
                return@forEach
            }
            val units = events.fold(BigInteger.ZERO) { sum, event ->
                sum + (if (event.toAddress == address.address) event.baseUnits else BigInteger.ZERO) -
                    (if (event.fromAddress == address.address) event.baseUnits else BigInteger.ZERO)
            }
            val amount = cryptoMinor(units, asset.decimals)
            if (amount == 0L) return@forEach
            val merchant = counterparties.singleOrNull()?.takeUnless { ownMovement }?.let { counterparty ->
                // Base58 is case-sensitive; normalizing an address like a shop name corrupts identity.
                val digest = MessageDigest.getInstance("SHA-256").digest(counterparty.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val merchantKey = "crypto:${address.chainId}:$digest"
                db.merchantDao().byKey(merchantKey) ?: run {
                    db.merchantDao().insert(MerchantEntity(
                        normalizedKey = merchantKey,
                        displayName = counterparty.take(6) + "…" + counterparty.takeLast(4),
                    ))
                    db.merchantDao().byKey(merchantKey)
                }
            }
            db.transactionDao().insert(TransactionEntity(
                accountId = accountId, amountMinor = amount, currency = asset.symbol,
                occurredAt = events.minOf { it.occurredAt }, status = TxStatus.CONFIRMED,
                source = TxSource.CRYPTO, externalKey = key,
                rawCounterparty = counterparties.singleOrNull(),
                merchantId = merchant?.id, categoryId = merchant?.categoryId,
                isTransfer = ownMovement, createdAt = now(),
            ))
            inserted++
        }
        inserted
    }
}

/** Truncate sub-cent dust, but never silently saturate a value too large for the ledger. */
internal fun cryptoMinor(units: BigInteger, decimals: Int): Long {
    require(decimals in 0..36)
    return if (decimals >= 2) (units / BigInteger.TEN.pow(decimals - 2)).longValueExact()
    else (units * BigInteger.TEN.pow(2 - decimals)).longValueExact()
}
