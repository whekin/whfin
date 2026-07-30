package dev.whekin.whfin.data.rates

import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Minimal GET transport so the rate adapters can be tested against a scripted server. */
interface RateHttpTransport {
    fun get(url: String): String
}

class UrlConnectionRateTransport : RateHttpTransport {
    override fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) throw RateException("HTTP_$status")
            response
        } catch (error: RateException) {
            throw error
        } catch (error: IOException) {
            throw RateException("NETWORK_ERROR", error)
        } finally {
            connection.disconnect()
        }
    }
}

class RateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One source of quotes. Each returns values already expressed in GEL per unit. */
interface RateProvider {
    suspend fun quotes(context: RateContext): List<ExchangeRate>
}

/**
 * What a provider may need from the ones before it. Crypto is priced in USD, so it can only be
 * expressed in GEL once the fiat provider has produced a USD quote.
 */
data class RateContext(
    val now: Long,
    val known: Map<String, ExchangeRate> = emptyMap(),
)

/**
 * Official National Bank of Georgia rates: the authoritative GEL quote, published once per banking
 * day. Every currency is requested, not just the ones held, so the request says nothing about the
 * contents of this ledger.
 */
class NbgFiatRateProvider(
    private val transport: RateHttpTransport = UrlConnectionRateTransport(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : RateProvider {

    override suspend fun quotes(context: RateContext): List<ExchangeRate> {
        val body = transport.get(endpoint)
        val root = runCatching { JSONArray(body) }.getOrElse { throw RateException("UNREADABLE") }
        val day = root.optJSONObject(0) ?: throw RateException("EMPTY")
        val validOn = day.optString("date").take(10).takeIf { it.isNotBlank() }
        val currencies = day.optJSONArray("currencies") ?: throw RateException("EMPTY")

        return (0 until currencies.length()).mapNotNull { index ->
            val item = currencies.optJSONObject(index) ?: return@mapNotNull null
            val code = item.optString("code").uppercase().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rate = item.optDouble("rate", Double.NaN)
            // NBG quotes some currencies per 100 or per 1000 units; ignoring quantity would make
            // the ruble a hundred times too expensive.
            val quantity = item.optInt("quantity", 1).takeIf { it > 0 } ?: 1
            if (rate.isNaN() || rate <= 0.0) return@mapNotNull null
            ExchangeRate(
                code = code,
                gelPerUnit = BigDecimal.valueOf(rate).divide(BigDecimal(quantity), PRECISION),
                observedAt = context.now,
                validOn = validOn,
                source = runCatching { URL(endpoint).host }.getOrNull(),
            )
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://nbg.gov.ge/gw/api/ct/monetarypolicy/currencies/en/json/"
        private val PRECISION = MathContext(24)
    }
}

/**
 * Market prices for the supported chain assets, in USD, converted through the pivot.
 *
 * The requested asset list is a build constant rather than the wallet contents, so the price lookup
 * does not reveal which assets this person actually holds.
 */
class CoinGeckoPriceProvider(
    private val transport: RateHttpTransport = UrlConnectionRateTransport(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : RateProvider {

    override suspend fun quotes(context: RateContext): List<ExchangeRate> {
        val gelPerUsd = context.known["USD"]
            ?: throw RateException("NO_USD_PIVOT")
        val url = "$endpoint?ids=${ASSETS.keys.joinToString(",")}&vs_currencies=usd"
        val root = runCatching { JSONObject(transport.get(url)) }
            .getOrElse { throw RateException("UNREADABLE") }

        return ASSETS.mapNotNull { (id, symbol) ->
            val usd = root.optJSONObject(id)?.optDouble("usd", Double.NaN) ?: return@mapNotNull null
            if (usd.isNaN() || usd <= 0.0) return@mapNotNull null
            ExchangeRate(
                code = symbol,
                gelPerUnit = BigDecimal.valueOf(usd).multiply(gelPerUsd.gelPerUnit, PRECISION),
                observedAt = context.now,
                source = runCatching { URL(endpoint).host }.getOrNull(),
            )
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.coingecko.com/api/v3/simple/price"

        /** Fixed set: every asset this build can track, held or not. */
        val ASSETS = linkedMapOf(
            "ethereum" to "ETH",
            "tron" to "TRX",
            "tether" to "USDT",
        )
        private val PRECISION = MathContext(24)
    }
}
