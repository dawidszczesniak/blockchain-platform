package pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardRepository

interface GetDashboardMetricsHistoryUseCase {
    suspend operator fun invoke(limit: Int = 30): List<DashboardMetric>
}

class GetDashboardMetricsHistoryUseCaseImpl(
    private val repository: DashboardRepository,
) : GetDashboardMetricsHistoryUseCase {
    override suspend operator fun invoke(limit: Int): List<DashboardMetric> {
        return repository.fetchMetricsHistory(limit = limit)
    }
}
