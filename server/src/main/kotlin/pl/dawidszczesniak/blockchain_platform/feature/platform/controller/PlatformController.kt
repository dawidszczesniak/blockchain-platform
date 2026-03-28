package pl.dawidszczesniak.blockchain_platform.feature.platform.controller

import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.AnchorConfig

internal class PlatformController(
    private val anchorConfig: AnchorConfig,
) {
    fun getPlatformConfig(): PlatformConfigDto {
        return PlatformConfigDto(
            anchorEnabled = anchorConfig.enabled,
            chainId = anchorConfig.chainId,
            networkName = networkName(anchorConfig.chainId),
            contractAddress = anchorConfig.contractAddress,
        )
    }
}

private fun networkName(chainId: Long?): String {
    return when (chainId) {
        1L -> "Ethereum Mainnet"
        11155111L -> "Ethereum Sepolia"
        8453L -> "Base Mainnet"
        84532L -> "Base Sepolia"
        137L -> "Polygon"
        80002L -> "Polygon Amoy"
        31337L -> "Local Anvil"
        null -> "Not configured"
        else -> "EVM chain #$chainId"
    }
}
