package pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate

interface DashboardRepository {
    suspend fun fetchMetricsHistory(limit: Int = 30): List<DashboardMetric>
    suspend fun fetchLatestUpdates(limit: Int = 3): List<DashboardUpdate>
}
