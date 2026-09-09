package dev.whekin.whfin.data.tbc

import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpCookie
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Protocol errors are deliberately codes, never response bodies containing credentials or identity. */
class TbcException(val code: String) : Exception(code)
class TbcSession(val cookies: Map<String, String>, val deviceId: String) {
    override fun toString() = "TbcSession(redacted)"
    fun encode(): String = JSONObject().put("cookies", JSONObject(cookies)).put("deviceId", deviceId).toString()
    companion object {
        fun decode(text: String): TbcSession {
            val json = JSONObject(text)
            val cookies = json.getJSONObject("cookies")
            return TbcSession(cookies.keys().asSequence().associateWith { cookies.getString(it) }, json.getString("deviceId"))
        }
    }
}
class TbcChallenge(val transactionId: String, val otpId: String, val type: String) {
    override fun toString() = "TbcChallenge(redacted)"
}
sealed interface TbcLoginResult {
    class Challenge(val value: TbcChallenge) : TbcLoginResult
    class Authenticated(val session: TbcSession) : TbcLoginResult
}
data class TbcAccount(val id: Long?, val iban: String, val currency: String, val name: String)

interface TbcGateway {
    suspend fun login(username: String, credential: String): TbcLoginResult
    suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession
    suspend fun resume(session: TbcSession): TbcSession
    suspend fun ledgerAccounts(): List<TbcLedgerAccount> = throw TbcException("HISTORY_FORMAT")
    suspend fun history(account: TbcLedgerAccount, from: java.time.LocalDate, through: java.time.LocalDate): List<TbcHistoryRow> = throw TbcException("HISTORY_FORMAT")
    suspend fun accounts(): List<TbcAccount>
    fun clear()
    fun snapshot(): TbcSession
}
internal class TbcResponse(val status: Int, val body: String, val cookies: List<String> = emptyList()) {
    override fun toString() = "TbcResponse(status=$status)"
}
internal fun interface TbcTransport {
    fun exchange(url: String, method: String, headers: Map<String, String>, body: String?): TbcResponse
}
internal class HttpsTbcTransport : TbcTransport {
    override fun exchange(url: String, method: String, headers: Map<String, String>, body: String?): TbcResponse {
        val endpoint = URL(url)
        if (endpoint.protocol != "https" || endpoint.host !in HOSTS || endpoint.port !in listOf(-1, 443) || endpoint.userInfo != null) {
            throw TbcException("ENDPOINT")
        }
        val connection = (endpoint.openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            useCaches = false
            headers.forEach(::setRequestProperty)
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 2 * 1024 * 1024) throw TbcException("RESPONSE")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }.orEmpty()
            val cookies = connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value }
            return TbcResponse(status, response, cookies)
        } catch (error: TbcException) { throw error }
        catch (_: Exception) { throw TbcException("NETWORK") }
        finally { connection.disconnect() }
    }
    companion object { val HOSTS = setOf("rmbgwauth.tbconline.ge", "rmbgw.tbconline.ge") }
}

/** Independent Android implementation of the observed retail mobile protocol; no payment API. */
class MobileTbcGateway internal constructor(private val transport: TbcTransport) : TbcGateway {
    constructor() : this(HttpsTbcTransport())
    private var deviceId = UUID.randomUUID().toString().replace("-", "")
    private val cookies = linkedMapOf<String, String>()
    override fun clear() { cookies.clear() }
    override fun snapshot() = TbcSession(cookies.toMap(), deviceId)

    override suspend fun login(username: String, credential: String): TbcLoginResult = withContext(Dispatchers.IO) {
        clear()
        val info = JSONObject().put("appVersion", PROTOCOL_APP_VERSION).put("deviceId", deviceId)
            .put("manufacturer", Build.MANUFACTURER).put("modelNumber", Build.MODEL)
            .put("os", "Android ${Build.VERSION.RELEASE}").put("remembered", false).put("rooted", false)
        val data = JSONObject(info.toString()).put("isRemembered", "false").put("isRooted", "false")
            .put("operatingSystem", "Android").put("operatingSystemVersion", Build.VERSION.RELEASE)
        val result = request(AUTH, "/v1/auth/loginWithPassword", JSONObject()
            .put("username", username).put("password", credential).put("language", "en")
            .put("deviceId", deviceId).put("deviceInfo", encode(info)).put("deviceData", encode(data)))
        if (result.optBoolean("changePasswordRequired") || result.optBoolean("userSelectionRequired")) throw TbcException("BANK_ACTION")
        if (result.optBoolean("secondPhaseRequired") || result.optJSONArray("signatures")?.length()?.let { it > 0 } == true) {
            val signatures = result.optJSONArray("signatures") ?: JSONArray()
            val choices = (0 until signatures.length()).map { signatures.getJSONObject(it) }
            val chosen = choices.firstOrNull { it.optString("type") == "SMS_OTP" }
                ?: choices.firstOrNull { it.optString("type") == "TOKEN_VASCO" }
            val type = chosen?.optString("type") ?: result.optJSONArray("possibleChallengeRegenTypes")?.let { types ->
                (0 until types.length()).map { types.optString(it) }.firstOrNull { it in setOf("SMS_OTP", "TOKEN_VASCO") }
            } ?: throw TbcException("OTP_METHOD")
            TbcLoginResult.Challenge(TbcChallenge(required(result, "transactionId"), chosen?.optString("otpId")?.ifBlank { "NONE" } ?: "NONE", type))
        } else {
            if (!result.optBoolean("success")) throw TbcException("LOGIN")
            TbcLoginResult.Authenticated(verifySession())
        }
    }
    override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession = withContext(Dispatchers.IO) {
        val result = request(AUTH, "/v1/auth/certifyLogin", JSONObject()
            .put("transactionId", challenge.transactionId).put("language", "en")
            .put("signature", JSONObject().put("response", code).put("status", "CHALLENGE").put("type", challenge.type).put("otpId", challenge.otpId)))
        if (!result.optBoolean("success")) throw TbcException("OTP")
        verifySession()
    }
    override suspend fun resume(session: TbcSession): TbcSession = withContext(Dispatchers.IO) {
        clear()
        deviceId = session.deviceId
        cookies.putAll(session.cookies)
        verifySession()
    }
    private fun verifySession(): TbcSession {
        val user = request(AUTH, "/v2/usermanagement/userinfo")
        required(user, "sessionId")
        if (cookies.isEmpty()) throw TbcException("SESSION")
        return TbcSession(cookies.toMap(), deviceId)
    }
    override suspend fun accounts(): List<TbcAccount> = withContext(Dispatchers.IO) {
        val result = request(API, "/dashboard/api/v1/cards-and-accounts")
        val items = result.optJSONArray("accountsAndDebitCards") ?: throw TbcException("RESPONSE")
        (0 until items.length()).map { i ->
            val row = items.getJSONObject(i)
            val iban = required(row, "iban")
            val currency = required(row, "currency")
            if (!iban.matches(Regex("GE[0-9]{2}TB[0-9]{16}")) || !currency.matches(Regex("[A-Z]{3}"))) throw TbcException("RESPONSE")
            TbcAccount(row.optLong("id").takeIf { it > 0 }, iban, currency, row.optString("name"))
        }.distinctBy { it.iban to it.currency }
    }
    override suspend fun ledgerAccounts(): List<TbcLedgerAccount> = withContext(Dispatchers.IO) {
        try {
            val result = linkedMapOf<String, TbcLedgerAccount>()
            val products = JSONArray(requestText(API, "/products/api/v1/cards"))
            for (i in 0 until products.length()) {
                val product = products.getJSONObject(i)
                if (product.optBoolean("isChildCard") || product.optBoolean("isCreditCard")) continue
                val iban = required(product, "iban")
                val ledgers = product.getJSONArray("accounts")
                for (j in 0 until ledgers.length()) {
                    val ledger = ledgers.getJSONObject(j)
                    val item = TbcLedgerAccount(ledger.getLong("id").toString(), iban, required(ledger, "currency"),
                        product.optString("friendlyName").takeUnless { it == "null" }.orEmpty(),
                        ledger.optLong("coreAccountId").takeIf { it > 0 }?.toString(),
                        ledger.opt("balance")?.takeUnless { it == JSONObject.NULL }?.let { java.math.BigDecimal(it.toString()).movePointRight(2).longValueExact() })
                    if (!iban.matches(Regex("GE[0-9]{2}TB[0-9]{16}")) || !item.currency.matches(Regex("[A-Z]{3}"))) throw TbcException("HISTORY_ACCOUNT")
                    if (result.put(item.key, item) != null) throw TbcException("HISTORY_ACCOUNT")
                }
            }
            val dashboard = request(API, "/dashboard/api/v1/cards-and-accounts").getJSONArray("accountsAndDebitCards")
            for (i in 0 until dashboard.length()) {
                val item = dashboard.getJSONObject(i)
                if (item.optString("type") == "Card") continue
                val iban = required(item, "iban")
                val currency = required(item, "currency")
                if (!iban.matches(Regex("GE[0-9]{2}TB[0-9]{16}")) || !currency.matches(Regex("[A-Z]{3}"))) throw TbcException("HISTORY_ACCOUNT")
                val account = TbcLedgerAccount(item.optLong("id").takeIf { it > 0 }?.toString() ?: iban, iban, currency, item.optString("name"), balanceMinor =
                    item.opt("amount")?.takeUnless { it == JSONObject.NULL }?.let { java.math.BigDecimal(it.toString()).movePointRight(2).longValueExact() })
                result.putIfAbsent(account.key, account)
            }
            result.values.toList()
        } catch (e: TbcException) { throw e }
        catch (_: Exception) { throw TbcException("HISTORY_FORMAT") }
    }

    override suspend fun history(account: TbcLedgerAccount, from: java.time.LocalDate, through: java.time.LocalDate): List<TbcHistoryRow> = withContext(Dispatchers.IO) {
        try {
            val rows = linkedMapOf<String, TbcHistoryRow>()
            var cursor: Long? = null
            var blockedCursor: Long? = null
            var previousDay: java.time.LocalDate? = null
            val seenCursors = mutableSetOf<String>()
            repeat(1000) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val body = JSONObject().put("coreAccountIds", JSONArray().put(JSONObject()
                    .put("currency", account.currency).put("iban", account.iban).put("id", account.id).put("type", "200")))
                    .put("pageSize", 100).put("pageType", "History").put("isChildCardRequest", false).put("showBlockedTransactions", false)
                cursor?.let { body.put("lastSortColKey", it) }
                blockedCursor?.let { body.put("lastBlockedMovementDate", it) }
                val page = TbcHistoryParser.page(JSONArray(requestText(API, "/pfm/api/v1/transactions/history", body)), account)
                if (page.empty) return@withContext rows.values.filter { it.row.postedDate in from..through }
                page.rows.forEach { row ->
                    if (previousDay != null && row.row.postedDate > previousDay) throw TbcException("HISTORY_PAGE")
                    previousDay = row.row.postedDate
                    val existing = rows.putIfAbsent(row.movementId, row)
                    if (existing != null && existing != row) throw TbcException("HISTORY_CHANGED")
                    if (row.row.postedDate > through) throw TbcException("HISTORY_CHANGED")
                }
                // Repeated days are normal. Stop at a genuinely older day, not at a repeated one.
                if (page.rows.any { it.row.postedDate < from }) return@withContext rows.values.filter { it.row.postedDate in from..through }
                val next = page.nextCursor ?: cursor
                val nextBlocked = page.blockedCursor ?: blockedCursor
                if (next == null && nextBlocked == null) throw TbcException("HISTORY_PAGE")
                if (!seenCursors.add("$next|$nextBlocked")) throw TbcException("HISTORY_PAGE")
                cursor = next; blockedCursor = nextBlocked
            }
            throw TbcException("HISTORY_PAGE")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: TbcException) { throw e }
        catch (_: Exception) { throw TbcException("HISTORY_FORMAT") }
    }

    private fun request(host: String, path: String, body: JSONObject? = null): JSONObject =
        try { JSONObject(requestText(host, path, body)) } catch (e: TbcException) { throw e }
        catch (_: Exception) { throw TbcException("RESPONSE") }

    private fun requestText(host: String, path: String, body: JSONObject? = null): String {
        val headers = buildMap {
            put("User-Agent", "TBC a$PROTOCOL_APP_VERSION (Android; Android ${Build.VERSION.RELEASE}; ANDROID_PHONE)")
            put("Accept", "application/json")
            put("Accept-Language", "en-us")
            put("Content-Type", "application/json; charset=UTF-8")
            if (cookies.isNotEmpty()) put("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        val response = transport.exchange("https://$host$path", if (body == null) "GET" else "POST", headers, body?.toString())
        response.cookies.forEach { header ->
            runCatching { HttpCookie.parse(header) }.getOrDefault(emptyList()).forEach { cookie ->
                if (cookie.hasExpired()) cookies.remove(cookie.name) else cookies[cookie.name] = cookie.value
            }
        }
        when (response.status) {
            401 -> throw TbcException(if (path == "/v1/auth/loginWithPassword") "LOGIN" else "SESSION")
            403 -> throw TbcException("PROTECTION")
            429 -> throw TbcException("RATE_LIMIT")
        }
        if (response.status !in 200..299) throw TbcException(if (path.endsWith("certifyLogin") && response.status == 400) "OTP" else "HTTP_${response.status}")
        return response.body
    }
    private fun required(json: JSONObject, key: String) = json.optString(key).takeIf { it.isNotBlank() && it != "null" } ?: throw TbcException("RESPONSE")
    private fun encode(json: JSONObject) = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    companion object {
        private const val AUTH = "rmbgwauth.tbconline.ge"
        private const val API = "rmbgw.tbconline.ge"
        // Version advertised by the observed client protocol, not WHFIN's version.
        private const val PROTOCOL_APP_VERSION = "6.66.3"
    }
}
