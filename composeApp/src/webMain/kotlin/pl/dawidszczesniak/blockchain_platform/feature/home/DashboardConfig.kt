package pl.dawidszczesniak.blockchain_platform.feature.home

class DashboardConfig(
    // Set to false to render only a centered "dashboard" label.
    val showFullDashboardContent: Boolean = true,
    // Section visibility toggles for Home dashboard.
    val showHeroSection: Boolean = true,
    val showStatsSection: Boolean = true,
    val showLatestChallengesSection: Boolean = true,
)
