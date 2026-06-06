package pl.dawidszczesniak.blockchain_platform.feature.platform.repository

import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto

interface PlatformRepository {
    suspend fun fetchPlatformConfig(): PlatformConfigDto
}
