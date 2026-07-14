package dev.whekin.whfin.data.credo

data class CredoCredentials(
    val username: String,
    val credential: String,
)

data class CredoLoginChallenge(
    val operationId: String,
    val requiresOtp: Boolean,
    val mobileHint: String?,
    val directConfirmationSalt: String?,
)

data class CredoSession(
    val accessToken: String,
    val refreshToken: String?,
)

data class CredoRemoteAccount(
    val accountNumber: String,
    val currency: String,
    val accountId: Long?,
    val category: String?,
    val type: String?,
) {
    val stableKey: String get() = "$accountNumber|$currency"
    val maskedLabel: String get() = listOfNotNull(
        category?.takeIf(String::isNotBlank) ?: type?.takeIf(String::isNotBlank),
        "•${accountNumber.takeLast(4)}",
        currency,
    ).joinToString(" · ")
}

class CredoApiException(
    val code: String,
    cause: Throwable? = null,
) : Exception(code, cause)

interface CredoGateway {
    suspend fun initiateLogin(credentials: CredoCredentials): CredoLoginChallenge
    suspend fun sendOtp(operationId: String)
    suspend fun confirmLogin(
        challenge: CredoLoginChallenge,
        username: String,
        otp: String? = null,
    ): CredoSession

    suspend fun accounts(session: CredoSession): List<CredoRemoteAccount>

    suspend fun downloadStatement(
        session: CredoSession,
        account: CredoRemoteAccount,
        fromIso: String,
        toIso: String,
    ): ByteArray
}
