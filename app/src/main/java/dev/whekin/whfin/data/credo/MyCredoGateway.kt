package dev.whekin.whfin.data.credo

import android.content.Context
import android.os.Build
import android.util.Base64
import android.webkit.WebSettings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun credoUserAgent(context: Context): String =
    runCatching { WebSettings.getDefaultUserAgent(context) }
        .getOrElse { System.getProperty("http.agent") ?: "WHFIN Android" }

private fun credoDeviceHistoryId(context: Context): String =
    context.getSharedPreferences("whfin_credo_device", Context.MODE_PRIVATE).run {
        getString("device_history_id", null) ?: UUID.randomUUID().toString().also { generated ->
            edit().putString("device_history_id", generated).apply()
        }
    }

internal interface CredoTransport {
    fun post(url: String, headers: Map<String, String>, body: String): String
}

internal class UrlConnectionCredoTransport(
    private val userAgent: String,
) : CredoTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", userAgent)
            headers.forEach(::setRequestProperty)
        }
        return try {
            connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) throw CredoApiException("HTTP_$status")
            response
        } catch (error: CredoApiException) {
            throw error
        } catch (error: IOException) {
            throw CredoApiException("NETWORK_ERROR", error)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Private, unsupported MyCredo web protocol adapter.
 *
 * The endpoint and request shapes mirror the public MyCredo web client. They can change without
 * notice, so the adapter deliberately exposes a small read-only surface and contains no logging.
 */
class MyCredoGateway internal constructor(
    private val context: Context,
    private val transport: CredoTransport,
    private val deviceHistoryId: String,
) : CredoGateway {
    constructor(appContext: Context) : this(
        context = appContext.applicationContext,
        transport = UrlConnectionCredoTransport(credoUserAgent(appContext)),
        deviceHistoryId = credoDeviceHistoryId(appContext),
    )

    override suspend fun initiateLogin(credentials: CredoCredentials): CredoLoginChallenge = io {
        val request = JSONObject()
            .put("username", credentials.username.lowercase(Locale.ROOT))
            .put("password", credentials.credential)
            .put("channel", CHANNEL)
            .put("deviceId", JSONObject.NULL)
            .put("refreshToken", JSONObject.NULL)
            .put("loggedInWith", "4")
            .put("WebDevicePublicId", deviceHistoryId)
            .put("deviceName", "Chrome")
            .put("deviceOs", "Android")
            .put("deviceOsVersion", Build.VERSION.RELEASE)
            .put("deviceScreenSize", "Android")
            .put("userAgent", runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("WHFIN Android"))
            .put("deviceType", "Mobile")
            .put("deviceModel", Build.MODEL)
            .put("deviceLanguage", "English")
        val data = responseData(transport.post("$AUTH_URL/Initiate", emptyMap(), request.toString()))
        val operationData = data.optJSONObject("operationData") ?: JSONObject()
        CredoLoginChallenge(
            operationId = data.requiredString("operationId"),
            requiresOtp = data.optBoolean("requires2FA", false),
            mobileHint = operationData.optString("mobile").takeIf(String::isNotBlank),
            directConfirmationSalt = operationData.optString("salt").takeIf(String::isNotBlank),
        )
    }

    override suspend fun sendOtp(operationId: String) {
        graphQl(
            session = null,
            query = SEND_OTP_MUTATION,
            variables = JSONObject().put("operationId", operationId),
        )
    }

    override suspend fun confirmLogin(
        challenge: CredoLoginChallenge,
        username: String,
        otp: String?,
    ): CredoSession = io {
        val direct = !challenge.requiresOtp
        val operationId = if (direct) {
            challenge.directConfirmationSalt ?: throw CredoApiException("MISSING_LOGIN_SALT")
        } else {
            challenge.operationId
        }
        val request = JSONObject()
            .put("OperationId", operationId)
            .put("TraceId", if (direct) createTraceId(username, operationId) else JSONObject.NULL)
            .put("TwoFactorHandle", otp ?: JSONObject.NULL)
        val data = responseData(transport.post("$AUTH_URL/confirm", emptyMap(), request.toString()))
        val operationData = data.optJSONObject("operationData") ?: throw CredoApiException("INVALID_LOGIN_RESPONSE")
        CredoSession(
            accessToken = operationData.requiredString("token"),
            refreshToken = operationData.optString("refreshToken").takeIf(String::isNotBlank),
        )
    }

    override suspend fun accounts(session: CredoSession): List<CredoRemoteAccount> {
        val data = graphQl(session, ACCOUNTS_QUERY, JSONObject())
        val accounts = data.optJSONArray("accounts") ?: JSONArray()
        return buildList {
            for (index in 0 until accounts.length()) {
                val item = accounts.optJSONObject(index) ?: continue
                val number = item.optString("accountNumber")
                val currency = item.optString("currency").uppercase(Locale.ROOT)
                if (number.isBlank() || currency.isBlank()) continue
                add(
                    CredoRemoteAccount(
                        accountNumber = number,
                        currency = currency,
                        accountId = item.optLong("accountId").takeIf { it != 0L },
                        category = item.optString("category").takeIf(String::isNotBlank),
                        type = item.optString("type").takeIf(String::isNotBlank),
                    ),
                )
            }
        }.distinctBy(CredoRemoteAccount::stableKey)
    }

    override suspend fun downloadStatement(
        session: CredoSession,
        account: CredoRemoteAccount,
        fromIso: String,
        toIso: String,
    ): ByteArray {
        val filter = JSONObject()
            .put("statementStartDate", fromIso)
            .put("statementEndDate", toIso)
            .put("language", ENGLISH_LANGUAGE)
            .put("accountNumber", account.accountNumber)
            .put("currency", account.currency)
        val data = graphQl(
            session,
            EXPORT_EXCEL_MUTATION,
            JSONObject().put("exportToExcel", filter),
        )
        val encoded = data.optString("exportToExcel")
            .substringAfter(',', data.optString("exportToExcel"))
        if (encoded.isBlank()) throw CredoApiException("EMPTY_STATEMENT")
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw CredoApiException("INVALID_STATEMENT", it) }
            .also { bytes -> if (bytes.isEmpty()) throw CredoApiException("EMPTY_STATEMENT") }
    }

    private suspend fun graphQl(
        session: CredoSession?,
        query: String,
        variables: JSONObject,
    ): JSONObject = io {
        val headers = buildMap {
            put("Language", "English")
            session?.let { put("Authorization", "Bearer ${it.accessToken}") }
        }
        val response = JSONObject(
            transport.post(
                GRAPHQL_URL,
                headers,
                JSONObject().put("query", query).put("variables", variables).toString(),
            ),
        )
        val errors = response.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val first = errors.optJSONObject(0)
            val code = first?.optJSONObject("extensions")?.optString("code")
                ?.takeIf(String::isNotBlank)
                ?: "CREDO_API_ERROR"
            throw CredoApiException(code)
        }
        response.optJSONObject("data") ?: throw CredoApiException("INVALID_API_RESPONSE")
    }

    private fun responseData(raw: String): JSONObject {
        val response = runCatching { JSONObject(raw) }
            .getOrElse { throw CredoApiException("INVALID_API_RESPONSE", it) }
        response.optString("errorCode").takeIf(String::isNotBlank)?.let { code ->
            throw CredoApiException(if (code == "PERSON_NOT_FOUND") "INVALID_INPUT_DATA" else code)
        }
        return response.optJSONObject("data") ?: throw CredoApiException("INVALID_API_RESPONSE")
    }

    private fun createTraceId(username: String, salt: String): String {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val message = "${username.uppercase(Locale.ROOT)}.$salt.WEB.$deviceHistoryId.$date"
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(PUBLIC_WEB_TRACE_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        return Base64.encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun JSONObject.requiredString(name: String): String = optString(name)
        .takeIf(String::isNotBlank)
        ?: throw CredoApiException("INVALID_API_RESPONSE")

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val CHANNEL = 508
        const val ENGLISH_LANGUAGE = 2
        const val AUTH_URL = "https://mobileapp.mycredo.ge/api/Auth"
        const val GRAPHQL_URL = "https://mobileapp.mycredo.ge/graphql"
        // This protocol constant ships in Credo's public web client; it is not a WHFIN credential.
        const val PUBLIC_WEB_TRACE_KEY = "509c0f6a-b331-4643-9081-cc4ce7639c21"
        val SEND_OTP_MUTATION = """
            mutation SendOtp(${'$'}operationId: String!) {
              operationSendChallenge(operationId: ${'$'}operationId)
            }
        """.trimIndent()

        val ACCOUNTS_QUERY = """
            query Accounts {
              accounts {
                accountId
                accountNumber
                currency
                category
                type
              }
            }
        """.trimIndent()

        val EXPORT_EXCEL_MUTATION = """
            mutation ExportStatement(${'$'}exportToExcel: StatementFilterGType!) {
              exportToExcel(exportToExcel: ${'$'}exportToExcel)
            }
        """.trimIndent()
    }
}
