package pl.dawidszczesniak.blockchain_platform.feature.problems.anchor

internal data class AnchorConfig(
    val enabled: Boolean,
    val chainId: Long?,
    val contractAddress: String?,
    val signerPrivateKey: String?,
    val gasLimit: Long,
    val gasPriceWei: Long?,
    val receiptTimeoutMs: Long,
    val receiptPollIntervalMs: Long,
    val explorerTxBaseUrl: String?,
    val contractMethodName: String,
) {
    fun explorerTxUrl(txHash: String?): String? {
        if (txHash.isNullOrBlank()) return null
        val base = explorerTxBaseUrl?.trim().orEmpty().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$txHash"
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AnchorConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProd = appEnv == PROD_ENV
            val enabled = env["ETH_ANCHOR_ENABLED"]
                ?.trim()
                ?.lowercase()
                ?.let { value -> value == "1" || value == "true" || value == "yes" }
                ?: false
            val gasLimit = env["ETH_ANCHOR_GAS_LIMIT"]
                ?.toLongOrNull()
                ?.coerceIn(80_000L, 5_000_000L)
                ?: DEFAULT_GAS_LIMIT
            val gasPriceWei = env["ETH_ANCHOR_GAS_PRICE_WEI"]
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            val receiptTimeoutMs = env["ETH_ANCHOR_RECEIPT_TIMEOUT_MS"]
                ?.toLongOrNull()
                ?.coerceIn(5_000L, 300_000L)
                ?: DEFAULT_RECEIPT_TIMEOUT_MS
            val receiptPollIntervalMs = env["ETH_ANCHOR_RECEIPT_POLL_INTERVAL_MS"]
                ?.toLongOrNull()
                ?.coerceIn(500L, 30_000L)
                ?: DEFAULT_RECEIPT_POLL_INTERVAL_MS
            val explorerTxBaseUrl = env["ETH_ANCHOR_EXPLORER_TX_BASE_URL"]
                ?.trim()
                ?.ifBlank { null }
            val contractMethodName = env["ETH_ANCHOR_METHOD_NAME"]
                ?.trim()
                ?.ifBlank { null }
                ?: DEFAULT_CONTRACT_METHOD_NAME

            val chainId = env["ETH_CHAIN_ID"]?.toLongOrNull()
            val contractAddress = env["ETH_ANCHOR_CONTRACT_ADDRESS"]?.trim()?.ifBlank { null }
            val signerPrivateKey = env["ETH_ANCHOR_PRIVATE_KEY"]?.trim()?.ifBlank { null }

            chainId?.let { configuredChainId ->
                if (isProd) {
                    require(configuredChainId == ETHEREUM_MAINNET_CHAIN_ID) {
                        "In APP_ENV=prod ETH_CHAIN_ID must be $ETHEREUM_MAINNET_CHAIN_ID (Ethereum mainnet)."
                    }
                } else {
                    require(configuredChainId != ETHEREUM_MAINNET_CHAIN_ID) {
                        "In APP_ENV=$appEnv ETH_CHAIN_ID must be a testnet chain id (mainnet is allowed only in prod)."
                    }
                }
            }

            if (enabled) {
                require(!contractAddress.isNullOrBlank()) {
                    "ETH_ANCHOR_CONTRACT_ADDRESS must be configured when ETH_ANCHOR_ENABLED=true."
                }
                require(!signerPrivateKey.isNullOrBlank()) {
                    "ETH_ANCHOR_PRIVATE_KEY must be configured when ETH_ANCHOR_ENABLED=true."
                }
                require(chainId != null && chainId > 0L) {
                    "ETH_CHAIN_ID must be configured when ETH_ANCHOR_ENABLED=true."
                }
            }

            return AnchorConfig(
                enabled = enabled,
                chainId = chainId,
                contractAddress = contractAddress,
                signerPrivateKey = signerPrivateKey,
                gasLimit = gasLimit,
                gasPriceWei = gasPriceWei,
                receiptTimeoutMs = receiptTimeoutMs,
                receiptPollIntervalMs = receiptPollIntervalMs,
                explorerTxBaseUrl = explorerTxBaseUrl,
                contractMethodName = contractMethodName,
            )
        }
    }
}

private const val DEFAULT_GAS_LIMIT = 350_000L
private const val DEFAULT_RECEIPT_TIMEOUT_MS = 90_000L
private const val DEFAULT_RECEIPT_POLL_INTERVAL_MS = 2_000L
private const val DEFAULT_CONTRACT_METHOD_NAME = "anchorSubmission"
private const val ETHEREUM_MAINNET_CHAIN_ID = 1L
private const val PROD_ENV = "prod"
