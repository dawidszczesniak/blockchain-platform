package pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain

data class DashboardMetric(
    val metricDate: String,
    val activeChallenges: Int,
    val completedChallenges: Int,
    val prizePoolLabel: String,
)
