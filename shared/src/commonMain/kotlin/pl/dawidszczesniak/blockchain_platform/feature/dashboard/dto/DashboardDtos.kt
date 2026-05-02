package pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardMetricDto(
    val metricDate: String,
    val activeChallenges: Int,
    val prizePoolLabel: String,
    val submissionsCount: Int,
)

@Serializable
data class DashboardUpdateDto(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: String,
)
