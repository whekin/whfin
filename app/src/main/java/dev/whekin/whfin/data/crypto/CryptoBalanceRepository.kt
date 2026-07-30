package dev.whekin.whfin.data.crypto

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CryptoBalanceEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.CancellationException

/**
 * Manual, foreground refresh of watch-only balances.
 *
 * One failing address must not hide the others, so every account is reported separately and a
 * failed read leaves the previous observation untouched rather than blanking the balance.
 */
class CryptoBalanceRepository(
    private val db: WhfinDatabase,
    private val provider: CryptoBalanceProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class AccountResult(
        val accountId: Long,
        val accountName: String,
        val reading: CryptoBalanceReading? = null,
        val failure: CryptoBalanceException.Kind? = null,
    ) {
        val succeeded: Boolean get() = reading != null
    }

    data class RefreshResult(val results: List<AccountResult>) {
        val refreshed: Int get() = results.count { it.succeeded }
        val failed: Int get() = results.size - refreshed
        val isEmpty: Boolean get() = results.isEmpty()
    }

    suspend fun refreshAll(): RefreshResult {
        val accounts = db.accountDao().allActive().filter { it.type == AccountType.CRYPTO }
        return RefreshResult(accounts.map { refresh(it) })
    }

    suspend fun refresh(accountId: Long): AccountResult? =
        db.accountDao().byId(accountId)?.takeIf { it.type == AccountType.CRYPTO }?.let { refresh(it) }

    private suspend fun refresh(account: AccountEntity): AccountResult {
        val request = requestFor(account)
            ?: return AccountResult(
                accountId = account.id,
                accountName = account.name,
                failure = CryptoBalanceException.Kind.UNSUPPORTED,
            )
        return try {
            val reading = provider.balance(request)
            db.cryptoDao().upsertBalance(
                CryptoBalanceEntity(
                    accountId = account.id,
                    baseUnits = reading.baseUnits.toString(),
                    decimals = reading.decimals,
                    observedAt = now(),
                    source = reading.source,
                ),
            )
            AccountResult(account.id, account.name, reading = reading)
        } catch (error: CryptoBalanceException) {
            AccountResult(account.id, account.name, failure = error.kind)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AccountResult(account.id, account.name, failure = CryptoBalanceException.Kind.UNREACHABLE)
        }
    }

    /**
     * An account created before explicit networks existed, or one pointing at a chain this build no
     * longer supports, is reported as unsupported instead of being guessed into a request.
     */
    private suspend fun requestFor(account: AccountEntity): CryptoBalanceRequest? {
        val address = account.walletAddressId?.let { db.cryptoDao().addressById(it) } ?: return null
        val assetRow = account.cryptoAssetId?.let { db.cryptoDao().assetById(it) } ?: return null
        val network = CryptoNetwork.byChainId(address.chainId) ?: return null
        if (network !in provider.networks) return null
        val asset = network.assets.firstOrNull { it.contractAddress == assetRow.contractAddress }
            ?: return null
        return CryptoBalanceRequest(network, address.address, asset)
    }
}
