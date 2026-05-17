package pl.dawidszczesniak.blockchain_platform.feature.dashboard.mapper

import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardMetricDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardUpdateDto

internal fun DashboardMetricDto.toDomain(): DashboardMetric {
    return DashboardMetric(
        metricDate = metricDate,
        activeChallenges = activeChallenges,
        completedChallenges = completedChallenges,
        prizePoolLabel = prizePoolLabel,
    )
}

internal fun DashboardUpdateDto.toDomain(): DashboardUpdate {
    return DashboardUpdate(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
    )
}
