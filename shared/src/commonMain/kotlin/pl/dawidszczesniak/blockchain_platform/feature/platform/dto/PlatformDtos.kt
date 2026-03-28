package pl.dawidszczesniak.blockchain_platform.feature.platform.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfigDto(
    val anchorEnabled: Boolean = false,
    val chainId: Long? = null,
    val networkName: String,
    val contractAddress: String? = null,
)
