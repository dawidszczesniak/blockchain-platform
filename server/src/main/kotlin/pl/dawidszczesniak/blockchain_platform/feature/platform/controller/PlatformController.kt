package pl.dawidszczesniak.blockchain_platform.feature.platform.controller

import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.WalletNetworkConfigDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig

internal class PlatformController(
    private val blockchainConfig: BlockchainConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) {
    fun getPlatformConfig(): PlatformConfigDto {
        return PlatformConfigDto(
            proxyAddress = contractConfig.proxyAddress,
            chainId = blockchainConfig.chainId,
            networkName = blockchainConfig.networkName,
            walletNetwork = WalletNetworkConfigDto(
                chainId = blockchainConfig.chainId,
                networkName = blockchainConfig.networkName,
                nativeCurrencyName = blockchainConfig.nativeCurrencyName,
                nativeCurrencySymbol = blockchainConfig.nativeCurrencySymbol,
                explorerBaseUrl = blockchainConfig.explorerBaseUrl,
            ),
            supportedPaymentAssets = paymentAssetCatalog.supportedAssets.map { it.toDto() },
        )
    }
}
