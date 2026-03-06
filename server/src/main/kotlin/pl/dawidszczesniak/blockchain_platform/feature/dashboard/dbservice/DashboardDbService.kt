package pl.dawidszczesniak.blockchain_platform.feature.dashboard.dbservice

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDao
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate

internal interface DashboardDbService {
    fun fetchMetricsHistory(limit: Int): List<DashboardMetric>
    fun fetchLatestUpdates(limit: Int): List<DashboardUpdate>
}

internal class DashboardDbServiceImpl(
    private val dashboardDao: DashboardDao,
) : DashboardDbService {
    override fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        return dashboardDao.fetchMetricsHistory(limit = limit)
    }

    override fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        return dashboardDao.fetchLatestUpdates(limit = limit)
    }
}
