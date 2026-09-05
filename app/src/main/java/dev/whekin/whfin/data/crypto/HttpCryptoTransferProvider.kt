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
) : CryptoTransferProvider, CryptoHistoryProvider {

    override suspend fun history(request: CryptoBalanceRequest): List<CryptoTransfer> {
        val contract = request.asset.contractAddress
        if (request.network != CryptoNetwork.TRON || contract == null ||
            request.network.assets.none { it == request.asset }
        ) throw CryptoBalanceException(CryptoBalanceException.Kind.UNSUPPORTED, "Token history unavailable")
        val endpoint = endpoints().urlFor(request.network).trim()
        if (!CryptoEndpoints.isUsable(endpoint)) {
            throw CryptoBalanceException(CryptoBalanceException.Kind.NOT_CONFIGURED, "Endpoint not configured")
        }
        val base = endpoint.trimEnd('/') + "/v1/accounts/${encode(request.address)}/transactions/trc20" +
            "?only_confirmed=true&limit=200&order_by=block_timestamp,desc&contract_address=${encode(contract)}" +
            "&max_timestamp=${System.currentTimeMillis()}"
        val result = mutableListOf<CryptoTransfer>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        // No partial ledger import: a refusal, malformed page or loop fails the whole read.
        repeat(100) {
            val response = JSONObject(transport.get(base + (cursor?.let { "&fingerprint=${encode(it)}" } ?: "")))
            if (!response.optBoolean("success", false)) historyRejected()
            val rows = response.optJSONArray("data") ?: historyRejected()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (!row.optString("type").equals("Transfer", ignoreCase = true)) continue
                val token = row.optJSONObject("token_info") ?: historyRejected()
                if (token.optString("address") != contract) continue
                if (token.optInt("decimals", -1) != request.asset.decimals) historyRejected()
                val from = row.optString("from").ifBlank { historyRejected() }
                val to = row.optString("to").ifBlank { historyRejected() }
                if (from != request.address && to != request.address) continue
                val units = row.optString("value").toBigIntegerOrNull() ?: historyRejected()
                if (units.signum() < 0) historyRejected()
                if (units.signum() == 0 || from == to) continue
                result += CryptoTransfer(
                    txHash = row.optString("transaction_id").ifBlank { historyRejected() },
                    contractAddress = contract,
                    fromAddress = from,
                    toAddress = to,
                    baseUnits = units,
                    occurredAt = row.optLong("block_timestamp").takeIf { it > 0 } ?: historyRejected(),
                )
            }
            cursor = response.optJSONObject("meta")?.optString("fingerprint")?.takeIf { it.isNotBlank() }
            if (cursor == null) return result
            if (!cursors.add(cursor)) historyRejected()
        }
        historyRejected()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun historyRejected(): Nothing =
        throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "Incomplete token history")

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
