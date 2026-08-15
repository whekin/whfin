package dev.whekin.whfin.data.crypto

import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Minimal transport so the chain adapters can be tested against a local server. */
interface CryptoHttpTransport {
    fun post(url: String, body: String): String

    /** Some chain reads are plain queries; Tron lists an address's transfers over GET. */
    fun get(url: String): String
}

class UrlConnectionCryptoTransport : CryptoHttpTransport {
    override fun post(url: String, body: String): String = request(url, body)

    override fun get(url: String): String = request(url, body = null)

    private fun request(url: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = body != null
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
        }
        return try {
            body?.let { payload ->
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw CryptoBalanceException(
                    CryptoBalanceException.Kind.REJECTED,
                    "HTTP_$status",
                )
            }
            response
        } catch (error: CryptoBalanceException) {
            throw error
        } catch (error: IOException) {
            throw CryptoBalanceException(CryptoBalanceException.Kind.UNREACHABLE, "NETWORK_ERROR", error)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Watch-only balance reads over public endpoints.
 *
 * Only two calls exist per chain and both are read-only: a native balance and an ERC-20/TRC-20
 * `balanceOf`. There is no key material, no signing, and no write path anywhere in this class.
 */
class HttpCryptoBalanceProvider(
    private val endpoints: () -> CryptoEndpoints,
    private val transport: CryptoHttpTransport = UrlConnectionCryptoTransport(),
) : CryptoBalanceProvider {

    override val networks: Set<CryptoNetwork> = setOf(CryptoNetwork.ETHEREUM, CryptoNetwork.TRON)

    override suspend fun balance(request: CryptoBalanceRequest): CryptoBalanceReading {
        val url = endpoints().urlFor(request.network).trim()
        if (!CryptoEndpoints.isUsable(url)) {
            throw CryptoBalanceException(
                CryptoBalanceException.Kind.NOT_CONFIGURED,
                "endpoint not configured",
            )
        }
        val baseUnits = when (request.network) {
            CryptoNetwork.ETHEREUM -> evmBalance(url, request)
            CryptoNetwork.TRON -> tronBalance(url, request)
        }
        return CryptoBalanceReading(
            baseUnits = baseUnits,
            decimals = request.asset.decimals,
            source = runCatching { URL(url).host }.getOrNull(),
        )
    }

    private fun evmBalance(url: String, request: CryptoBalanceRequest): BigInteger {
        val contract = request.asset.contractAddress
        val payload = if (contract == null) {
            jsonRpc("eth_getBalance", JSONArray().put(request.address).put("latest"))
        } else {
            val call = JSONObject()
                .put("to", contract)
                .put("data", ERC20_BALANCE_OF + evmPadded(request.address))
            jsonRpc("eth_call", JSONArray().put(call).put("latest"))
        }
        val response = JSONObject(transport.post(url, payload))
        response.optJSONObject("error")?.let { error ->
            throw CryptoBalanceException(
                CryptoBalanceException.Kind.REJECTED,
                error.optString("message").ifBlank { "rpc error" },
            )
        }
        return hexToBigInteger(response.optString("result"))
    }

    private fun tronBalance(url: String, request: CryptoBalanceRequest): BigInteger {
        val base = url.trimEnd('/')
        val contract = request.asset.contractAddress
        if (contract == null) {
            val body = JSONObject()
                .put("address", request.address)
                .put("visible", true)
                .toString()
            val response = JSONObject(transport.post("$base/wallet/getaccount", body))
            // An address that never received anything answers with an empty object, not an error.
            return BigInteger.valueOf(response.optLong("balance", 0L))
        }
        val owner = CryptoAddressValidator.tronAddressHex(request.address)
            ?: throw CryptoBalanceException(CryptoBalanceException.Kind.UNSUPPORTED, "bad address")
        val body = JSONObject()
            .put("owner_address", request.address)
            .put("contract_address", contract)
            .put("function_selector", "balanceOf(address)")
            .put("parameter", owner.padStart(64, '0'))
            .put("visible", true)
            .toString()
        val response = JSONObject(transport.post("$base/wallet/triggerconstantcontract", body))
        val failure = response.optJSONObject("result")?.optString("message").orEmpty()
        if (failure.isNotEmpty()) {
            throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "contract call failed")
        }
        val result = response.optJSONArray("constant_result")?.optString(0)
            ?: throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "no result")
        return hexToBigInteger(result)
    }

    private fun jsonRpc(method: String, params: JSONArray): String = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", 1)
        .put("method", method)
        .put("params", params)
        .toString()

    private fun evmPadded(address: String): String =
        address.removePrefix("0x").removePrefix("0X").lowercase().padStart(64, '0')

    private fun hexToBigInteger(raw: String): BigInteger {
        val cleaned = raw.trim().removePrefix("0x").removePrefix("0X")
        if (cleaned.isEmpty()) {
            throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "empty result")
        }
        return runCatching { BigInteger(cleaned, 16) }.getOrElse {
            throw CryptoBalanceException(CryptoBalanceException.Kind.REJECTED, "unreadable result")
        }
    }

    private companion object {
        /** keccak256("balanceOf(address)")[0..3] — the standard ERC-20/TRC-20 selector. */
        const val ERC20_BALANCE_OF = "0x70a08231"
    }
}
