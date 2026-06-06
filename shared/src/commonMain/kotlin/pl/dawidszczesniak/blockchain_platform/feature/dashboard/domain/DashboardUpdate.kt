package pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain

data class DashboardUpdate(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: String,
)
