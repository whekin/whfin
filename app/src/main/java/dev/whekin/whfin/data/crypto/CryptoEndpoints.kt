package dev.whekin.whfin.data.crypto

/**
 * Where balance reads are sent.
 *
 * Reading a public address always tells somebody that this address interests this device, so the
 * endpoint is a visible, editable setting rather than a hidden constant. Defaults are public
 * community endpoints that need no account and no API key.
 */
data class CryptoEndpoints(
    val ethereumRpcUrl: String = DEFAULT_ETHEREUM_RPC,
    val tronApiUrl: String = DEFAULT_TRON_API,
) {

    fun urlFor(network: CryptoNetwork): String = when (network) {
        CryptoNetwork.ETHEREUM -> ethereumRpcUrl
        CryptoNetwork.TRON -> tronApiUrl
    }

    companion object {
        const val DEFAULT_ETHEREUM_RPC = "https://ethereum-rpc.publicnode.com"
        const val DEFAULT_TRON_API = "https://api.trongrid.io"

        /** Endpoints are user-editable, so an empty or non-HTTPS value must not reach the network. */
        fun isUsable(url: String): Boolean =
            url.isNotBlank() && url.trim().startsWith("https://", ignoreCase = true)
    }
}
