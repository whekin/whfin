package dev.whekin.whfin.data.crypto

/**
 * A watch-only network WHFIN actually supports.
 *
 * A crypto asset is identified by `(chainId, contractAddress)` rather than by ticker, so USDT-ERC20
 * and USDT-TRC20 stay different assets with different balances. Networks that are not implemented
 * are deliberately absent instead of being offered as a broken choice.
 */
enum class CryptoNetwork(val chainId: String, val displayName: String) {
    ETHEREUM("eip155:1", "Ethereum"),
    TRON("tron:mainnet", "Tron"),
    ;

    val assets: List<CryptoAssetSpec>
        get() = when (this) {
            ETHEREUM -> listOf(
                CryptoAssetSpec("ETH", "Ether", decimals = 18, contractAddress = null),
                CryptoAssetSpec(
                    symbol = "USDT",
                    name = "Tether USD",
                    decimals = 6,
                    contractAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7",
                ),
                CryptoAssetSpec(
                    symbol = "USDC",
                    name = "USD Coin",
                    decimals = 6,
                    contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                ),
            )
            TRON -> listOf(
                CryptoAssetSpec("TRX", "Tronix", decimals = 6, contractAddress = null),
                CryptoAssetSpec(
                    symbol = "USDT",
                    name = "Tether USD",
                    decimals = 6,
                    contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                ),
                CryptoAssetSpec(
                    symbol = "USDC",
                    name = "USD Coin",
                    decimals = 6,
                    contractAddress = "TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8",
                ),
            )
        }

    /** Coin of the chain itself: the one asset an address always has, even at zero. */
    val nativeAsset: CryptoAssetSpec get() = assets.first { it.contractAddress == null }

    fun asset(symbol: String): CryptoAssetSpec? =
        assets.firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }

    companion object {
        fun byChainId(chainId: String): CryptoNetwork? = entries.firstOrNull { it.chainId == chainId }

        /** Every ticker WHFIN can actually track, without duplicating USDT per network. */
        val supportedSymbols: List<String> =
            entries.flatMap { network -> network.assets.map { it.symbol } }.distinct()
    }
}

/**
 * Native asset of a chain has no contract; a token is pinned to its exact contract so a look-alike
 * token cannot silently take its place.
 */
data class CryptoAssetSpec(
    val symbol: String,
    val name: String,
    val decimals: Int,
    val contractAddress: String?,
)
