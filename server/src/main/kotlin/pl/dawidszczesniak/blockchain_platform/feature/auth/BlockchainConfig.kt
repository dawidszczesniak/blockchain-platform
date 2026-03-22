package pl.dawidszczesniak.blockchain_platform.feature.auth

import java.net.URI

internal data class BlockchainConfig(
    val appEnvironment: String,
    val ethRpcUrl: String?,
) {
    val eip1271Enabled: Boolean
        get() = !ethRpcUrl.isNullOrBlank()

    val isProductionEnvironment: Boolean
        get() = appEnvironment == PROD_ENV

    fun validateChainIdForEnvironment(chainId: Long) {
        require(chainId > 0L) { "chainId must be greater than 0." }
        if (isProductionEnvironment) {
            require(chainId == ETHEREUM_MAINNET_CHAIN_ID) {
                "In APP_ENV=prod chainId must be $ETHEREUM_MAINNET_CHAIN_ID (Ethereum mainnet)."
            }
        } else {
            require(chainId != ETHEREUM_MAINNET_CHAIN_ID) {
                "In APP_ENV=$appEnvironment chainId must be a testnet chain id (mainnet is allowed only in prod)."
            }
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): BlockchainConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"
            val rpcUrl = env["ETH_RPC_URL"]?.trim().orEmpty().ifBlank { null }

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
                ethRpcUrl = rpcUrl,
            )
        }
    }
}

private const val ETHEREUM_MAINNET_CHAIN_ID = 1L
private const val PROD_ENV = "prod"
