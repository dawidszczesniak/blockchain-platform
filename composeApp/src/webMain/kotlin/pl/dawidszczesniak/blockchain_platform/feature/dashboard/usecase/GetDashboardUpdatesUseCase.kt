package pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardRepository

interface GetDashboardUpdatesUseCase {
    suspend operator fun invoke(limit: Int = 3): List<DashboardUpdate>
}

class GetDashboardUpdatesUseCaseImpl(
    private val repository: DashboardRepository,
) : GetDashboardUpdatesUseCase {
    override suspend operator fun invoke(limit: Int): List<DashboardUpdate> {
        return repository.fetchLatestUpdates(limit = limit)
    }
}
