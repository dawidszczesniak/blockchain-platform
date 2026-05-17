package pl.dawidszczesniak.blockchain_platform.feature.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase

data class HomeState(
    val showFullDashboardContent: Boolean = true,
    val showHeroSection: Boolean = true,
    val showStatsSection: Boolean = true,
    val showLatestChallengesSection: Boolean = true,
    val activeChallenges: Int? = null,
    val completedChallenges: Int? = null,
    val prizePoolLabel: String? = null,
    val updates: List<HomeUpdateItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class HomeUpdateItem(
    val id: Long,
    val title: String,
    val body: String,
)

sealed interface HomeIntent {
    data object Refresh : HomeIntent
}

class HomeViewModel(
    private val getDashboardMetricsHistoryUseCase: GetDashboardMetricsHistoryUseCase,
    private val getDashboardUpdatesUseCase: GetDashboardUpdatesUseCase,
    private val dashboardConfig: DashboardConfig,
    private val metricsLimit: Int = 30,
    private val updatesLimit: Int = 3,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        onIntent(HomeIntent.Refresh)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> {
                refresh()
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        _state.update { current ->
            current.copy(isLoading = true, errorMessage = null)
        }
        scope.launch {
            runCatching {
                val metrics = getDashboardMetricsHistoryUseCase(limit = metricsLimit)
                val updates = getDashboardUpdatesUseCase(limit = updatesLimit)
                val orderedMetrics = metrics.sortedByDescending { it.metricDate }
                val latestMetric = orderedMetrics.firstOrNull()

                HomeState(
                    showFullDashboardContent = dashboardConfig.showFullDashboardContent,
                    showHeroSection = dashboardConfig.showHeroSection,
                    showStatsSection = dashboardConfig.showStatsSection,
                    showLatestChallengesSection = dashboardConfig.showLatestChallengesSection,
                    activeChallenges = latestMetric?.activeChallenges,
                    completedChallenges = latestMetric?.completedChallenges,
                    prizePoolLabel = latestMetric?.prizePoolLabel,
                    updates = updates.map { update ->
                        HomeUpdateItem(
                            id = update.id,
                            title = update.title,
                            body = update.body,
                        )
                    },
                    isLoading = false,
                    errorMessage = null,
                )
            }.onSuccess { loadedState ->
                _state.value = loadedState
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load dashboard data.",
                    )
                }
            }
        }
    }

    private fun initialState(): HomeState {
        return HomeState(
            showFullDashboardContent = dashboardConfig.showFullDashboardContent,
            showHeroSection = dashboardConfig.showHeroSection,
            showStatsSection = dashboardConfig.showStatsSection,
            showLatestChallengesSection = dashboardConfig.showLatestChallengesSection,
            isLoading = true,
        )
    }
}
