package pl.dawidszczesniak.blockchain_platform.feature.blockchain

internal data class EvmNetworkProfile(
    val chainId: Long,
    val networkName: String,
    val nativeCurrencyName: String,
    val nativeCurrencySymbol: String,
    val explorerBaseUrl: String?,
    val explorerTxBaseUrl: String?,
)

internal object EvmNetworkProfiles {
    fun defaultForEnvironment(appEnv: String): EvmNetworkProfile {
        return when (appEnv.trim().lowercase()) {
            "prod" -> ETHEREUM_MAINNET
            else -> ETHEREUM_SEPOLIA
        }
    }

    fun byChainId(chainId: Long): EvmNetworkProfile? {
        return when (chainId) {
            ETHEREUM_MAINNET.chainId -> ETHEREUM_MAINNET
            ETHEREUM_SEPOLIA.chainId -> ETHEREUM_SEPOLIA
            else -> null
        }
    }
}

internal val ETHEREUM_MAINNET = EvmNetworkProfile(
    chainId = 1L,
    networkName = "Ethereum Mainnet",
    nativeCurrencyName = "Ether",
    nativeCurrencySymbol = "ETH",
    explorerBaseUrl = "https://etherscan.io",
    explorerTxBaseUrl = "https://etherscan.io/tx",
)

internal val ETHEREUM_SEPOLIA = EvmNetworkProfile(
    chainId = 11155111L,
    networkName = "Ethereum Sepolia",
    nativeCurrencyName = "Sepolia Ether",
    nativeCurrencySymbol = "ETH",
    explorerBaseUrl = "https://sepolia.etherscan.io",
    explorerTxBaseUrl = "https://sepolia.etherscan.io/tx",
)
