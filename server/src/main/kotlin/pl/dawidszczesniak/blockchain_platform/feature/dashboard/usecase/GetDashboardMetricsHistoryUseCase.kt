package pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepository

internal interface GetDashboardMetricsHistoryUseCase {
    operator fun invoke(limit: Int): List<DashboardMetric>
}

internal class GetDashboardMetricsHistoryUseCaseImpl(
    private val repository: DashboardReadRepository,
) : GetDashboardMetricsHistoryUseCase {
    override operator fun invoke(limit: Int): List<DashboardMetric> {
        return repository.fetchMetricsHistory(limit = limit)
    }
}
