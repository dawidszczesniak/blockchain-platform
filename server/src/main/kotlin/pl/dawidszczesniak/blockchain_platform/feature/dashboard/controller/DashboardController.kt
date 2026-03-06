package pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase

internal class DashboardController(
    private val getDashboardMetricsHistoryUseCase: GetDashboardMetricsHistoryUseCase,
    private val getDashboardUpdatesUseCase: GetDashboardUpdatesUseCase,
) {
    fun getMetricsHistory(limit: Int): List<DashboardMetric> {
        return getDashboardMetricsHistoryUseCase(limit)
    }

    fun getLatestUpdates(limit: Int): List<DashboardUpdate> {
        return getDashboardUpdatesUseCase(limit)
    }
}
