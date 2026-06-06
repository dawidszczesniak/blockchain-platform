package pl.dawidszczesniak.blockchain_platform.feature.platform.repository

import pl.dawidszczesniak.blockchain_platform.feature.platform.datasource.PlatformRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto

class PlatformRepositoryImpl(
    private val remoteDataSource: PlatformRemoteDataSource,
) : PlatformRepository {
    override suspend fun fetchPlatformConfig(): PlatformConfigDto {
        return remoteDataSource.fetchPlatformConfig()
    }
}
