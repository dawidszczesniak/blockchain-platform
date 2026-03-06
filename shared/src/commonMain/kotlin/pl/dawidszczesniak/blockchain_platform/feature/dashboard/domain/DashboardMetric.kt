package pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain

data class DashboardMetric(
    val metricDate: String,
    val activeChallenges: Int,
    val prizePoolAmount: Long,
    val submissionsCount: Int,
)
