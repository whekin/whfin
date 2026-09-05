package dev.whekin.whfin.data.crypto

import java.math.BigInteger

/** A confirmed token transfer. Identity includes the contract, never just its display ticker. */
data class CryptoTransfer(
    val txHash: String,
    val contractAddress: String,
    val fromAddress: String,
    val toAddress: String,
    val baseUnits: BigInteger,
    val occurredAt: Long,
)

interface CryptoHistoryProvider {
    /** Complete confirmed history for one supported token, or an explicit failure. */
    suspend fun history(request: CryptoBalanceRequest): List<CryptoTransfer>
}
