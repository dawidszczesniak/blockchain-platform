package pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepository

internal interface GetDashboardUpdatesUseCase {
    operator fun invoke(limit: Int): List<DashboardUpdate>
}

internal class GetDashboardUpdatesUseCaseImpl(
    private val repository: DashboardReadRepository,
) : GetDashboardUpdatesUseCase {
    override operator fun invoke(limit: Int): List<DashboardUpdate> {
        return repository.fetchLatestUpdates(limit = limit)
    }
}
