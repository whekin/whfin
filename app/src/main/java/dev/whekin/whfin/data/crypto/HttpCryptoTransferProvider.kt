package dev.whekin.whfin.data.crypto

import java.math.BigInteger
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Incoming transfers over the same public endpoints the balance reads use.
 *
 * Only Tron is supported, and the reason is worth recording rather than discovering again. Tron
 * publishes an address's token transfers as one query, so a month of pay is a single request.
 * Ethereum has no such endpoint: `eth_getLogs` over an address's whole history is an archive query,
 * and every free public node either refuses it outright, caps the block range far below a useful
 * window, or demands an account. Reading ERC-20 income therefore needs an indexer with a key, which
 * is a different kind of promise than "public endpoints, no account", and it is not made here.
 *
 * The distinction matters because the honest failure is loud: a chain we cannot read raises, so it
 * can never be mistaken for a month in which nothing was earned.
 */
class HttpCryptoTransferProvider(
    private val endpoints: () -> CryptoEndpoints,
    private val transport: CryptoHttpTransport = UrlConnectionCryptoTransport(),
) : CryptoTransferProvider {

    override val networks: Set<CryptoNetwork> = setOf(CryptoNetwork.TRON)

    override suspend fun incoming(
        network: CryptoNetwork,
        address: String,
        sinceMillis: Long,
        limit: Int,
    ): List<CryptoIncomingTransfer> {
        if (network !in networks) {
            throw CryptoBalanceException(
                CryptoBalanceException.Kind.UNSUPPORTED,
                "incoming transfers are not readable on ${network.chainId}",
            )
        }
        val url = endpoints().urlFor(network).trim()
        if (!CryptoEndpoints.isUsable(url)) {
            throw CryptoBalanceException(
                CryptoBalanceException.Kind.NOT_CONFIGURED,
                "endpoint not configured",
            )
        }
        val encoded = URLEncoder.encode(address, "UTF-8")
        val query = buildString {
            append(url.trimEnd('/'))
            append("/v1/accounts/").append(encoded).append("/transactions/trc20")
            append("?only_to=true&limit=").append(limit.coerceIn(1, 200))
            append("&order_by=block_timestamp,desc")
            if (sinceMillis > 0) append("&min_timestamp=").append(sinceMillis)
        }
        val response = JSONObject(transport.get(query))
        if (!response.optBoolean("success", false)) {
            throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "transfer list refused")
        }
        val data = response.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val row = data.optJSONObject(index) ?: return@mapNotNull null
            // A contract can emit anything; only a plain transfer is a payment received.
            if (!row.optString("type").equals("Transfer", ignoreCase = true)) return@mapNotNull null
            val token = row.optJSONObject("token_info") ?: return@mapNotNull null
            val value = runCatching { BigInteger(row.optString("value")) }.getOrNull()
                ?: return@mapNotNull null
            if (value.signum() <= 0) return@mapNotNull null
            CryptoIncomingTransfer(
                txHash = row.optString("transaction_id").ifBlank { return@mapNotNull null },
                symbol = token.optString("symbol").ifBlank { return@mapNotNull null },
                contractAddress = token.optString("address").takeIf { it.isNotBlank() },
                baseUnits = value,
                decimals = token.optInt("decimals", 0),
                fromAddress = row.optString("from"),
                occurredAt = row.optLong("block_timestamp"),
            )
        }
    }
}
