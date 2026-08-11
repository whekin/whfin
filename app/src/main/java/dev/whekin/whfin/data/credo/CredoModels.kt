package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.db.BankProduct

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

    /** Best-effort product metadata; it never decides whether the owner's money is Available. */
    val bankProduct: BankProduct? get() {
        val label = listOfNotNull(category, type).joinToString(" ").lowercase()
        return when {
            "მოთხოვნამდე" in label || "demand" in label -> BankProduct.DEMAND_DEPOSIT
            "ვადიანი" in label || "term deposit" in label -> BankProduct.TERM_DEPOSIT
            "მიმდინარე" in label || type.equals("ACCOUNT", ignoreCase = true) -> BankProduct.CURRENT_ACCOUNT
            else -> null
        }
    }
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
