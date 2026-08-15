package dev.whekin.whfin.data.crypto

import java.math.BigInteger

/**
 * One payment that arrived at a watched address.
 *
 * Read-only, like everything else in this package: the chain is asked what it already published,
 * nothing is signed and no key exists anywhere in this path.
 */
data class CryptoIncomingTransfer(
    /** Chain-unique id of the transaction; the only thing that makes reading twice idempotent. */
    val txHash: String,
    val symbol: String,
    val contractAddress: String?,
    val baseUnits: BigInteger,
    val decimals: Int,
    val fromAddress: String,
    val occurredAt: Long,
)

/**
 * Payments *into* a watched address.
 *
 * Balances answer "how much is there"; this answers "when did money arrive and how much" — the only
 * question that can date income at the moment it was earned rather than at the moment it was later
 * moved, converted or carried to a bank counter.
 */
interface CryptoTransferProvider {
    val networks: Set<CryptoNetwork>

    /**
     * Transfers received at [address] since [sinceMillis], newest first.
     *
     * Refuses rather than returns nothing for a chain it cannot read: an empty list would be
     * indistinguishable from "you were never paid", which is the one wrong answer here.
     */
    suspend fun incoming(
        network: CryptoNetwork,
        address: String,
        sinceMillis: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<CryptoIncomingTransfer>

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
