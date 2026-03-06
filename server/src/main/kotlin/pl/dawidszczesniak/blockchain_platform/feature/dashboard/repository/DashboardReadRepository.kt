package pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dbservice.DashboardDbService
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate

internal interface DashboardReadRepository {
    fun fetchMetricsHistory(limit: Int): List<DashboardMetric>
    fun fetchLatestUpdates(limit: Int): List<DashboardUpdate>
}

internal class DashboardReadRepositoryImpl(
    private val dbService: DashboardDbService,
) : DashboardReadRepository {
    override fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        return dbService.fetchMetricsHistory(limit = limit)
    }

    override fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        return dbService.fetchLatestUpdates(limit = limit)
    }
}
