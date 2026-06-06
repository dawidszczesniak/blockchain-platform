package pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardMetricDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardUpdateDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.mapper.toDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase

internal class DashboardController(
    private val getDashboardMetricsHistoryUseCase: GetDashboardMetricsHistoryUseCase,
    private val getDashboardUpdatesUseCase: GetDashboardUpdatesUseCase,
) {
    fun getMetricsHistory(limit: Int): List<DashboardMetricDto> {
        return getDashboardMetricsHistoryUseCase(limit).map { it.toDto() }
    }

    fun getLatestUpdates(limit: Int): List<DashboardUpdateDto> {
        return getDashboardUpdatesUseCase(limit).map { it.toDto() }
    }
}
