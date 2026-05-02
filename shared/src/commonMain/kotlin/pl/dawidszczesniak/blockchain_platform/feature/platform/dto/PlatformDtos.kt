package pl.dawidszczesniak.blockchain_platform.feature.platform.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaymentAssetDto(
    val code: String,
    val displayName: String,
    val symbol: String,
    val decimals: Int,
    val kind: String,
    val tokenAddress: String? = null,
)

@Serializable
data class WalletNetworkConfigDto(
    val chainId: Long,
    val networkName: String,
    val nativeCurrencyName: String,
    val nativeCurrencySymbol: String,
    val explorerBaseUrl: String? = null,
)

@Serializable
data class PlatformConfigDto(
    val proxyAddress: String? = null,
    val chainId: Long? = null,
    val networkName: String,
    val walletNetwork: WalletNetworkConfigDto? = null,
    val supportedPaymentAssets: List<PaymentAssetDto> = emptyList(),
)
