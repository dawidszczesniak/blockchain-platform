package pl.dawidszczesniak.blockchain_platform.feature.maintenance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.config.AppConfig

data class BackendHealthState(
    val isAvailable: Boolean? = null,
)

sealed interface BackendHealthIntent {
    data object Start : BackendHealthIntent
    data object RefreshNow : BackendHealthIntent
}

class BackendHealthViewModel(
    private val appConfig: AppConfig,
    private val clientConnectivityMonitor: ClientConnectivityMonitor,
    private val intervalMs: Long = 2_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BackendHealthState())
    val state: StateFlow<BackendHealthState> = _state.asStateFlow()
    private var pollingStarted: Boolean = false
    private var refreshInFlight: Boolean = false

    init {
        onIntent(BackendHealthIntent.Start)
        watchClientConnectivity()
    }

    fun onIntent(intent: BackendHealthIntent) {
        when (intent) {
            BackendHealthIntent.Start -> {
                startPollingIfNeeded()
            }
            BackendHealthIntent.RefreshNow -> {
                refreshHealthAsync()
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun startPollingIfNeeded() {
        if (pollingStarted) return
        pollingStarted = true
        scope.launch {
            while (isActive) {
                refreshHealth()
                delay(intervalMs)
            }
        }
    }

    private fun watchClientConnectivity() {
        scope.launch {
            clientConnectivityMonitor.state.collect { connectivity ->
                if (connectivity.isOnline) {
                    refreshHealthAsync()
                } else {
                    _state.update { current ->
                        current.copy(isAvailable = null)
                    }
                }
            }
        }
    }

    private fun refreshHealthAsync() {
        scope.launch {
            refreshHealth()
        }
    }

    private suspend fun refreshHealth() {
        if (refreshInFlight) {
            return
        }
        refreshInFlight = true
        try {
            if (!clientConnectivityMonitor.state.value.isOnline) {
                _state.update { current ->
                    current.copy(isAvailable = null)
                }
                return
            }

            val isAvailable = runCatching {
                checkBackendHealth(appConfig.apiBaseUrl)
            }.getOrDefault(false)

            if (clientConnectivityMonitor.state.value.isOnline) {
                _state.update { current ->
                    current.copy(isAvailable = isAvailable)
                }
            }
        } finally {
            refreshInFlight = false
        }
    }
}
