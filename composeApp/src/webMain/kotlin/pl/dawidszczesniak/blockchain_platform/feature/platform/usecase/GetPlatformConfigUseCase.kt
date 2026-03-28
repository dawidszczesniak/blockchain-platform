package pl.dawidszczesniak.blockchain_platform.feature.platform.usecase

import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto
import pl.dawidszczesniak.blockchain_platform.feature.platform.repository.PlatformRepository

interface GetPlatformConfigUseCase {
    suspend operator fun invoke(): PlatformConfigDto
}

class GetPlatformConfigUseCaseImpl(
    private val repository: PlatformRepository,
) : GetPlatformConfigUseCase {
    override suspend fun invoke(): PlatformConfigDto {
        return repository.fetchPlatformConfig()
    }
}
