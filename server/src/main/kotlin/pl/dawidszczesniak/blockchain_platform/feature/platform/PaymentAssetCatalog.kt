package pl.dawidszczesniak.blockchain_platform.feature.platform

import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto

internal enum class PaymentAssetKind {
    Native,
    Erc20,
}

internal data class PaymentAssetConfig(
    val code: String,
    val displayName: String,
    val symbol: String,
    val decimals: Int,
    val kind: PaymentAssetKind,
    val tokenAddress: String? = null,
) {
    fun toDto(): PaymentAssetDto {
        return PaymentAssetDto(
            code = code,
            displayName = displayName,
            symbol = symbol,
            decimals = decimals,
            kind = when (kind) {
                PaymentAssetKind.Native -> "native"
                PaymentAssetKind.Erc20 -> "erc20"
            },
            tokenAddress = tokenAddress,
        )
    }
}

internal class PaymentAssetCatalog private constructor(
    val nativeAsset: PaymentAssetConfig,
    val supportedAssets: List<PaymentAssetConfig>,
) {
    fun requireByCode(code: String): PaymentAssetConfig {
        return supportedAssets.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
            ?: error("Unsupported payment asset code '$code'.")
    }

    fun findByTokenAddress(tokenAddress: String?): PaymentAssetConfig? {
        val normalized = tokenAddress?.trim()?.lowercase()?.ifBlank { null } ?: return null
        return supportedAssets.firstOrNull { asset ->
            asset.tokenAddress?.lowercase() == normalized
        }
    }

    companion object {
        fun fromEnvironment(
            env: Map<String, String>,
            blockchainConfig: BlockchainConfig,
        ): PaymentAssetCatalog {
            val nativeSymbol = blockchainConfig.nativeCurrencySymbol.trim().ifBlank { "ETH" }.uppercase()
            val nativeAsset = PaymentAssetConfig(
                code = nativeSymbol,
                displayName = blockchainConfig.nativeCurrencyName.trim().ifBlank { "Ether" },
                symbol = nativeSymbol,
                decimals = 18,
                kind = PaymentAssetKind.Native,
            )
            val erc20Assets = parseSupportedErc20Assets(env)
            val supportedAssets = buildList {
                add(nativeAsset)
                addAll(erc20Assets)
            }
            return PaymentAssetCatalog(
                nativeAsset = nativeAsset,
                supportedAssets = supportedAssets,
            )
        }

        private fun parseSupportedErc20Assets(env: Map<String, String>): List<PaymentAssetConfig> {
            val raw = env[SUPPORTED_ERC20_TOKENS_ENV]?.trim().orEmpty()
            if (raw.isBlank()) {
                return emptyList()
            }

            val parsed = raw.split(';')
                .mapNotNull { entry -> entry.trim().ifBlank { null } }
                .mapIndexed { index, entry ->
                    val parts = entry.split('|').map { it.trim() }
                    require(parts.size == 5) {
                        "$SUPPORTED_ERC20_TOKENS_ENV entry #${index + 1} must use CODE|DISPLAY_NAME|SYMBOL|DECIMALS|ADDRESS."
                    }
                    val code = parts[0].uppercase()
                    val displayName = parts[1]
                    val symbol = parts[2].uppercase()
                    val decimals = parts[3].toIntOrNull()
                    require(!code.isBlank()) { "$SUPPORTED_ERC20_TOKENS_ENV entry #${index + 1} must define code." }
                    require(!displayName.isBlank()) {
                        "$SUPPORTED_ERC20_TOKENS_ENV entry #${index + 1} must define display name."
                    }
                    require(!symbol.isBlank()) {
                        "$SUPPORTED_ERC20_TOKENS_ENV entry #${index + 1} must define symbol."
                    }
                    require(decimals != null && decimals in 0..255) {
                        "$SUPPORTED_ERC20_TOKENS_ENV entry #${index + 1} must define valid decimals."
                    }
                    PaymentAssetConfig(
                        code = code,
                        displayName = displayName,
                        symbol = symbol,
                        decimals = decimals,
                        kind = PaymentAssetKind.Erc20,
                        tokenAddress = normalizeAddress(parts[4]),
                    )
                }

            val duplicateCodes = parsed.groupBy { it.code }.filterValues { it.size > 1 }.keys
            require(duplicateCodes.isEmpty()) {
                "$SUPPORTED_ERC20_TOKENS_ENV contains duplicate codes: ${duplicateCodes.joinToString()}."
            }
            val duplicateAddresses = parsed.groupBy { it.tokenAddress }.filterValues { it.size > 1 }.keys
            require(duplicateAddresses.isEmpty()) {
                "$SUPPORTED_ERC20_TOKENS_ENV contains duplicate token addresses: ${duplicateAddresses.joinToString()}."
            }
            return parsed
        }
    }
}

private fun normalizeAddress(value: String): String {
    val raw = value.removePrefix("0x").trim().lowercase()
    require(raw.length == 40 && raw.all { it in "0123456789abcdef" }) {
        "Ethereum address must be 20 bytes."
    }
    return "0x$raw"
}

private const val SUPPORTED_ERC20_TOKENS_ENV = "ETH_PLATFORM_SUPPORTED_ERC20_TOKENS"
