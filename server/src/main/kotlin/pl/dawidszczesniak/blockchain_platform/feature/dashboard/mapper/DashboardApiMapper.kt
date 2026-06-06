package pl.dawidszczesniak.blockchain_platform.feature.dashboard.mapper

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardMetricDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardUpdateDto

internal fun DashboardMetric.toDto(): DashboardMetricDto {
    return DashboardMetricDto(
        metricDate = metricDate,
        activeChallenges = activeChallenges,
        completedChallenges = completedChallenges,
        prizePoolLabel = prizePoolLabel,
    )
}

internal fun DashboardUpdate.toDto(): DashboardUpdateDto {
    return DashboardUpdateDto(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
    )
}
