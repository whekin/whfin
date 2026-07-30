package dev.whekin.whfin.data.crypto

import java.math.BigInteger

/**
 * Read-only view of one address×asset balance on a chain.
 *
 * WHFIN is watch-only by design: this boundary can read a public address and nothing else. It never
 * receives, stores, or asks for a seed phrase or a private key, and it cannot sign or send anything.
 */
interface CryptoBalanceProvider {

    /** Chains this provider can answer for. */
    val networks: Set<CryptoNetwork>

    /**
     * Exact base units of [request]. Throws [CryptoBalanceException] for an expected failure such as
     * an unreachable endpoint or a refusing node; the caller reports it per account.
     */
    suspend fun balance(request: CryptoBalanceRequest): CryptoBalanceReading
}

data class CryptoBalanceRequest(
    val network: CryptoNetwork,
    val address: String,
    val asset: CryptoAssetSpec,
)

data class CryptoBalanceReading(
    /** Exact base units: wei, sun, or token units. Never rounded, never fiat minor units. */
    val baseUnits: BigInteger,
    val decimals: Int,
    /** Endpoint host the reading came from, so the UI can say where the number is from. */
    val source: String? = null,
)

class CryptoBalanceException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** No endpoint configured for this network yet. */
        NOT_CONFIGURED,

        /** Network unreachable, timeout, TLS failure. */
        UNREACHABLE,

        /** The endpoint answered, but with an error or something unparseable. */
        REJECTED,

        /** The provider does not support this chain or asset. */
        UNSUPPORTED,
    }
}
