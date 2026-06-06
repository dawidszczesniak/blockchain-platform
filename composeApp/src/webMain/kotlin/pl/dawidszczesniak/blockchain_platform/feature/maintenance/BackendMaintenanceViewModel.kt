package pl.dawidszczesniak.blockchain_platform.feature.maintenance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackendMaintenanceState(
    val showContent: Boolean = true,
)

class BackendMaintenanceViewModel {
    private val _state = MutableStateFlow(BackendMaintenanceState())
    val state: StateFlow<BackendMaintenanceState> = _state.asStateFlow()
}
