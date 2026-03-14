package pl.dawidszczesniak.blockchain_platform.feature.auth

import java.net.URI

internal data class BlockchainConfig(
    val ethRpcUrl: String?,
) {
    val eip1271Enabled: Boolean
        get() = !ethRpcUrl.isNullOrBlank()

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
                ethRpcUrl = rpcUrl,
            )
        }
    }
}
