package pl.dawidszczesniak.blockchain_platform.feature.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeState(
    val showFullDashboardContent: Boolean = DashboardConfig.showFullDashboardContent,
)

class HomeViewModel {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
}
