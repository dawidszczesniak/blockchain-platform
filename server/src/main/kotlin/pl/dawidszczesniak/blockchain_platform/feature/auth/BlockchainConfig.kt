package pl.dawidszczesniak.blockchain_platform.feature.auth

import java.net.URI
import pl.dawidszczesniak.blockchain_platform.feature.blockchain.EvmNetworkProfiles

internal data class BlockchainConfig(
    val appEnvironment: String,
    val chainId: Long,
    val networkName: String,
    val nativeCurrencyName: String,
    val nativeCurrencySymbol: String,
    val ethRpcUrl: String?,
    val explorerBaseUrl: String?,
) {
    val eip1271Enabled: Boolean
        get() = !ethRpcUrl.isNullOrBlank()

    val isProductionEnvironment: Boolean
        get() = appEnvironment == PROD_ENV

    fun validateChainIdForEnvironment(chainId: Long) {
        require(chainId > 0L) { "chainId must be greater than 0." }
        require(chainId == this.chainId) {
            "Unsupported chainId for current environment. Expected ${this.chainId} ($networkName)."
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String>): BlockchainConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"
            val defaultNetwork = EvmNetworkProfiles.defaultForEnvironment(appEnv)
            val configuredChainId = env["ETH_CHAIN_ID"]?.trim()?.toLongOrNull() ?: defaultNetwork.chainId
            val rpcUrl = env["ETH_RPC_URL"]?.trim().orEmpty().ifBlank { null }
            val baseNetwork = EvmNetworkProfiles.byChainId(configuredChainId)
            val networkName = env["ETH_NETWORK_NAME"]?.trim().orEmpty().ifBlank {
                baseNetwork?.networkName ?: "EVM chain #$configuredChainId"
            }
            val nativeCurrencyName = env["ETH_NATIVE_CURRENCY_NAME"]?.trim().orEmpty().ifBlank {
                baseNetwork?.nativeCurrencyName ?: "Ether"
            }
            val nativeCurrencySymbol = env["ETH_NATIVE_CURRENCY_SYMBOL"]?.trim().orEmpty().ifBlank {
                baseNetwork?.nativeCurrencySymbol ?: "ETH"
            }
            val explorerBaseUrl = env["ETH_PUBLIC_EXPLORER_BASE_URL"]?.trim().orEmpty().ifBlank {
                baseNetwork?.explorerBaseUrl
            }

            if (appEnv == PROD_ENV) {
                require(configuredChainId == ETHEREUM_MAINNET_CHAIN_ID) {
                    "In APP_ENV=prod ETH_CHAIN_ID must be $ETHEREUM_MAINNET_CHAIN_ID (Ethereum mainnet)."
                }
            } else {
                require(configuredChainId != ETHEREUM_MAINNET_CHAIN_ID) {
                    "In APP_ENV=$appEnv ETH_CHAIN_ID must be a testnet chain id (mainnet is allowed only in prod)."
                }
            }

            if (isProductionLike && rpcUrl.isNullOrBlank()) {
                error("ETH_RPC_URL must be configured in staging/prod to support smart-contract wallet verification.")
            }
            if (!rpcUrl.isNullOrBlank()) {
                val parsed = runCatching { URI(rpcUrl) }.getOrNull()
                    ?: error("ETH_RPC_URL has invalid URI format.")
                val scheme = parsed.scheme?.lowercase().orEmpty()
                if (scheme != "http" && scheme != "https") {
                    error("ETH_RPC_URL must use http:// or https:// scheme.")
                }
                if (isProductionLike && scheme != "https") {
                    error("ETH_RPC_URL must use https:// in staging/prod.")
                }
            }
            return BlockchainConfig(
                appEnvironment = appEnv,
                chainId = configuredChainId,
                networkName = networkName,
                nativeCurrencyName = nativeCurrencyName,
                nativeCurrencySymbol = nativeCurrencySymbol,
                ethRpcUrl = rpcUrl,
                explorerBaseUrl = explorerBaseUrl,
            )
        }
    }
}

private const val ETHEREUM_MAINNET_CHAIN_ID = 1L
private const val PROD_ENV = "prod"
