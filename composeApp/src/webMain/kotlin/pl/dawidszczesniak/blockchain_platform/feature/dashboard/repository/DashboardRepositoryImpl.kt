package pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource.DashboardRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.mapper.toDomain

class DashboardRepositoryImpl(
    private val remoteDataSource: DashboardRemoteDataSource,
) : DashboardRepository {
    override suspend fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        return remoteDataSource.fetchMetricsHistory(limit = limit).map { it.toDomain() }
    }

    override suspend fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        return remoteDataSource.fetchLatestUpdates(limit = limit).map { it.toDomain() }
    }
}
